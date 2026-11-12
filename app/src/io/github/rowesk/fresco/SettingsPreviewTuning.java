package io.github.rowesk.fresco;

import android.graphics.Bitmap;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The mock's dock material, solved on the miniature's own patch, never during an animation frame. */
final class SettingsPreviewTuning {
    private WeakReference<Bitmap> source = new WeakReference<>(null);
    private final Map<String, float[]> solved = new HashMap<>();

    void clear() { source.clear(); solved.clear(); }

    float[] at(Bitmap blur, Wallpapers.Tuning tuning, int x, int y, int width) {
        if (blur == null || blur.isRecycled()) return new float[]{tuning.dock, tuning.milk};
        if (source.get() != blur) { source = new WeakReference<>(blur); solved.clear(); }
        String key = x + ":" + y + ":" + width + ":" + tuning.frost;
        float[] cached = solved.get(key);
        if (cached != null) return cached;
        List<double[]> cells = new ArrayList<>();
        for (int gy = Math.max(0, y / 60); gy <= Math.min(17, (y + 79) / 60); gy++) {
            for (int gx = Math.max(0, x / 60); gx <= Math.min(31, (x + width - 1) / 60); gx++) {
                int x0 = gx * blur.getWidth() / 32, x1 = (gx + 1) * blur.getWidth() / 32;
                int y0 = gy * blur.getHeight() / 18, y1 = (gy + 1) * blur.getHeight() / 18;
                int w = x1 - x0, h = y1 - y0;
                int[] pixels = new int[w * h];
                blur.getPixels(pixels, 0, w, x0, y0, w, h);
                double r = 0, g = 0, b = 0;
                for (int pixel : pixels) { r += (pixel >> 16) & 255; g += (pixel >> 8) & 255; b += pixel & 255; }
                cells.add(new double[]{r / pixels.length, g / pixels.length, b / pixels.length});
            }
        }
        cells.sort((a, b) -> Double.compare(Wallpapers.Tuning.lum(a), Wallpapers.Tuning.lum(b)));
        double[] median = percentile(cells, .5);
        double lo = 0, hi = 1, target = tuning.meanL < .07 ? .30 : .50;
        for (int i = 0; i < 14; i++) { double mid = (lo + hi) / 2; if (Wallpapers.Tuning.lum(mix(median, new double[]{255, 255, 255}, mid)) >= target) hi = mid; else lo = mid; }
        float milk = (float) Math.max(.35, Math.min(.92, hi));
        float tint = Math.max(tint(percentile(cells, .9), tuning.frost, 1, 4.5), tint(percentile(cells, 1), tuning.frost, .62, 3.2));
        float[] result = {tint, milk}; solved.put(key, result); return result;
    }

    private static double[] percentile(List<double[]> cells, double q) {
        return cells.get(Math.min(cells.size() - 1, (int) Math.floor(q * (cells.size() - 1) + .5)));
    }

    private static float tint(double[] pixel, double frost, double inkAlpha, double target) {
        double lo = 0, hi = .78;
        double[] white = {255, 255, 255}, dark = {9, 13, 18};
        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            double[] glass = mix(mix(pixel, dark, mid), white, frost);
            double ratio = (Wallpapers.Tuning.lum(mix(glass, white, inkAlpha)) + .05) / (Wallpapers.Tuning.lum(glass) + .05);
            if (ratio >= target) hi = mid; else lo = mid;
        }
        return (float) Math.max(.10, Math.min(.78, hi));
    }

    private static double[] mix(double[] a, double[] b, double fraction) {
        return new double[]{a[0] * (1 - fraction) + b[0] * fraction, a[1] * (1 - fraction) + b[1] * fraction, a[2] * (1 - fraction) + b[2] * fraction};
    }
}
