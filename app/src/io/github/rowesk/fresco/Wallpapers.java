package io.github.rowesk.fresco;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

/**
 * The wallpaper sets, one folder each under `wallpapers/`: nature, art, abstract and space. Each
 * picture is `<key>.webp` with its blurred copy `<key>-blur.webp`; WallpaperStyle decides which key
 * the chosen style shows now. Only the current picture is ever decoded (8.3 MB), plus its 480x270
 * blur (0.5 MB). Both are dropped whenever the launcher leaves the screen.
 *
 * A style whose set isn't packed (abstract and space may not be yet) shows the nature photo for the
 * same moment. The build packs placeholders as `fallback-<slot>.jpg` for any slot the nature set
 * doesn't cover, and if a blurred copy is missing we make one here with the same pipeline as
 * tools/prepare_wallpapers.py.
 *
 * For screenshots, a picture can be previewed in place of the scheduled one (HomeActivity's
 * `preview_wallpaper` extra) until the next Home press or the next change of the scheduled picture.
 */
final class Wallpapers {
    private static final String TAG = "Fresco";

    static final int MORNING = WallpaperStyle.MORNING, DAY = WallpaperStyle.DAY,
            EVENING = WallpaperStyle.EVENING, NIGHT = WallpaperStyle.NIGHT;
    /** Settings thumbnails: 1920x1080 decoded at 1/16 is 120x68, about 32 KB each. */
    private static final int THUMB_SAMPLE = 16;
    /** If the service decoded early for a Home press that never reached us, let it go after this. */
    private static final long PREWARM_HOLD_MS = 4000;

    /** One decoded wallpaper and the glass values solved for it. */
    static final class Frame {
        final String key;
        final int period;
        Bitmap sharp;
        Bitmap blur;
        Tuning tuning;

        Frame(String key, int period) {
            this.key = key;
            this.period = period;
        }
    }

    interface Callback {
        void done(Frame f);
    }

    interface ThumbCallback {
        void done(int index, Bitmap thumb);
    }

    private static Wallpapers instance;
    private final AssetManager assets;
    private final Prefs prefs;
    private final HashSet<String> files = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler worker;
    private Frame current;
    private String loadingKey;
    private final List<Callback> waiting = new ArrayList<>();
    private int generation;
    private boolean inUse;
    /** A previewed picture, and the scheduled key it stands in for: it ends when that key changes. */
    private String previewKey, previewOver;
    private final Runnable releaseIfUnused = () -> {
        if (!inUse) release();
    };

    private Wallpapers(Context context) {
        assets = context.getApplicationContext().getAssets();
        prefs = Prefs.get(context);
        listFiles("");
        if (files.isEmpty()) Log.e(TAG, "no wallpapers in assets");
        HandlerThread t = new HandlerThread("wallpaper");
        t.start();
        worker = new Handler(t.getLooper());
    }

    static synchronized Wallpapers get(Context context) {
        if (instance == null) instance = new Wallpapers(context);
        return instance;
    }

    /** Asset paths relative to `wallpapers/`, e.g. `art/everyday/day-3.webp`. Listed once. */
    private void listFiles(String dir) {
        try {
            String[] names = assets.list(dir.isEmpty() ? "wallpapers" : "wallpapers/" + dir);
            if (names == null) return;
            for (String n : names) {
                String path = dir.isEmpty() ? n : dir + "/" + n;
                if (n.indexOf('.') < 0) listFiles(path);
                else files.add(path);
            }
        } catch (IOException e) {
            Log.e(TAG, "could not list wallpapers/" + dir, e);
        }
    }

    int style() {
        return WallpaperStyle.fromId(prefs.wallpaperStyle());
    }

    /** What `style` shows at `now`, or the nature photo for that moment if its set isn't packed. */
    String keyFor(int style, Calendar now) {
        String key = WallpaperStyle.key(style, now);
        return packed(key) ? key : WallpaperStyle.natureKey(now);
    }

    private boolean packed(String key) {
        for (String ext : new String[]{".webp", ".jpg", ".png"}) if (files.contains(key + ext)) return true;
        return false;
    }

    String currentKey() {
        String key = keyFor(style(), Calendar.getInstance());
        if (previewKey != null && key.equals(previewOver)) return previewKey;
        previewKey = null;
        return key;
    }

    /**
     * Shows `key` (e.g. `art/everyday/evening-1`, an extension is ignored) instead of the scheduled
     * picture until the time of day or the style changes, or Home is pressed. Returns false, and
     * changes nothing, for a key that isn't packed.
     */
    boolean preview(String key) {
        key = key.trim().replaceFirst("\\.(webp|jpg|png)$", "");
        if (!packed(key)) {
            Log.w(TAG, "wallpaper: no picture " + key + " to preview");
            return false;
        }
        previewOver = keyFor(style(), Calendar.getInstance());
        previewKey = key;
        Log.i(TAG, "wallpaper: previewing " + key);
        return true;
    }

    /** Back to the scheduled picture. Returns true if a preview was showing. */
    boolean endPreview() {
        boolean was = previewKey != null;
        previewKey = null;
        return was;
    }

    Frame current() {
        return current;
    }

    /** The launcher is on screen and holding the current frame. */
    void setInUse(boolean on) {
        inUse = on;
        if (on) main.removeCallbacks(releaseIfUnused);
    }

    /** Ensures the current wallpaper (sharp + blur) is decoded, then calls back on the main thread. */
    void ensureCurrent(Callback cb) {
        String key = currentKey();
        if (current != null && key.equals(current.key) && current.sharp != null && current.blur != null) {
            if (cb != null) cb.done(current);
            return;
        }
        if (cb != null) waiting.add(cb);
        if (key.equals(loadingKey)) return;
        loadingKey = key;
        final int gen = ++generation;
        load(key, true, f -> {
            if (gen != generation) {
                recycle(f);
                return;
            }
            loadingKey = null;
            Frame old = current;
            current = f;
            if (old != null && old != f) recycle(old);
            List<Callback> run = new ArrayList<>(waiting);
            waiting.clear();
            for (Callback c : run) c.done(f);
        });
    }

    /**
     * Called by the service as soon as Amazon home appears after Home, so decoding overlaps the
     * tile click and our activity's start. Held only briefly if the launcher never shows.
     */
    void prewarm() {
        // A Home press ends any preview.
        previewKey = null;
        ensureCurrent(null);
        main.removeCallbacks(releaseIfUnused);
        main.postDelayed(releaseIfUnused, PREWARM_HOLD_MS);
    }

    /** The launcher left, but a Home press is on its way back: keep the frame a moment for it. */
    void holdBriefly() {
        main.removeCallbacks(releaseIfUnused);
        main.postDelayed(releaseIfUnused, PREWARM_HOLD_MS);
    }

    /** Drops both bitmaps. Callers must have detached them from every view first. */
    void release() {
        generation++;
        loadingKey = null;
        waiting.clear();
        if (current != null) recycle(current);
        current = null;
    }

    /** For the wallpaper change: decode only the blur (and solve its glass) for `key`. */
    void loadBlur(String key, Callback cb) {
        load(key, false, cb);
    }

    /** For the wallpaper change: decode the sharp photo into a frame that already has its blur. */
    void loadSharp(final Frame f, final Callback cb) {
        worker.post(() -> {
            Bitmap b = decodeSharp(f.key);
            main.post(() -> {
                f.sharp = b;
                cb.done(f);
            });
        });
    }

    /** For the wallpaper change: the old sharp photo goes before the new one is decoded (peak = one photo). */
    void dropSharp() {
        if (current != null && current.sharp != null) {
            current.sharp.recycle();
            current.sharp = null;
        }
    }

    /** For the wallpaper change: the new frame becomes current; the old blur goes. */
    void commit(Frame f) {
        generation++;
        loadingKey = null;
        Frame old = current;
        current = f;
        if (old != null && old != f) recycle(old);
    }

    private void load(final String key, final boolean withSharp, final Callback cb) {
        worker.post(() -> {
            long t0 = System.currentTimeMillis();
            Frame f = new Frame(key, WallpaperStyle.periodOfKey(key));
            f.blur = decodeBlur(key);
            f.tuning = f.blur == null ? Tuning.DEFAULT : new Tuning(f.blur);
            if (withSharp) f.sharp = decodeSharp(key);
            Log.i(TAG, "wallpaper " + key + (withSharp ? "" : " (blur only)") + " decoded in "
                    + (System.currentTimeMillis() - t0) + " ms");
            main.post(() -> cb.done(f));
        });
    }

    /** Small copies for the settings sub-page, decoded one by one on the wallpaper thread. */
    void loadThumbs(final String[] keys, final ThumbCallback cb) {
        for (int i = 0; i < keys.length; i++) {
            final int index = i;
            worker.post(() -> {
                Bitmap b = null;
                String path = find(keys[index], false);
                if (path != null) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inScaled = false;
                    o.inSampleSize = THUMB_SAMPLE;
                    o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    b = decode(path, o);
                }
                final Bitmap thumb = b;
                main.post(() -> cb.done(index, thumb));
            });
        }
    }

    private static void recycle(Frame f) {
        // On Android 8+ recycle only drops our reference; anything still drawing keeps the pixels alive.
        if (f.sharp != null) f.sharp.recycle();
        if (f.blur != null) f.blur.recycle();
        f.sharp = null;
        f.blur = null;
    }

    private String find(String key, boolean blur) {
        String suffix = blur ? "-blur" : "";
        for (String ext : new String[]{".webp", ".jpg", ".png"}) {
            if (files.contains(key + suffix + ext)) return "wallpapers/" + key + suffix + ext;
        }
        String slot = WallpaperStyle.SLOTS[WallpaperStyle.periodOfKey(key)];
        for (String ext : new String[]{".webp", ".jpg"}) {
            if (files.contains("fallback-" + slot + suffix + ext)) return "wallpapers/fallback-" + slot + suffix + ext;
        }
        return null;
    }

    private Bitmap decodeSharp(String key) {
        String path = find(key, false);
        if (path == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        // A hardware bitmap lives only in graphics memory, so the photo costs 8.3 MB once instead of
        // twice (a CPU copy plus its GPU texture). We never read its pixels; glass reads the blur.
        if (Build.VERSION.SDK_INT >= 26) o.inPreferredConfig = Bitmap.Config.HARDWARE;
        Bitmap b = decode(path, o);
        if (b == null && Build.VERSION.SDK_INT >= 26) {
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            b = decode(path, o);
        }
        return b;
    }

    private Bitmap decodeBlur(String key) {
        String path = find(key, true);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        if (path != null) {
            Bitmap b = decode(path, o);
            if (b != null) return b;
        }
        String sharp = find(key, false);
        if (sharp == null) return null;
        Log.i(TAG, "no blurred copy for " + key + ", making one");
        o.inSampleSize = 2;
        Bitmap half = decode(sharp, o);
        return half == null ? null : makeBlur(half);
    }

    private Bitmap decode(String path, BitmapFactory.Options o) {
        try (InputStream in = assets.open(path)) {
            return BitmapFactory.decodeStream(in, null, o);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "could not decode " + path, e);
            return null;
        }
    }

    /** DESIGN.md §6.1: area-downscale to 480x270, three box-blur passes of radius 6, saturation ×1.3. */
    static Bitmap makeBlur(Bitmap half) {
        Bitmap small = Bitmap.createScaledBitmap(half, 480, 270, true);
        if (small != half) half.recycle();
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        small.recycle();
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(px, tmp, w, h, 6, true);
            boxBlur(tmp, px, w, h, 6, false);
        }
        Bitmap blurred = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.30f);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(blurred, 0, 0, p);
        blurred.recycle();
        return out;
    }

    private static void boxBlur(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int rs = 0, gs = 0, bs = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[idx(line, Math.min(len - 1, Math.max(0, i)), w, horizontal)];
                rs += (c >> 16) & 255;
                gs += (c >> 8) & 255;
                bs += c & 255;
            }
            for (int i = 0; i < len; i++) {
                dst[idx(line, i, w, horizontal)] = 0xFF000000 | ((rs / div) << 16) | ((gs / div) << 8) | (bs / div);
                int out = src[idx(line, Math.max(0, i - r), w, horizontal)];
                int in = src[idx(line, Math.min(len - 1, i + r + 1), w, horizontal)];
                rs += ((in >> 16) & 255) - ((out >> 16) & 255);
                gs += ((in >> 8) & 255) - ((out >> 8) & 255);
                bs += (in & 255) - (out & 255);
            }
        }
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    /**
     * Glass alpha is solved, not picked (DESIGN.md §7, the prototype's adapt()): binary-search each
     * value until white text over the brightest 10 % of what sits behind the panel reaches its
     * contrast target. Composited in sRGB, as the GPU blends.
     */
    static final class Tuning {
        static final Tuning DEFAULT = new Tuning();
        private static final double[] DARK = {9, 13, 18};
        private static final double[] WHITE = {255, 255, 255};
        private static final double[] BLACK = {0, 0, 0};

        /** The dock's width for its app count (DESIGN.md: 448 for two); the launcher sets it. */
        static volatile int dockWidth = 448;

        float meanL, frost = .06f;
        float dock = .22f, gear = .22f, sheet = .30f;
        float milk = .66f, milkGear = .66f, milkSheet = .5f;
        float scrimTop = .2f, scrimBottom = .2f, scrimRow = .45f, scrimSettings = .3f;
        private double[][] grid;

        private Tuning() {}

        /** For checking the solver on the Mac against grids made there (no Bitmap, no log). */
        static Tuning fromGrid(double[][] g) {
            Tuning t = new Tuning();
            t.solve(g);
            return t;
        }

        Tuning(Bitmap blur) {
            solve(grid(blur));
            Log.i(TAG, String.format(java.util.Locale.ROOT,
                    "glass: meanL %.2f frost %.3f dock %.2f gear %.2f sheet %.2f milk %.2f/%.2f/%.2f scrims %.2f %.2f %.2f %.2f",
                    meanL, frost, dock, gear, sheet, milk, milkGear, milkSheet, scrimTop, scrimBottom, scrimRow, scrimSettings));
        }

        /** Takes the 32x18 grid, row-major, as sRGB triples. */
        private void solve(double[][] g) {
            grid = g;
            double sum = 0;
            for (double[] c : grid) sum += lum(c);
            meanL = (float) (sum / grid.length);
            frost = Math.round((0.10 - 0.06 * Math.min(1, meanL / .25)) * 1000) / 1000f;
            boolean nightish = meanL < .07;
            solveDock();
            List<double[]> gc = cells(1720, 80, 80, 80);
            gear = tintFor(gc, 3.0, frost, 1, .9);
            scrimSettings = scrimFor(cells(120, 96, 620, 720), 4.5, .22, 1);
            // The sheet draws the raw blur (the settings scrim is behind it) and rows scroll anywhere, so
            // solve against its brightest cell; its frost includes the 8 % sheen under the first rows.
            List<double[]> sc = cells(800, 72, 1000, 936);
            float sf = frost * .6f + .08f;
            sheet = Math.max(.16f, Math.max(tintFor(sc, 4.5, sf, .78, 1), tintFor(sc, 3.0, sf, .62, 1)));
            milkGear = milkFor(gc, nightish ? .36 : .60);
            milkSheet = milkFor(sc, nightish ? .28 : .42);
            // Text straight on the sharp photo: the blur under-reports bright detail, so these targets run higher.
            scrimTop = scrimFor(cells(1380, 80, 320, 80), 4.5, .14, .82);
            scrimBottom = scrimFor(cells(120, 960, 760, 48), 6, .16, .82);
            scrimRow = scrimFor(cells(120, 596, 900, 110), 6, .34, .66);
        }

        /** The dock's glass and droplet, over whatever the dock covers at its current width. */
        void solveDock() {
            if (grid == null) return;
            List<double[]> dc = cells(120, 712, dockWidth, 232);
            dock = Math.max(tintFor(dc, 4.5, frost, 1, .9), tintFor(dc, 3.0, frost, .64, .9)); // labels are 64 % white
            milk = milkFor(dc, meanL < .07 ? .30 : .50);
        }

        /** 32x18 mean sRGB colours of the blur: 60x60 px cells at full size. */
        private static double[][] grid(Bitmap blur) {
            int w = blur.getWidth(), h = blur.getHeight();
            int[] px = new int[w * h];
            blur.getPixels(px, 0, w, 0, 0, w, h);
            double[][] g = new double[32 * 18][];
            for (int gy = 0; gy < 18; gy++) {
                for (int gx = 0; gx < 32; gx++) {
                    int x0 = gx * w / 32, x1 = (gx + 1) * w / 32, y0 = gy * h / 18, y1 = (gy + 1) * h / 18;
                    double r = 0, gr = 0, b = 0;
                    int n = 0;
                    for (int y = y0; y < y1; y++) {
                        for (int x = x0; x < x1; x++) {
                            int c = px[y * w + x];
                            r += (c >> 16) & 255;
                            gr += (c >> 8) & 255;
                            b += c & 255;
                            n++;
                        }
                    }
                    n = Math.max(1, n);
                    g[gy * 32 + gx] = new double[]{r / n, gr / n, b / n};
                }
            }
            return g;
        }

        private List<double[]> cells(int x, int y, int w, int h) {
            List<double[]> out = new ArrayList<>();
            for (int gy = Math.max(0, y / 60); gy <= Math.min(17, (y + h - 1) / 60); gy++)
                for (int gx = Math.max(0, x / 60); gx <= Math.min(31, (x + w - 1) / 60); gx++)
                    out.add(grid[gy * 32 + gx]);
            return out;
        }

        private static double lin(double c) {
            c /= 255;
            return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
        }

        static double lum(double[] c) {
            return .2126 * lin(c[0]) + .7152 * lin(c[1]) + .0722 * lin(c[2]);
        }

        private static double[] mix(double[] a, double[] b, double t) {
            return new double[]{a[0] * (1 - t) + b[0] * t, a[1] * (1 - t) + b[1] * t, a[2] * (1 - t) + b[2] * t};
        }

        private static double[] pct(List<double[]> cs, double q) {
            List<double[]> s = new ArrayList<>(cs);
            s.sort((a, b) -> Double.compare(lum(a), lum(b)));
            return s.get(Math.min(s.size() - 1, (int) Math.floor(q * (s.size() - 1) + .5)));
        }

        private interface Composite { double[] at(double t); }
        private interface Ok { boolean test(double[] c); }

        private static double solve(Composite fn, double lo, double hi, Ok ok) {
            for (int i = 0; i < 14; i++) {
                double mid = (lo + hi) / 2;
                if (ok.test(fn.at(mid))) hi = mid;
                else lo = mid;
            }
            return hi;
        }

        /** inkA: alpha of the faintest white text on this glass (text = glass blended toward white by inkA). */
        private static float tintFor(List<double[]> cs, double target, double frost, double inkA, double q) {
            double[] p = pct(cs, q);
            double a = solve(t -> mix(mix(p, DARK, t), WHITE, frost), 0, .78, g -> {
                double[] text = mix(g, WHITE, inkA);
                return (lum(text) + .05) / (lum(g) + .05) >= target;
            });
            return (float) Math.min(.78, Math.max(.10, a));
        }

        private static float scrimFor(List<double[]> cs, double target, double min, double falloff) {
            double[] p = pct(cs, .9);
            double a = solve(t -> mix(p, BLACK, t), 0, .9, c -> 1.05 / (lum(c) + .05) >= target);
            return (float) Math.min(.86, Math.max(min, a / falloff));
        }

        private static float milkFor(List<double[]> cs, double target) {
            double[] p = pct(cs, .5);
            return (float) Math.min(.92, Math.max(.35, solve(t -> mix(p, WHITE, t), 0, 1, c -> lum(c) >= target)));
        }
    }
}
