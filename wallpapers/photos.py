#!/usr/bin/env python3
"""Download and prepare the Nature and Abstract photo sets.

The Pexels and Unsplash licences let an app use these photos, but not redistribute them as wallpaper
files, so this repo keeps only each photo's ID, crop and credit (<set>/selection.json). This script
fetches them from Pexels and Unsplash and builds what the app ships:

  1. download the photo, 3840 px wide                                          -> <set>/originals/
  2. cover-crop to 16:9 at the entry's focal point (crop.fx, crop.fy), resize to 1920x1080
  3. encode WebP with cwebp (-m 6 -sharp_yuv) from q82 upwards, stepping the quality up until flat
     regions (skies, mist, still water) show no banding                         -> <set>/out/<slot>-<day>.webp
  4. decode that WebP, area-downscale to 480x270, 3 passes of box blur radius 6, saturation x1.30
     (the blurred copy behind the glass)                                         -> <set>/out/<slot>-<day>-blur.webp

Art and Space are public domain or CC BY, so their finished files are committed in art/out and space/out.

Run: python3 photos.py [nature|abstract ...]   (needs Pillow, numpy and cwebp; build.sh runs it when needed)
"""
import json, os, subprocess, sys, tempfile, urllib.request
import numpy as np
from PIL import Image, ImageFilter, ImageEnhance

Image.MAX_IMAGE_PIXELS = None
HERE = os.path.dirname(os.path.abspath(__file__))
SETS = ['nature', 'abstract']
UA = {'User-Agent': 'Fresco-FireTV-Launcher/1.0 (+https://github.com/rowesk/fresco-firetv-launcher)'}
QUALITIES = [82, 85, 88, 91, 94, 97]
BAND_LIMIT = 1.25   # max smoothed error (8-bit levels) allowed in flat regions

def url(e):
    if e.get('pexels_id'):
        i = e['pexels_id']
        return f'https://images.pexels.com/photos/{i}/pexels-photo-{i}.jpeg?w=3840'
    # images.unsplash.com takes the photo's image id (unsplash_image), not the short id in its page URL.
    return f"https://images.unsplash.com/{e['unsplash_image']}?fm=jpg&q=95&w=3840"

def page_id(e):
    return f"pexels-{e['pexels_id']}" if e.get('pexels_id') else f"unsplash-{e['unsplash_id']}"

def download(e, folder):
    path = os.path.join(folder, page_id(e) + '.jpg')
    if not os.path.exists(path):
        data = urllib.request.urlopen(urllib.request.Request(url(e), headers=UA), timeout=120).read()
        with open(path + '.part', 'wb') as f:
            f.write(data)
        os.replace(path + '.part', path)
    return path

def cover(im, w, h, fx, fy):
    sw, sh = im.size
    scale = max(w / sw, h / sh)
    nw, nh = round(sw * scale), round(sh * scale)
    im = im.resize((nw, nh), Image.LANCZOS)
    x = round((nw - w) * fx)
    y = round((nh - h) * fy)
    return im.crop((x, y, x + w, y + h))

def luma(im):
    a = np.asarray(im, dtype=np.float32)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]

def flat_mask(im):
    # pixels whose 9x9 neighbourhood is nearly flat: skies, mist, still water
    a = luma(im).astype(np.float64)
    k = 9
    pad = np.pad(a, k // 2, mode='edge')
    c = np.pad(pad.cumsum(0).cumsum(1), ((1, 0), (1, 0)))
    s = c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]
    c2 = np.pad((pad ** 2).cumsum(0).cumsum(1), ((1, 0), (1, 0)))
    s2 = c2[k:, k:] - c2[:-k, k:] - c2[k:, :-k] + c2[:-k, :-k]
    return s2 / (k * k) - (s / (k * k)) ** 2 < 1.0

def gauss(a, sigma):
    r = int(3 * sigma)
    k = np.exp(-np.arange(-r, r + 1) ** 2 / (2 * sigma ** 2))
    k /= k.sum()
    a = np.pad(a, r, mode='edge')
    a = np.apply_along_axis(lambda v: np.convolve(v, k, 'valid'), 1, a)
    return np.apply_along_axis(lambda v: np.convolve(v, k, 'valid'), 0, a)

def banding(ref, enc, mask):
    # compare low-passed luma: grain and edge ringing vanish, contour steps and blotches remain
    if mask.mean() < 0.01:
        return 0.0
    d = gauss(luma(ref) - luma(enc), 2.0)
    return float(np.percentile(np.abs(d)[mask], 99.9))

def encode(png, dst, q):
    subprocess.run(['cwebp', '-quiet', '-q', str(q), '-m', '6', '-sharp_yuv', '-mt', png, '-o', dst], check=True)

def blur_copy(full):
    small = full.resize((960, 540), Image.BOX).resize((480, 270), Image.BOX)
    for _ in range(3):
        small = small.filter(ImageFilter.BoxBlur(6))
    return ImageEnhance.Color(small).enhance(1.30)

def build(set_name):
    base = os.path.join(HERE, set_name)
    originals, out = os.path.join(base, 'originals'), os.path.join(base, 'out')
    os.makedirs(originals, exist_ok=True)
    os.makedirs(out, exist_ok=True)
    entries = json.load(open(os.path.join(base, 'selection.json')))['wallpapers']
    with tempfile.TemporaryDirectory() as tmp:
        for e in entries:
            name = f"{e['slot']}-{e['day']}"
            dst, bdst = os.path.join(out, name + '.webp'), os.path.join(out, name + '-blur.webp')
            if os.path.exists(dst) and os.path.exists(bdst):
                continue
            src = download(e, originals)
            full = cover(Image.open(src).convert('RGB'), 1920, 1080, e['crop']['fx'], e['crop']['fy'])
            png = os.path.join(tmp, name + '.png')
            full.save(png)
            mask = flat_mask(full)
            for q in QUALITIES:
                encode(png, dst, q)
                enc = Image.open(dst).convert('RGB')
                if banding(full, enc, mask) <= BAND_LIMIT:
                    break
            blur_copy(enc).save(bdst, quality=90, method=6)
            print(f'{set_name:8} {name:13} q{q}  {page_id(e)}')
    print(f'{set_name}: {len(entries)} ready in {out}')

if __name__ == '__main__':
    for s in sys.argv[1:] or SETS:
        if s not in SETS:
            sys.exit(f'unknown set {s}; choose from {SETS}')
        build(s)
