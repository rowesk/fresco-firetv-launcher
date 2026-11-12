package io.github.rowesk.fresco;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The dock's marks, drawn once into small bitmaps: a white mono mark for rest and the colour mark for
 * focus (they crossfade). Stremio and YouTube are hand-drawn from the prototype's 100-unit SVGs; other
 * known apps draw their mark from assets/marks (MarkArt); any other app gets a monogram of its name in
 * a colour taken from its icon. No app's own icon is ever shown.
 */
final class Marks {
    private Marks() {}

    static final int SIZE = 96;
    /** Room around the mark for its soft drop shadow. */
    static final int PAD = 8;
    /** The monogram letter's size, and the stroke that brings Red Hat Display 500 up to the marks' weight. */
    static final float MONO_TEXT = 42, MONO_BOLD = 2.6f;

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

    /** A known app's mark from assets/marks as {rest, focus}, or null if its file can't be read. */
    static Bitmap[] brand(AssetManager assets, MarkArt.Brand b) {
        MarkArt.Art art;
        try (InputStream in = assets.open("marks/" + b.id + ".svg")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) bytes.write(buf, 0, n);
            art = MarkArt.load(new String(bytes.toByteArray(), StandardCharsets.UTF_8), b.fit);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        // Rest: every layer as one white shape, less the cut-outs.
        Path ink = new Path(), cut = new Path();
        for (MarkArt.Layer l : art.layers) (l.cut ? cut : ink).op(path(l), Path.Op.UNION);
        ink.op(cut, Path.Op.DIFFERENCE);
        Bitmap rest = mono(ink);

        // Focus: the layers in order, in the brand gradient, their own colour, or white.
        Bitmap focus = blank();
        Canvas c = canvas(focus);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader brand = b.top == b.bottom ? null : new LinearGradient(0, art.top, 0, art.bottom, b.top, b.bottom, Shader.TileMode.CLAMP);
        for (MarkArt.Layer l : art.layers) {
            boolean own = l.cut || l.fill != 0;
            p.setShader(own ? null : brand);
            p.setColor(l.cut ? 0xFFFFFFFF : own ? l.fill : b.top);
            c.drawPath(path(l), p);
        }
        return new Bitmap[]{rest, focus};
    }

    /**
     * Any other app: the first letter or digit of its name cut from a rounded tile, as {rest, focus}.
     * The focus tile takes the colour from accent(); the letter is white.
     */
    static Bitmap[] monogram(String name, int color) {
        Path tile = new Path();
        tile.addRoundRect(new RectF(20, 20, 80, 80), 16, 16, Path.Direction.CW);
        Path letter = letter(name);
        return new Bitmap[]{render(tile, letter, false, null, color), render(tile, letter, true, null, color)};
    }

    /** A monogram's colour from the app's icon, drawn small and freed straight away; the icon is never shown. */
    static int accent(Drawable icon) {
        if (icon == null) return MarkArt.NEUTRAL;
        int s = 32;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, s, s);
        icon.draw(new Canvas(b));
        int[] px = new int[s * s];
        b.getPixels(px, 0, s, 0, 0, s, s);
        b.recycle();
        return MarkArt.accent(px);
    }

    /** The name's first letter or digit in Red Hat Display, centred by its ink and thickened to match the marks. */
    private static Path letter(String name) {
        String ch = "?";
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                ch = new String(Character.toChars(cp)).toUpperCase(Locale.ROOT);
                break;
            }
            i += Character.charCount(cp);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Ui.display500 != null ? Ui.display500 : Typeface.DEFAULT_BOLD);
        p.setTextSize(MONO_TEXT);
        Path glyph = new Path();
        p.getTextPath(ch, 0, ch.length(), 0, 0, glyph);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(MONO_BOLD);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path bold = new Path();
        p.getFillPath(glyph, bold);
        bold.op(glyph, Path.Op.UNION);
        RectF r = new RectF();
        bold.computeBounds(r, true);
        bold.offset(50 - r.centerX(), 50 - r.centerY());
        return bold;
    }

    private static Path path(MarkArt.Layer l) {
        Path p = new Path();
        p.setFillType(l.evenOdd ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        float[] q = l.pts;
        for (int i = 0, k = 0; i < l.nOps; i++) {
            switch (l.ops[i]) {
                case MarkArt.MOVE: p.moveTo(q[k], q[k + 1]); k += 2; break;
                case MarkArt.LINE: p.lineTo(q[k], q[k + 1]); k += 2; break;
                case MarkArt.CUBIC: p.cubicTo(q[k], q[k + 1], q[k + 2], q[k + 3], q[k + 4], q[k + 5]); k += 6; break;
                default: p.close();
            }
        }
        return p;
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

    /** Rest: the shape less the play mark, white. Focus: the shape in brand colour with the play mark white on it. */
    private static Bitmap render(Path shape, Path play, boolean color, Shader brand, int brandColor) {
        if (!color) {
            Path cut = new Path(shape);
            cut.op(play, Path.Op.DIFFERENCE);
            return mono(cut);
        }
        Bitmap b = blank();
        Canvas c = canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (brand != null) p.setShader(brand);
        else p.setColor(brandColor);
        c.drawPath(shape, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        c.drawPath(play, p);
        return b;
    }

    /** Every mark at rest: white at 92 % with a soft shadow. */
    private static Bitmap mono(Path shape) {
        Bitmap b = blank();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Ui.white(.92f));
        p.setShadowLayer(3f / (SIZE / 100f), 0, 2f / (SIZE / 100f), 0x2E000000);
        canvas(b).drawPath(shape, p);
        return b;
    }

    private static Bitmap blank() {
        return Bitmap.createBitmap(SIZE + 2 * PAD, SIZE + 2 * PAD, Bitmap.Config.ARGB_8888);
    }

    /** A canvas in mark units: the 100 box mapped onto the SIZE px inside the shadow padding. */
    private static Canvas canvas(Bitmap b) {
        Canvas c = new Canvas(b);
        c.translate(PAD, PAD);
        c.scale(SIZE / 100f, SIZE / 100f);
        return c;
    }
}
