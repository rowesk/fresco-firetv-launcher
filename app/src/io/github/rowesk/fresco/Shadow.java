package io.github.rowesk.fresco;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.HashMap;

/**
 * Soft drop shadows, rendered once per size into small ALPHA_8 bitmaps at half resolution and
 * drawn stretched. They stand in for the design's 9-patches: static, one draw each, never animated
 * except through the alpha and transform of the view that owns them.
 */
final class Shadow {
    private Shadow() {}

    /** A rendered shadow: draw `bitmap` into (−margin, −margin, w + margin, h + margin). */
    static final class Baked {
        final Bitmap bitmap;
        final int margin;

        Baked(Bitmap bitmap, int margin) {
            this.bitmap = bitmap;
            this.margin = margin;
        }

        void draw(Canvas c, float w, float h, Paint paint) {
            c.drawBitmap(bitmap, null, new RectF(-margin, -margin, w + margin, h + margin), paint);
        }
    }

    private static final HashMap<String, Baked> cache = new HashMap<>();

    /**
     * @param layers CSS box-shadow layers as {offsetY, blur, alpha} triples, e.g. {18, 48, .26f}
     */
    static synchronized Baked get(int w, int h, float radius, float[] layers) {
        StringBuilder key = new StringBuilder().append(w).append('x').append(h).append('r').append(radius);
        for (float f : layers) key.append(',').append(f);
        Baked b = cache.get(key.toString());
        if (b != null) return b;

        int margin = 0;
        for (int i = 0; i < layers.length; i += 3) margin = Math.max(margin, (int) Math.ceil(layers[i + 1] + Math.abs(layers[i])));
        margin += 2;
        Bitmap bmp = Bitmap.createBitmap((w + 2 * margin + 1) / 2, (h + 2 * margin + 1) / 2, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(bmp);
        c.scale(.5f, .5f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < layers.length; i += 3) {
            // CSS blur b is a Gaussian with sigma b/2; Skia's radius → sigma is 0.57735·r + 0.5.
            float sigma = layers[i + 1] / 2f;
            float r = Math.max(.1f, (sigma - .5f) / .57735f);
            p.setMaskFilter(new BlurMaskFilter(r, BlurMaskFilter.Blur.NORMAL));
            p.setAlpha(Math.round(layers[i + 2] * 255));
            c.drawRoundRect(margin, margin + layers[i], margin + w, margin + h + layers[i], radius, radius, p);
        }
        b = new Baked(bmp, margin);
        cache.put(key.toString(), b);
        return b;
    }
}
