package io.github.rowesk.fresco;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.util.Property;
import android.view.View;

import java.util.HashMap;

/**
 * Interruptible property animations: a new animation of a property cancels the old one and starts
 * from wherever the property is now, so a key press never queues behind a running move
 * (the prototype's animTo). One running animator per view and property.
 */
final class Anim {
    private Anim() {}

    private static final class Slot {
        final HashMap<Property<View, Float>, Animator> running = new HashMap<>();
        int layers;
    }

    private static Slot slot(View v) {
        Object tag = v.getTag(R.id.anims);
        if (tag instanceof Slot) return (Slot) tag;
        Slot s = new Slot();
        v.setTag(R.id.anims, s);
        return s;
    }

    static ObjectAnimator to(View v, Property<View, Float> p, float to, long ms, TimeInterpolator ease, long delay) {
        return fromTo(v, p, p.get(v), to, ms, ease, delay, false);
    }

    static ObjectAnimator to(View v, Property<View, Float> p, float to, long ms, TimeInterpolator ease) {
        return fromTo(v, p, p.get(v), to, ms, ease, 0, false);
    }

    /** Fades through a transient hardware layer, so overlapping children fade as one (DESIGN.md §12.10). */
    static ObjectAnimator alphaWithLayer(View v, float to, long ms, TimeInterpolator ease, long delay) {
        return fromTo(v, View.ALPHA, v.getAlpha(), to, ms, ease, delay, true);
    }

    /** Jumps to `from` now (held through any delay), then animates to `to`. */
    static ObjectAnimator fromTo(View v, Property<View, Float> p, float from, float to, long ms,
                                 TimeInterpolator ease, long delay) {
        return fromTo(v, p, from, to, ms, ease, delay, false);
    }

    private static ObjectAnimator fromTo(View v, Property<View, Float> p, float from, float to, long ms,
                                         TimeInterpolator ease, long delay, boolean layer) {
        cancel(v, p);
        p.set(v, from);
        ObjectAnimator a = ObjectAnimator.ofFloat(v, p, from, to);
        return run(v, p, a, ms, ease, delay, layer);
    }

    /** Keyframed values at the given fractions; the easing applies to the whole run, keyframes are linear. */
    static ObjectAnimator keys(View v, Property<View, Float> p, float[] fractions, float[] values, long ms,
                               TimeInterpolator ease, long delay) {
        cancel(v, p);
        Keyframe[] k = new Keyframe[values.length];
        for (int i = 0; i < values.length; i++) k[i] = Keyframe.ofFloat(fractions[i], values[i]);
        p.set(v, values[0]);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, PropertyValuesHolder.ofKeyframe(p, k));
        return run(v, p, a, ms, ease, delay, false);
    }

    private static ObjectAnimator run(final View v, final Property<View, Float> p, final ObjectAnimator a, long ms,
                                      TimeInterpolator ease, long delay, final boolean layer) {
        final Slot s = slot(v);
        a.setDuration(Math.max(0, ms));
        a.setInterpolator(ease);
        a.setStartDelay(Math.max(0, delay));
        s.running.put(p, a);
        if (layer) {
            if (s.layers++ == 0 && v.getLayerType() != View.LAYER_TYPE_HARDWARE) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (s.running.get(p) == animation) s.running.remove(p);
                if (layer && --s.layers == 0) v.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        a.start();
        return a;
    }

    static void set(View v, Property<View, Float> p, float value) {
        cancel(v, p);
        p.set(v, value);
    }

    static void cancel(View v, Property<View, Float> p) {
        Object tag = v.getTag(R.id.anims);
        if (!(tag instanceof Slot)) return;
        Animator a = ((Slot) tag).running.remove(p);
        if (a != null) a.cancel();
    }

    static boolean running(View v, Property<View, Float> p) {
        Object tag = v.getTag(R.id.anims);
        return tag instanceof Slot && ((Slot) tag).running.containsKey(p);
    }

    static void cancelAll(View v) {
        Object tag = v.getTag(R.id.anims);
        if (!(tag instanceof Slot)) return;
        Animator[] all = ((Slot) tag).running.values().toArray(new Animator[0]);
        ((Slot) tag).running.clear();
        for (Animator a : all) a.cancel();
    }

    /**
     * Edge feedback: 10 px toward the pressed direction and back, 240 ms. It runs on a view whose
     * translation is used for nothing else, so it adds to whatever focus transform sits below it.
     */
    static void bump(View v, int dx, int dy) {
        float[] f = {0f, .35f, 1f};
        if (dx != 0) keys(v, View.TRANSLATION_X, f, new float[]{v.getTranslationX(), dx * 10f, 0f}, 240, Ui.STD, 0);
        if (dy != 0) keys(v, View.TRANSLATION_Y, f, new float[]{v.getTranslationY(), dy * 10f, 0f}, 240, Ui.STD, 0);
    }

    /** Select press: scale s → s − d → s over 200 ms. */
    static void press(View v, float s, float d) {
        float[] f = {0f, .5f, 1f};
        float[] vals = {v.getScaleX(), s - d, s};
        keys(v, View.SCALE_X, f, vals, 200, Ui.STD, 0);
        keys(v, View.SCALE_Y, f, vals.clone(), 200, Ui.STD, 0);
    }

    static void scale(View v, float to, long ms, TimeInterpolator ease, long delay) {
        to(v, View.SCALE_X, to, ms, ease, delay);
        to(v, View.SCALE_Y, to, ms, ease, delay);
    }
}
