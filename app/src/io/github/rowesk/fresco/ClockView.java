package io.github.rowesk.fresco;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * The 24-hour clock, right-aligned in its box. Its own small view, so the once-a-minute tick
 * redraws only this (the launcher's one idle redraw). Only digits that change roll: the old one
 * rises 18 px and fades (220 ms, in), the new one rises from 18 px (340 ms, out, 60 ms later).
 */
final class ClockView extends View {
    private static final float DIGIT_W = 36f;   // .6em of 60 px, tabular
    private static final float COLON_W = 16.8f; // .28em

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final char[] shown = {' ', ' ', ' ', ' '};
    private final char[] previous = {' ', ' ', ' ', ' '};
    private final boolean[] rolling = new boolean[4];
    private float t = 1; // ms into the roll / 400
    private ValueAnimator roll;
    private final float capHeight;

    ClockView(Context context) {
        super(context);
        paint.setTypeface(Ui.display400);
        paint.setTextSize(60);
        paint.setColor(Ui.INK);
        paint.setLetterSpacing(-1f / 60);
        paint.setFontFeatureSettings("tnum");
        paint.setTextAlign(Paint.Align.CENTER);
        capHeight = Ui.capHeight(paint);
    }

    void setInk(int color) { paint.setColor(color); invalidate(); }

    void setTime(String hhmm, boolean animate) {
        char[] next = {hhmm.charAt(0), hhmm.charAt(1), hhmm.charAt(3), hhmm.charAt(4)};
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            rolling[i] = animate && shown[i] != ' ' && shown[i] != next[i];
            any |= rolling[i];
            previous[i] = shown[i];
            shown[i] = next[i];
        }
        if (roll != null) roll.cancel();
        if (any) {
            roll = ValueAnimator.ofFloat(0, 400);
            roll.setDuration(400);
            roll.setInterpolator(null);
            roll.addUpdateListener(a -> {
                t = (float) a.getAnimatedValue();
                invalidate();
            });
            roll.start();
        } else {
            t = 400;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight();
        float base = h / 2 + capHeight / 2;
        float x = getWidth() - (4 * DIGIT_W + COLON_W);
        for (int i = 0; i < 4; i++) {
            float left = x + i * DIGIT_W + (i >= 2 ? COLON_W : 0);
            c.save();
            c.clipRect(left, 0, left + DIGIT_W, h);
            if (rolling[i] && t < 400) {
                float out = Ui.IN.getInterpolation(Math.min(1, t / 220f));
                drawChar(c, previous[i], left, base - 18 * out, 1 - out);
                float in = Ui.OUT.getInterpolation(Math.max(0, Math.min(1, (t - 60) / 340f)));
                drawChar(c, shown[i], left, base + 18 * (1 - in), in);
            } else {
                drawChar(c, shown[i], left, base, 1);
            }
            c.restore();
            if (i == 1) {
                paint.setAlpha(Math.round(.86f * 255));
                c.drawText(":", left + DIGIT_W + COLON_W / 2, base - 4, paint);
            }
        }
    }

    private void drawChar(Canvas c, char ch, float left, float y, float alpha) {
        if (alpha <= 0) return;
        paint.setAlpha(Math.round(alpha * 255));
        c.drawText(String.valueOf(ch), left + DIGIT_W / 2, y, paint);
    }
}
