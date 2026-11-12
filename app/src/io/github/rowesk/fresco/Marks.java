package io.github.rowesk.fresco;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The dock's marks, drawn once into small bitmaps: a quiet mark for rest and the colour mark for
 * focus (they crossfade). Stremio and YouTube are hand-drawn from the prototype's 100-unit SVGs, a
 * white mono mark and the brand colours. Any other app shows its own icon in the same box.
 */
final class Marks {
    private Marks() {}

    static final int SIZE = 96;
    /** Room around the mark for its soft drop shadow. */
    static final int PAD = 8;
    /** Another app's icon: a rounded square a little inside the mark box, so it weighs about the same as the drawn marks. */
    private static final int ICON = 80;
    private static final float ICON_RADIUS = 18;

    static Bitmap stremio(boolean color) {
        Path shape = new Path();
        shape.addRoundRect(new RectF(21, 21, 79, 79), 13, 13, Path.Direction.CW);
        Matrix rot = new Matrix();
        rot.setRotate(45, 50, 50);
        shape.transform(rot);
        Path play = triangle(41, 35.5f, 65, 50, 41, 64.5f, 5);
        // The gradient runs corner to corner of the unrotated square, so after the 45° turn it reads top to bottom.
        Shader brand = new LinearGradient(50, 50 - 29 * 1.4142f, 50, 50 + 29 * 1.4142f, 0xFFA57BFF, 0xFF5A3FE3, Shader.TileMode.CLAMP);
        return render(shape, play, color, brand, 0);
    }

    static Bitmap youtube(boolean color) {
        Path shape = new Path();
        shape.addRoundRect(new RectF(12, 25, 88, 75), 15, 15, Path.Direction.CW);
        Path play = triangle(43, 38.5f, 62, 50, 43, 61.5f, 4);
        return render(shape, play, color, null, 0xFFFF0033);
    }

    /**
     * An app's own icon as {rest, focus}. Icons are full-colour artwork, so a white silhouette would
     * be a blank square; instead rest mutes the colour (30 % saturation, 90 % alpha) with the mono
     * marks' soft shadow, and focus wakes it on the milk glass, as the brand marks do.
     */
    static Bitmap[] icon(Drawable d) {
        Bitmap art = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, ICON, ICON);
        d.draw(new Canvas(art));
        ColorMatrix muted = new ColorMatrix();
        muted.setSaturation(.3f);
        Bitmap rest = iconMark(art, new ColorMatrixColorFilter(muted), .9f, true);
        Bitmap focus = iconMark(art, null, 1, false);
        art.recycle();
        return new Bitmap[]{rest, focus};
    }

    private static Bitmap iconMark(Bitmap art, ColorMatrixColorFilter filter, float alpha, boolean shadow) {
        int s = SIZE + 2 * PAD;
        float o = PAD + (SIZE - ICON) / 2f;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        // A shader-filled round rect, so the corners are anti-aliased (a clipPath on a bitmap canvas isn't).
        BitmapShader shader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setTranslate(o, o);
        shader.setLocalMatrix(m);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(shader);
        p.setColorFilter(filter);
        p.setAlpha(Math.round(alpha * 255));
        new Canvas(b).drawRoundRect(o, o, o + ICON, o + ICON, ICON_RADIUS, ICON_RADIUS, p);
        if (!shadow) return b;
        // The shadow follows the icon's own alpha, so a round icon gets a round shadow.
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(3f * SIZE / 100f, BlurMaskFilter.Blur.NORMAL));
        int[] at = new int[2];
        Bitmap sh = b.extractAlpha(blur, at);
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint sp = new Paint();
        sp.setColor(0x2E000000);
        c.drawBitmap(sh, at[0], at[1] + 2f * SIZE / 100f, sp);
        c.drawBitmap(b, 0, 0, null);
        sh.recycle();
        b.recycle();
        return out;
    }

    /** A triangle with the SVG's round-joined stroke folded into its outline. */
    private static Path triangle(float x0, float y0, float x1, float y1, float x2, float y2, float stroke) {
        Path t = new Path();
        t.moveTo(x0, y0);
        t.lineTo(x1, y1);
        t.lineTo(x2, y2);
        t.close();
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path outline = new Path();
        p.getFillPath(t, outline);
        outline.op(t, Path.Op.UNION);
        return outline;
    }

    private static Bitmap render(Path shape, Path play, boolean color, Shader brand, int brandColor) {
        Bitmap b = Bitmap.createBitmap(SIZE + 2 * PAD, SIZE + 2 * PAD, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.translate(PAD, PAD);
        c.scale(SIZE / 100f, SIZE / 100f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (color) {
            if (brand != null) p.setShader(brand);
            else p.setColor(brandColor);
            c.drawPath(shape, p);
            p.setShader(null);
            p.setColor(0xFFFFFFFF);
            c.drawPath(play, p);
        } else {
            Path cut = new Path(shape);
            cut.op(play, Path.Op.DIFFERENCE);
            p.setColor(Ui.white(.92f));
            p.setShadowLayer(3f / (SIZE / 100f), 0, 2f / (SIZE / 100f), 0x2E000000);
            c.drawPath(cut, p);
        }
        return b;
    }
}
