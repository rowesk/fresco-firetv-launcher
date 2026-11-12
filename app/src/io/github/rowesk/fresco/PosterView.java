package io.github.rowesk.fresco;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * One Continue watching poster. Each level owns exactly one transform, so animations never fight
 * (DESIGN.md §8 "Poster"):
 *   this: x nudge, alpha, dip offset
 *     shadow: alpha, translationY, scale
 *     lift: translationY, scale (pivot 50 % / 60 %)
 *       tilt: rotationY
 *         img (clipped, radius 22): art (translationX, scale), bottom gradient
 *         chrome (edge + progress), rimf (alpha)
 */
final class PosterView extends FrameLayout {
    static final int W = 176, H = 264, R = 22;

    final ShadowView shadow;
    final FrameLayout lift, tilt, img;
    final ImageView art;
    final View rimf;
    private float dim;
    private ValueAnimator dimAnim;

    PosterView(Context context, float progress) {
        super(context);
        setClipChildren(false);
        shadow = new ShadowView(context, W - 12, H - 10, R, new float[]{26, 52, .55f, 8, 18, .25f});
        Ui.add(this, shadow, 6, 10, W - 12, H - 10);
        shadow.setPivotX((W - 12) / 2f);
        shadow.setPivotY(H - 10);
        shadow.setAlpha(0);
        shadow.setScaleX(.94f);
        shadow.setScaleY(.94f);

        lift = Ui.add(this, new FrameLayout(context), 0, 0, W, H);
        lift.setClipChildren(false);
        lift.setPivotX(W * .5f);
        lift.setPivotY(H * .6f);
        tilt = Ui.add(lift, new FrameLayout(context), 0, 0, W, H);
        tilt.setCameraDistance(8000 * context.getResources().getDisplayMetrics().density);

        img = Ui.add(tilt, new FrameLayout(context), 0, 0, W, H);
        img.setBackgroundColor(0xFF1A1F26);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), R);
            }
        });
        img.setClipToOutline(true);

        art = Ui.add(img, new ImageView(context), 0, 0, W, H);
        art.setScaleType(ImageView.ScaleType.FIT_XY);
        View grad = new View(context);
        grad.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x9E000000, 0}));
        Ui.add(img, grad, 0, H - Math.round(H * .34f), W, Math.round(H * .34f));

        Ui.add(tilt, new ChromeView(context, progress), 0, 0, W, H);
        rimf = Ui.add(tilt, new RimView(context), 0, 0, W, H);
        rimf.setAlpha(0);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // Fading a poster as a group draws it into an offscreen layer, and Fire OS's renderer drops the
        // rounded outline clip there: posters slipping past the left edge flashed square. Fading the
        // children one by one keeps the clip.
        return false;
    }

    void setBitmap(Bitmap b) {
        // setImageBitmap(null) still leaves an empty BitmapDrawable, which made loadPosters think the
        // poster was already there after a release, so the row came back blank.
        if (b == null) art.setImageDrawable(null);
        else art.setImageBitmap(b);
    }

    /** 0 = full colour (focused or row closed); 1 = settled back: 15 % desaturated, 6 % dimmed, still opaque. */
    void animateDim(float to, long ms, android.animation.TimeInterpolator ease, boolean animate) {
        if (dimAnim != null) dimAnim.cancel();
        if (!animate || ms == 0) {
            setDim(to);
            return;
        }
        if (dim == to) return;
        dimAnim = ValueAnimator.ofFloat(dim, to);
        dimAnim.setDuration(ms);
        dimAnim.setInterpolator(ease);
        dimAnim.addUpdateListener(a -> setDim((float) a.getAnimatedValue()));
        dimAnim.start();
    }

    private void setDim(float d) {
        dim = d;
        if (d <= 0.001f) {
            art.setColorFilter(null);
            return;
        }
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1 - .15f * d);
        float k = 1 - .06f * d;
        ColorMatrix dimm = new ColorMatrix();
        dimm.setScale(k, k, k, 1);
        cm.postConcat(dimm);
        art.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    /** The 1.5 px inner edge and the progress bar. Static. */
    private static final class ChromeView extends View {
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float progress;

        ChromeView(Context c, float progress) {
            super(c);
            this.progress = progress;
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.5f);
            edge.setColor(Ui.white(.16f));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawRoundRect(.75f, .75f, w - .75f, h - .75f, R - .75f, R - .75f, edge);
            if (progress < 0) return;
            float l = 14, r = w - 14, b = h - 14, t = b - 6;
            bar.setColor(Ui.white(.30f));
            c.drawRoundRect(l, t, r, b, 3, 3, bar);
            if (progress > 0) {
                bar.setColor(Ui.INK);
                c.drawRoundRect(l, t, l + Math.max(6, (r - l) * progress), b, 3, 3, bar);
            }
        }
    }

    /** The focused poster's top-lit rim: 2 px white 55 %, masked from full at the top to 15 % at the bottom. */
    private static final class RimView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        RimView(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{Ui.white(.55f), Ui.white(.55f * .35f), Ui.white(.55f * .15f)},
                    new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRoundRect(1, 1, getWidth() - 1, getHeight() - 1, R - 1, R - 1, p);
        }
    }

    /** A static soft shadow; its alpha and transform animate. */
    static final class ShadowView extends View {
        private final Shadow.Baked baked;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);

        ShadowView(Context c, int w, int h, float r, float[] layers) {
            super(c);
            baked = Shadow.get(w, h, r, layers);
            p.setColor(0xFF000000);
        }

        @Override
        protected void onDraw(Canvas c) {
            baked.draw(c, getWidth(), getHeight(), p);
        }
    }
}
