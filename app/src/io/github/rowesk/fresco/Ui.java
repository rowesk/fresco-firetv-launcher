package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.text.Layout;
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

    static final int FRAME_X = 120, FRAME_Y = 80, FRAME_RIGHT = 1800, FRAME_BOTTOM = 1000, MEASURE = 600;
    static final int TYPE_DISPLAY = 60, LINE_DISPLAY = 72, TYPE_TITLE = 44, LINE_TITLE = 52,
            TYPE_ROW = 32, LINE_ROW = 40, TYPE_BODY = 26, LINE_BODY = 32, LINE_PARAGRAPH = 38,
            TYPE_CAPTION = 22, LINE_CAPTION = 28;
    static final float TRACK_DISPLAY = -1;
    static final int AMBER_MILK = 0xFFC26A12, TOAST_INK = 0xFFFFF6EC, TOAST_SECONDARY_RGB = 0xFFECD6;
    static final int SPACE_XS = 8, SPACE_S = 12, SPACE_M = 16, SPACE_L = 24,
            SPACE_XL = 32, SPACE_2XL = 48, SPACE_3XL = 80, SPACE_4XL = 120;
    static final float GLASS_1 = 1, GLASS_2 = .78f, GLASS_3 = .62f,
            PHOTO_1 = 1, PHOTO_2 = .86f, MILK_1 = 1, MILK_2 = .70f, TRACK = .24f;
    static final int ICON_INLINE = 24, ICON_ROW = 32, ICON_GEAR = 36;
    static final float ICON_STROKE = 2.5f;
    static final int R_DOCK = 64, R_DOCK_DROP = 52, R_SHEET = 48, R_ROW_DROP = 36,
            R_THUMB = 20, R_PREVIEW = 24, R_PREVIEW_DROP = 16, R_TOAST = 48,
            R_DISC = 32, R_SWITCH = 21, R_KNOB = 16, R_GEAR = 40, R_POSTER = 22, R_TRACK = 5;
    enum TypeRole { DISPLAY, TITLE, ROW_LABEL, ROW_VALUE, BODY, META, EYEBROW, CAPTION, PARAGRAPH }

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
        t.setFontFeatureSettings("tnum");
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setFocusable(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /** The five type roles, with explicit weights and single-line/paragraph rhythm. */
    static TextView role(Context c, TypeRole role, int color) {
        Typeface face = text500;
        int size = TYPE_BODY, line = LINE_BODY;
        switch (role) {
            case DISPLAY: face = display400; size = TYPE_DISPLAY; line = LINE_DISPLAY; break;
            case TITLE: face = display500; size = TYPE_TITLE; line = LINE_TITLE; break;
            case ROW_LABEL: size = TYPE_ROW; line = LINE_ROW; break;
            case ROW_VALUE: face = text400; size = TYPE_ROW; line = LINE_ROW; break;
            case BODY: case PARAGRAPH: face = text400; break;
            case EYEBROW: face = text600; break;
            case CAPTION: size = TYPE_CAPTION; line = LINE_CAPTION; break;
            default: break;
        }
        TextView t = text(c, face, size, color);
        t.setLetterSpacing(role == TypeRole.DISPLAY ? TRACK_DISPLAY / TYPE_DISPLAY : 0);
        if (role == TypeRole.PARAGRAPH) {
            t.setSingleLine(false);
            t.setMaxLines(3);
            t.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
            t.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
            line = LINE_PARAGRAPH;
        }
        lineHeight(t, line);
        return t;
    }

    /** Position a single line by baseline within its box, independent of font ascenders/descenders. */
    static void baseline(TextView t, int baseline) {
        t.setGravity(Gravity.TOP | (t.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK));
        t.setPadding(t.getPaddingLeft(), 0, t.getPaddingRight(), 0);
        t.setFirstBaselineToTopHeight(baseline);
    }

    /** Place a complete line by an absolute baseline; useful for settings' cap-height rhythm. */
    static TextView baseline(FrameLayout parent, TextView t, int x, float base, int width, boolean optical) {
        t.setGravity(Gravity.TOP | Gravity.LEFT);
        t.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        add(parent, t, x, Math.round(base - t.getBaseline()), width, t.getMeasuredHeight());
        if (optical) opticalLeft(t);
        return t;
    }

    static TextView capCentered(FrameLayout parent, TextView t, int x, int width, int height) {
        return baseline(parent, t, x, height / 2f + capHeight(t.getPaint()) / 2, width, false);
    }

    static float capHeight(android.graphics.Paint p) {
        Rect b = new Rect();
        p.getTextBounds("H", 0, 1, b);
        return -b.top;
    }

    /** Optical offset only; apply it in addition to any motion translation. */
    static float opticalOffset(TextView t, CharSequence text) {
        if (text == null || text.length() == 0) return 0;
        String first = text.subSequence(0, Character.charCount(Character.codePointAt(text, 0))).toString();
        Rect b = new Rect();
        t.getPaint().getTextBounds(first, 0, first.length(), b);
        return -b.left;
    }

    static void opticalLeft(TextView t) { t.setTranslationX(opticalOffset(t, t.getText())); }

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
