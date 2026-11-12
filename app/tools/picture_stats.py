#!/usr/bin/env python3
"""Production solver inputs: blur means and exact sharp-picture percentile pixels.

The 26 line windows and endpoints in GlassCheck reproduce the accepted mock fixtures;
the TV extends samples to x1800 and measures its actual Stremio strings instead.
"""
import os
import sys
import numpy as np
from PIL import Image

def lum(rgb):
    c = rgb.astype(float) / 255
    c = np.where(c <= .04045, c / 12.92, ((c + .055) / 1.055) ** 2.4)
    return .2126 * c[..., 0] + .7152 * c[..., 1] + .0722 * c[..., 2]

def samples(rgb):
    px = rgb.reshape(-1, 3)
    l = lum(px)
    return [int(v) for q in (90, 10, 50)
            for v in px[int(np.argmin(np.abs(l - np.percentile(l, q))))]]

root = sys.argv[1]
for d, _, files in sorted(os.walk(root)):
    for f in sorted(files):
        if not f.endswith(('-blur.webp', '-blur.jpg')):
            continue
        stem = f.rsplit('-blur', 1)[0]
        sharp = next((os.path.join(d, stem + ext) for ext in ('.webp', '.jpg', '.png')
                      if os.path.isfile(os.path.join(d, stem + ext))), None)
        if sharp is None:
            raise RuntimeError('No sharp picture for ' + f)
        a = np.asarray(Image.open(sharp).convert('RGB'))
        assert a.shape == (1080, 1920, 3), sharp
        g = Image.open(os.path.join(d, f)).convert('RGB').resize((32, 18), Image.Resampling.BOX)
        values = [v for px in g.getdata() for v in px]
        for x, w in ((1527.2, 36), (1563.2, 36), (1599.2, 16.8), (1616, 36), (1652, 36)):
            values.extend(samples(a[98:142, round(x):round(x + w)]))
        for top, bottom in ((968, 1000), (569, 621), (629, 661)):
            for x in range(120, 1000 - 64 + 1, 32):
                values.extend(samples(a[top:bottom, x:x + 64]))
        print(os.path.relpath(os.path.join(d, stem), root), *values)
