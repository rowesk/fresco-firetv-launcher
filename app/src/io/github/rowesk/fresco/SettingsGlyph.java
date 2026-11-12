package io.github.rowesk.fresco;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** 2.5 physical-pixel strokes at both supported icon sizes; no existing drawable is modified. */
final class SettingsGlyph extends Drawable {
    private final String kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    SettingsGlyph(String kind) { this(kind, Ui.INK); }
    SettingsGlyph(String kind, int ink) { this.kind = kind; p.setColor(ink); p.setStrokeWidth(2.5f); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }
    @Override public void draw(Canvas c) {
        Rect b = getBounds(); float x = b.exactCenterX(), y = b.exactCenterY(), q = b.width() / 24f;
        Path path = new Path();
        if ("left".equals(kind) || "right".equals(kind)) {
            float d = "left".equals(kind) ? -1 : 1;
            path.moveTo(x - d * 3*q, y - 6*q); path.lineTo(x + d * 3*q, y); path.lineTo(x - d * 3*q, y + 6*q);
        } else if ("check".equals(kind)) {
            path.moveTo(x - 8.5f*q, y + .25f*q); path.lineTo(x - 3*q, y + 5.75f*q); path.lineTo(x + 8.5f*q, y - 5.75f*q);
        } else if ("ext".equals(kind)) {
            path.moveTo(x - 5*q, y + 5*q); path.lineTo(x + 5*q, y - 5*q); path.moveTo(x - 3*q, y - 5*q); path.lineTo(x + 5*q, y - 5*q); path.lineTo(x + 5*q, y + 3*q);
        } else if ("dot".equals(kind)) {
            c.drawCircle(x, y, Math.min(b.width(), b.height()) / 2f - 1.25f, p);
            return;
        } else {
            float arm = b.width() == 24 ? 8.5f : 7f;
            path.moveTo(x - arm*q, y); path.lineTo(x + arm*q, y); path.moveTo(x, y - arm*q); path.lineTo(x, y + arm*q);
        }
        c.drawPath(path, p);
    }
    @Override public void setAlpha(int a) { p.setAlpha(a); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

