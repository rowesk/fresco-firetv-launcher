package io.github.rowesk.fresco;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Continue watching row (style A from the prototype): focus is anchored at x = 120 under its
 * title and the strip slides beneath it; titles you've passed slip out behind the left edge. The
 * focused poster itself lifts; neighbours settle back, opaque, slightly desaturated and dimmed.
 *
 * Positions are relative to the row, which sits at y = 1000 on the plane (the plane dips 400 px).
 */
final class ContinueRow extends FrameLayout {
    static final int PITCH = 204;

    private final Lane title, meta;
    private final View titleBar, metaBar;
    private final FrameLayout stripBump, strip;
    private final List<PosterView> posters = new ArrayList<>();
    private final List<GlassView> skeletons = new ArrayList<>();
    private final List<ObjectAnimator> breathing = new ArrayList<>();
    private List<StremioClient.Item> items = new ArrayList<>();
    private StremioClient.State state = StremioClient.State.NONE;
    private int idx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private static Handler decoder;
    private int posterGeneration;

    ContinueRow(Context context) {
        super(context);
        setClipChildren(false);
        title = Ui.add(this, new Lane(context, Ui.display500, 44, 1, 56, true), 96, -45, 1300, 72);
        title.setLetterSpacing(0);
        meta = Ui.add(this, new Lane(context, Ui.text500, 26, .86f, 32, false), 96, 23, 1300, 44);
        meta.setFontFeatures("tnum");
        titleBar = Ui.add(title, bar(context), 24, 12, 360, 40);
        metaBar = Ui.add(meta, bar(context), 24, 10, 240, 26);
        stripBump = Ui.add(this, new FrameLayout(context), 120, 128, 1800, 264);
        stripBump.setClipChildren(false);
        strip = Ui.add(stripBump, new FrameLayout(context), 0, 0, 1800, 264);
        strip.setClipChildren(false);
        if (decoder == null) {
            HandlerThread t = new HandlerThread("posters");
            t.start();
            decoder = new Handler(t.getLooper());
        }
    }

    private static View bar(Context c) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setColor(Ui.white(.14f));
        d.setCornerRadius(13);
        v.setBackground(d);
        v.setVisibility(GONE);
        return v;
    }

    @Override
    public boolean hasOverlappingRendering() {
        // Fading the row as a group would need a 1920-wide offscreen layer; per-view alpha is close enough.
        return false;
    }

    StremioClient.State state() { return state; }
    int index() { return idx; }
    int count() { return items.size(); }
    StremioClient.Item selected() { return idx < items.size() ? items.get(idx) : null; }

    /** Down opens the row when it is ready or loading; otherwise it bumps. */
    boolean canOpen() {
        return state == StremioClient.State.READY || state == StremioClient.State.LOADING;
    }

    void setPictureInk(boolean dark, boolean animate) {
        title.setPictureInk(dark, animate);
        meta.setPictureInk(dark, animate);
    }

    /** Actual widest strings, stable across focus changes; endpoints include the x=120 axis. */
    float[] textEnds() {
        Paint p = new Paint(); p.setTypeface(Ui.display500); p.setTextSize(44); p.setFontFeatureSettings("tnum");
        float tw = 0, mw = 0;
        for (StremioClient.Item it : items) tw = Math.max(tw, p.measureText(it.name));
        p.setTypeface(Ui.text500); p.setTextSize(26);
        for (StremioClient.Item it : items) {
            float w = p.measureText(it.episode());
            String left = it.left();
            if (left != null) w += (w > 0 ? 29 : 0) + p.measureText(left);
            mw = Math.max(mw, w);
        }
        return new float[]{Math.min(1396, 120 + tw), Math.min(1396, 120 + mw)};
    }

    /** Rebuilds the strip. Only called while the row is closed, so nothing moves under the user. */
    void setData(StremioClient.State s, List<StremioClient.Item> list, float skeletonTint, float frost) {
        stopBreathing();
        strip.removeAllViews();
        posters.clear();
        skeletons.clear();
        state = s;
        items = s == StremioClient.State.READY ? new ArrayList<>(list) : new ArrayList<>();
        idx = 0;
        Anim.set(strip, View.TRANSLATION_X, 0);
        title.clear();
        meta.clear();
        titleBar.setVisibility(s == StremioClient.State.LOADING ? VISIBLE : GONE);
        metaBar.setVisibility(s == StremioClient.State.LOADING ? VISIBLE : GONE);
        if (s == StremioClient.State.LOADING) {
            for (int i = 0; i < 6; i++) {
                GlassView g = new GlassView(getContext(), PosterView.R);
                g.mag = 1.04f;
                g.setTint(skeletonTint, frost);
                Ui.add(strip, g, i * PITCH, 0, PosterView.W, PosterView.H);
                skeletons.add(g);
            }
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            StremioClient.Item it = items.get(i);
            // No bar until you're properly into it: an untouched or barely started episode reads as new.
            float progress = it.duration > 0 ? it.progress() : -1;
            PosterView p = new PosterView(getContext(), progress >= .05f ? progress : -1);
            Ui.add(strip, p, i * PITCH, 0, PosterView.W, PosterView.H);
            posters.add(p);
        }
        if (!items.isEmpty()) showMeta(0, 0, false, false);
    }

    void setSkeletonTint(float tint, float frost) {
        for (GlassView g : skeletons) g.setTint(tint, frost);
    }

    /** Decodes the cached posters off the main thread (~260 KB each). */
    void loadPosters(StremioClient client) {
        final int gen = ++posterGeneration;
        final List<StremioClient.Item> list = new ArrayList<>(items);
        final List<PosterView> views = new ArrayList<>(posters);
        for (int i = 0; i < list.size(); i++) {
            final File f = client.posterFile(list.get(i));
            final PosterView v = views.get(i);
            if (v.art.getDrawable() != null) continue;
            decoder.post(() -> {
                if (gen != posterGeneration) return;
                final Bitmap b = StremioClient.decodePoster(f);
                main.post(() -> {
                    if (gen == posterGeneration && b != null) v.setBitmap(b);
                    else if (b != null) b.recycle();
                });
            });
        }
    }

    /** The launcher left the screen: drop poster bitmaps (they come back from disk on return). */
    void releasePosters() {
        posterGeneration++;
        for (PosterView p : posters) p.setBitmap(null);
    }

    /** Loading skeletons breathe 0.5 ↔ 0.9, staggered; they stop after 8 s even if the network never answers. */
    void startBreathing() {
        stopBreathing();
        for (int i = 0; i < skeletons.size(); i++) {
            ObjectAnimator a = ObjectAnimator.ofFloat(skeletons.get(i), View.ALPHA, .5f, .9f);
            a.setDuration(1400);
            a.setStartDelay(i * 120L);
            a.setInterpolator(Ui.BREATHE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(5);
            a.start();
            breathing.add(a);
        }
    }

    void stopBreathing() {
        for (ObjectAnimator a : breathing) a.cancel();
        breathing.clear();
    }

    /** The dip: posters from the focused one rightwards rise 36 px, staggered 34 ms (first 7). */
    void surface() {
        List<? extends View> list = state == StremioClient.State.LOADING ? skeletons : posters;
        int from = state == StremioClient.State.READY ? idx : 0;
        for (int j = 0; j + from < list.size() && j <= 6; j++) {
            Anim.fromTo(list.get(j + from), View.TRANSLATION_Y, 36, 0, 420, Ui.OUT, 100 + 34L * j);
        }
        if (state == StremioClient.State.LOADING) startBreathing();
    }

    /** The prototype's rowFocus. `on` is whether focus is in the row; `fast` is a held D-pad repeat. */
    void focus(int i, boolean on, boolean animate, boolean fast) {
        if (posters.isEmpty()) {
            idx = 0;
            return;
        }
        i = Math.max(0, Math.min(posters.size() - 1, i));
        int dir = Integer.signum(i - idx);
        idx = i;
        long up = fast ? 220 : 480, down = fast ? 160 : 340;
        for (int j = 0; j < posters.size(); j++) {
            PosterView p = posters.get(j);
            boolean f = on && j == i;
            float nudge = on ? (j > i ? 22 : 0) : 0;
            boolean gone = on && j < i;
            long dur = f ? up : down;
            android.animation.TimeInterpolator ease = f ? Ui.OUT : Ui.STD;
            long goneDur = gone ? (fast ? 120 : 260) : dur;
            android.animation.TimeInterpolator goneEase = gone ? Ui.IN : ease;
            go(p, View.TRANSLATION_X, nudge, goneDur, goneEase, animate);
            go(p, View.ALPHA, gone ? 0 : 1, goneDur, goneEase, animate);
            p.animateDim(on && !f ? 1 : 0, dur, ease, animate);
            go(p.lift, View.TRANSLATION_Y, f ? -25 : 0, dur, ease, animate);
            go(p.lift, View.SCALE_X, f ? 1.125f : 1, dur, ease, animate);
            go(p.lift, View.SCALE_Y, f ? 1.125f : 1, dur, ease, animate);
            go(p.shadow, View.ALPHA, f ? 1 : 0, dur, ease, animate);
            go(p.shadow, View.TRANSLATION_Y, f ? -25 + 18 : 0, dur, ease, animate);
            go(p.shadow, View.SCALE_X, f ? 1.125f : .94f, dur, ease, animate);
            go(p.shadow, View.SCALE_Y, f ? 1.125f : .94f, dur, ease, animate);
            go(p.rimf, View.ALPHA, f ? 1 : 0, f ? 320 : 160, ease, animate);
            if (f && animate && dir != 0 && !fast) {
                // Arrival: the poster turns to face you, and its artwork settles a beat later (parallax).
                Anim.fromTo(p.tilt, View.ROTATION_Y, -10 * dir, 0, 640, Ui.OUT, 0);
                Anim.set(p.art, View.SCALE_X, 1.05f);
                Anim.set(p.art, View.SCALE_Y, 1.05f);
                Anim.fromTo(p.art, View.TRANSLATION_X, 12 * dir, 0, 760, Ui.OUT, 0);
            } else {
                go(p.tilt, View.ROTATION_Y, 0, 200, Ui.STD, animate);
                go(p.art, View.TRANSLATION_X, 0, dur, ease, animate);
                go(p.art, View.SCALE_X, f ? 1.05f : 1, dur, ease, animate);
                go(p.art, View.SCALE_Y, f ? 1.05f : 1, dur, ease, animate);
            }
        }
        go(strip, View.TRANSLATION_X, -i * PITCH, fast ? 200 : 540, fast ? Ui.HELD : Ui.STD, animate);
        showMeta(i, dir, animate && dir != 0, fast);
    }

    private static void go(View v, android.util.Property<View, Float> p, float to, long ms,
                           android.animation.TimeInterpolator ease, boolean animate) {
        if (animate) Anim.to(v, p, to, ms, ease, 0);
        else Anim.set(v, p, to);
    }

    /** Edge feedback on the strip (its own wrapper, so it adds to the scroll offset). */
    void bump(int dx, int dy) {
        Anim.bump(stripBump, dx, dy);
    }

    /** Select press on the focused poster: the lift dips and comes back. */
    void press() {
        if (idx >= posters.size()) return;
        View l = posters.get(idx).lift;
        float[] f = {0, .5f, 1};
        Anim.keys(l, View.TRANSLATION_Y, f, new float[]{-25, -13, -25}, 200, Ui.STD, 0);
        Anim.keys(l, View.SCALE_X, f, new float[]{1.125f, 1.05f, 1.125f}, 200, Ui.STD, 0);
        Anim.keys(l, View.SCALE_Y, f, new float[]{1.125f, 1.05f, 1.125f}, 200, Ui.STD, 0);
    }

    private void showMeta(int i, int dir, boolean animate, boolean fast) {
        if (i >= items.size()) return;
        StremioClient.Item it = items.get(i);
        title.show(it.name, dir, animate, fast, 0);
        SpannableStringBuilder m = new SpannableStringBuilder(it.episode());
        String left = it.left();
        if (left != null) {
            if (m.length() > 0) {
                int at = m.length();
                m.append(" ");
                m.setSpan(new DotSpan(), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            m.append(left);
        }
        meta.show(m, dir, animate, fast, 70);
    }

    /**
     * A clipped, fixed-height text lane with soft edges. Two TextViews swap roles each move: the
     * current one drifts the way you're travelling and fades; the other arrives from the far side.
     * Whatever was still leaving is dropped, so held presses retarget instead of queueing.
     */
    static final class Lane extends FrameLayout {
        private final TextView[][] tx = new TextView[2][2]; // tone, moving page
        private final FrameLayout[] tones = new FrameLayout[2];
        private final boolean optical;
        private int cur;

        Lane(Context c, android.graphics.Typeface face, float size, float level, int baseline, boolean optical) {
            super(c);
            this.optical = optical;
            setClipChildren(true);
            setWillNotDraw(false);
            setHorizontalFadingEdgeEnabled(true);
            setFadingEdgeLength(24);
            for (int tone = 0; tone < 2; tone++) {
                tones[tone] = Ui.add(this, new FrameLayout(c), 0, 0, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                tones[tone].setAlpha(tone == 0 ? 1 : 0);
                for (int page = 0; page < 2; page++) {
                    TextView t = Ui.text(c, face, size, Ui.withAlpha(tone == 0 ? Ui.INK : Ui.INK_DARK, level));
                    Ui.lineHeight(t, size == 44 ? 52 : 32);
                    Ui.baseline(t, baseline);
                    t.setAlpha(page == 0 ? 1 : 0);
                    tx[tone][page] = Ui.add(tones[tone], t, 24, 0, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                }
            }
        }

        void setPictureInk(boolean dark, boolean animate) {
            for (int i = 0; i < 2; i++) {
                float a = (i == 1) == dark ? 1 : 0;
                if (animate) Anim.to(tones[i], View.ALPHA, a, 300, Ui.STD, 0);
                else Anim.set(tones[i], View.ALPHA, a);
            }
        }
        void setLetterSpacing(float em) { for (TextView[] pair : tx) for (TextView t : pair) t.setLetterSpacing(em); }
        void setFontFeatures(String f) { for (TextView[] pair : tx) for (TextView t : pair) t.setFontFeatureSettings(f); }
        void clear() {
            for (TextView[] pair : tx) for (TextView t : pair) {
                Anim.cancelAll(t); t.setText(""); t.setAlpha(0);
            }
        }
        void show(CharSequence text, int dir, boolean animate, boolean fast, long extraDelay) {
            for (int tone = 0; tone < 2; tone++) {
                TextView out = tx[tone][cur], in = tx[tone][1 - cur];
                float offset = optical ? Ui.opticalOffset(in, text) : 0;
                if (!animate) {
                    Anim.cancelAll(out); Anim.cancelAll(in);
                    in.setText(""); in.setAlpha(0);
                    out.setText(text); out.setAlpha(1); out.setTranslationX(offset);
                } else {
                    Anim.cancelAll(in); in.setText(text);
                    Anim.to(out, View.ALPHA, 0, fast ? 80 : 150, Ui.IN, 0);
                    Anim.to(out, View.TRANSLATION_X, -dir * (fast ? 16 : 48), fast ? 80 : 150, Ui.IN, 0);
                    long delay = fast ? 60 : 120 + extraDelay, ms = fast ? 200 : 540;
                    Anim.fromTo(in, View.ALPHA, 0, 1, ms, Ui.OUT, delay);
                    Anim.fromTo(in, View.TRANSLATION_X, offset + dir * (fast ? 18 : 56), offset, ms, Ui.OUT, delay);
                }
            }
            if (animate) cur = 1 - cur;
        }
        @Override protected float getLeftFadingEdgeStrength() { return 1f; }
        @Override protected float getRightFadingEdgeStrength() { return 1f; }
        @Override public boolean hasOverlappingRendering() { return false; }
    }

    /** A 5 px dot with 12 px either side, inheriting the run's 86 % picture ink. */
    static final class DotSpan extends ReplacementSpan {
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return 29;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            int c = paint.getColor();
            canvas.drawCircle(x + 14.5f, y - 7.5f, 2.5f, paint);
            paint.setColor(c);

        }
    }
}
