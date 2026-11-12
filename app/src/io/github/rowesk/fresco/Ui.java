package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Tokens shared by the launcher and the service: easing, colours, the two bundled fonts, and
 * a few layout helpers. Every length in this app is a real pixel at 1920x1080 (DESIGN.md).
 */
final class Ui {
    private Ui() {}

    // Easing tokens, DESIGN.md §10.
    static final Interpolator OUT = new PathInterpolator(.16f, 1f, .3f, 1f);
    static final Interpolator STD = new PathInterpolator(.2f, .8f, .2f, 1f);
    static final Interpolator IN = new PathInterpolator(.4f, 0f, 1f, 1f);
    static final Interpolator DROP = new PathInterpolator(.3f, .9f, .25f, 1f);
    static final Interpolator DEVELOP = new PathInterpolator(.45f, 0f, .25f, 1f);
    static final Interpolator BREATHE = new PathInterpolator(.45f, 0f, .55f, 1f);
    /** Held D-pad: short retargeting moves. */
    static final Interpolator HELD = new PathInterpolator(.25f, .6f, .3f, 1f);
    static final Interpolator AMBIENT = new PathInterpolator(.4f, 0f, .6f, 1f);
    static final Interpolator FOLD = new PathInterpolator(.2f, .6f, .3f, 1f);
    static final Interpolator GLIDE = new PathInterpolator(.45f, 0f, .2f, 1f);

    static final int INK = 0xFFFFFFFF;
    static final int INK_DARK = 0xFF10151B;
    static final int AMBER = 0xFFFFB25C;

    static Typeface display400, display500, text400, text500, text600;

    static int white(float a) {
        return (Math.round(a * 255) << 24) | 0xFFFFFF;
    }

    static int withAlpha(int rgb, float a) {
        return (Math.round(a * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Builds the five weights once. Variable-font weights need API 26; the stick is API 28. */
    static synchronized void initFonts(Context context) {
        if (display400 != null) return;
        Context app = context.getApplicationContext();
        display400 = font(app, "fonts/RedHatDisplay-VF.ttf", 400);
        display500 = font(app, "fonts/RedHatDisplay-VF.ttf", 500);
        text400 = font(app, "fonts/RedHatText-VF.ttf", 400);
        text500 = font(app, "fonts/RedHatText-VF.ttf", 500);
        text600 = font(app, "fonts/RedHatText-VF.ttf", 600);
    }

    private static Typeface font(Context context, String path, int weight) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                return new Typeface.Builder(context.getAssets(), path)
                        .setFontVariationSettings("'wght' " + weight).build();
            }
            return Typeface.createFromAsset(context.getAssets(), path);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static TextView text(Context context, Typeface face, float px, int color) {
        TextView t = new TextView(context);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setFocusable(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /** CSS line-height for multi-line text. TextView.setLineHeight needs API 28. */
    static void lineHeight(TextView t, int px) {
        if (Build.VERSION.SDK_INT >= 28) {
            t.setLineHeight(px);
        } else {
            int natural = t.getPaint().getFontMetricsInt(null);
            t.setLineSpacing(px - natural, 1f);
        }
    }

    /** A FrameLayout child placed at an absolute position. */
    static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    static <T extends View> T add(FrameLayout parent, T child, int x, int y, int w, int h) {
        parent.addView(child, at(x, y, w, h));
        return child;
    }

    /**
     * Where a view's rect lands on the stage, after every transform between them. Glass uses this
     * to pick the patch of wallpaper it shows, so it stays right while panels move and scale.
     */
    static void mapToStage(View v, View stage, RectF out) {
        out.set(0, 0, v.getWidth(), v.getHeight());
        View cur = v;
        while (cur != null && cur != stage) {
            Matrix m = cur.getMatrix();
            if (!m.isIdentity()) m.mapRect(out);
            out.offset(cur.getLeft() - cur.getScrollX(), cur.getTop() - cur.getScrollY());
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
    }

    static String hhmm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return two(m / 60) + ":" + two(m % 60);
    }

    static String two(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }
}
