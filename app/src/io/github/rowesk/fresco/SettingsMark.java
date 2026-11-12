package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Shared 48 px app art with full-white preview marks and brand colour on focus. */
final class SettingsMark extends View {
    private final Bitmap[] art;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private boolean focused;
    private final float restAlpha;
    SettingsMark(Context c, Bitmap[] art) { this(c, art, .78f); }
    SettingsMark(Context c, Bitmap[] art, float restAlpha) { super(c); this.art = art; this.restAlpha = restAlpha; }
    void setFocusedMark(boolean f) { if (focused != f) { focused = f; invalidate(); } }
    @Override protected void onDraw(Canvas c) {
        Bitmap b = art[focused ? 1 : 0]; if (b == null || b.isRecycled()) return;
        // Crop the shared 48 px mark's transparent padding.
        source.set(Marks.ROW_PAD, Marks.ROW_PAD, Marks.ROW_PAD + Marks.ROW_SIZE, Marks.ROW_PAD + Marks.ROW_SIZE);
        target.set(0, 0, getWidth(), getHeight());
        paint.setColorFilter(focused ? null : new PorterDuffColorFilter(Ui.INK, PorterDuff.Mode.SRC_IN));
        paint.setAlpha(focused ? 255 : Math.round(restAlpha * 255));
        c.drawBitmap(b, source, target, paint);
    }
}

