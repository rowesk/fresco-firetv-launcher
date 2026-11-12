package io.github.rowesk.fresco;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The bedtime tint: a full-screen TYPE_ACCESSIBILITY_OVERLAY over every app. Fire OS has no
 * night light and blocks the SurfaceFlinger colour matrix (SPEC.md), so this is a plain
 * source-over colour layer. Recipe "Dim + amber": #1F0C01 at up to 0.58 × strength.
 *
 * While a copy-protected app is in front the window is removed entirely (they black out their
 * video under any overlay) and it comes back when you leave.
 */
final class TintController implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "Fresco";

    /** "Dim + amber" (default). The alternative "Amber" recipe is 0x4A1E00 at up to 0.55. */
    static final int COLOR = 0x1F0C01;
    static final float MAX_ALPHA = .58f;
    /**
     * Fade the tint with the window's own alpha (applied by SurfaceFlinger) over an opaque RGB_565
     * window: the window draws once and never again, at half the buffer memory of a translucent one.
     * Set false to fall back to a translucent window whose view alpha animates.
     */
    static final boolean WINDOW_ALPHA = true;
    /**
     * The Music button turns the tint on "at full strength": the strength setting's value, the same
     * level the schedule reaches at "Full strength by". The prototype jumps to 100 % instead; set this
     * false for that.
     */
    static final boolean MUSIC_USES_STRENGTH_SETTING = true;

    interface PictureListener { void changed(float alpha); }
    private static PictureListener pictureListener;
    private static float pictureAlpha;
    static float pictureAlpha() { return pictureAlpha; }
    static void setPictureListener(PictureListener listener) {
        pictureListener = listener;
        if (listener != null) listener.changed(pictureAlpha);
    }
    private static void notifyPicture(float alpha) {
        if (pictureAlpha == alpha) return;
        pictureAlpha = alpha;
        if (pictureListener != null) pictureListener.changed(alpha);
    }

    private static final long RAMP_TICK_MS = 1000;
    private static final long IDLE_TICK_MS = 30_000;

    private final Context service;
    private final WindowManager wm;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View tintView;
    private WindowManager.LayoutParams tintLp;
    private float shown;
    private ValueAnimator fade;
    private float fadingTo = -1;
    private String foreground;
    private boolean skipped;
    private float preview = -1;
    private boolean writing;
    private boolean started;
    private ToastWindow toast;

    private final Runnable tick = this::tick;
    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            evaluate(2000, 0);
        }
    };

    TintController(Context service) {
        this.service = service;
        wm = service.getSystemService(WindowManager.class);
        prefs = Prefs.get(service);
    }

    void start() {
        if (started) return;
        started = true;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_SCREEN_ON);
        service.registerReceiver(clock, f);
        prefs.sp.registerOnSharedPreferenceChangeListener(this);
        evaluate(2000, 0);
    }

    void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacksAndMessages(null);
        try {
            service.unregisterReceiver(clock);
        } catch (IllegalArgumentException ignored) {
        }
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this);
        if (fade != null) fade.cancel();
        apply(0);
        if (toast != null) toast.remove();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (writing || key == null || !key.startsWith("tint_") && !Prefs.SKIP_APPS.equals(key)) return;
        if (Prefs.SKIP_APPS.equals(key)) setForeground(foreground);
        evaluate(320, 0);
    }

    /** The app in front changed (activity windows only). */
    void setForeground(String pkg) {
        foreground = pkg;
        boolean s = prefs.isSkipped(pkg);
        if (s == skipped) return;
        skipped = s;
        Log.i(TAG, "tint: " + (s ? "stepping aside for " : "back after ") + pkg);
        if (s && toast != null) toast.remove();
        evaluate(320, 0);
    }

    /** Launcher settings' strength row: shows the real tint full-screen while focused. */
    void preview(boolean on, int strength) {
        preview = on ? MAX_ALPHA * strength / 100f : -1;
        evaluate(320, 0);
    }

    /**
     * The Amazon Music button (and fast-forward on home) flips what you can see. If the flipped state is
     * what the schedule shows right now, the press just drops any override (back to the schedule), so a
     * second press always undoes the first. Otherwise it sets an override that ends at the next morning's
     * off time: on until then, or off for tonight. No toast, and no tint, while a skipped app is in front.
     */
    void toggleFromMusic(String returningTo) {
        Calendar now = Calendar.getInstance();
        boolean visible = level(now, true) > 0;
        boolean scheduled = scheduled(now) > 0;
        boolean on = !visible;
        long off = next(prefs.tintOff()), start = next(prefs.tintStart());
        String sub;
        writing = true;
        if (on == scheduled) {
            prefs.setTintManual(0, 0);
            sub = on ? "Back to your schedule, until " + Ui.hhmm(prefs.tintOff())
                    : prefs.tintEnabled() ? "Back to your schedule, from " + when(start) : "Until you turn it on again";
        } else {
            prefs.setTintManual(on ? 1 : -1, off);
            sub = on ? "Until " + Ui.hhmm(prefs.tintOff()) : "Off for tonight, back " + when(start);
        }
        writing = false;
        Log.i(TAG, "tint: music button turns it " + (on ? "on" : "off") + (on == scheduled ? " (back to schedule)" : ""));
        if (on) evaluate(900, 80);
        else evaluate(600, 0);
        if (!prefs.isSkipped(returningTo)) {
            if (toast == null) toast = new ToastWindow();
            toast.show(on, sub);
        }
    }

    /** What the Music button is overriding right now, for settings, or null when the schedule is in charge. */
    static String overrideNote(Prefs prefs) {
        if (prefs.tintManual() == 0 || System.currentTimeMillis() >= prefs.tintManualUntil()) return null;
        return prefs.tintManual() > 0 ? "Turned on with the button until " + Ui.hhmm(prefs.tintOff()) + "."
                : "Turned off with the button for tonight; back " + when(next(prefs.tintStart())) + ".";
    }

    /** "tonight at 19:15" ("this evening" after midnight), "today at 15:00" or "tomorrow at 19:15". */
    private static String when(long t) {
        Calendar c = Calendar.getInstance(), today = Calendar.getInstance();
        c.setTimeInMillis(t);
        String time = Ui.hhmm(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
        boolean sameDay = c.get(Calendar.YEAR) == today.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        String day = !sameDay ? "tomorrow" : c.get(Calendar.HOUR_OF_DAY) < 17 ? "today"
                : today.get(Calendar.HOUR_OF_DAY) < 12 ? "this evening" : "tonight";
        return day + " at " + time;
    }

    // ------------------------------------------------------------------ the schedule

    /** 0..1 of the chosen strength: the Music button's override while it lasts, otherwise the schedule. */
    private float level(Calendar now, boolean clearExpired) {
        int manual = prefs.tintManual();
        if (manual != 0) {
            if (now.getTimeInMillis() < prefs.tintManualUntil()) {
                if (manual > 0) return MUSIC_USES_STRENGTH_SETTING ? 1 : 100f / prefs.tintStrength();
                return 0;
            }
            if (clearExpired) {
                writing = true;
                prefs.setTintManual(0, 0);
                writing = false;
            }
        }
        return scheduled(now);
    }

    /**
     * The schedule alone, 0..1: linear from start to full, held until off. The evening axis puts times
     * before noon after midnight, so start/full may cross midnight.
     */
    private float scheduled(Calendar now) {
        if (!prefs.tintEnabled()) return 0;
        float m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        float e = m < 720 ? m + 1440 : m;
        float s = ev(prefs.tintStart()), f = Math.max(ev(prefs.tintFull()), s), o = prefs.tintOff() + 1440;
        if (e < s || e >= o) return 0;
        if (e < f) return (e - s) / (f - s);
        return 1;
    }

    private static int ev(int m) {
        return m < 720 ? m + 1440 : m;
    }

    /** The next time the clock shows `minutes`. */
    private static long next(int minutes) {
        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, minutes / 60);
        c.set(Calendar.MINUTE, minutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    private float target() {
        if (skipped) return 0;
        if (preview >= 0) return preview;
        return level(Calendar.getInstance(), true) * MAX_ALPHA * prefs.tintStrength() / 100f;
    }

    private void tick() {
        evaluate(2000, 0);
    }

    /** Moves toward the target: small steps (the ramp) directly, bigger jumps as a fade of `ms`. */
    private void evaluate(long ms, long delay) {
        if (!started) return;
        handler.removeCallbacks(tick);
        float t = target();
        boolean ramping = false;
        if (skipped) {
            // Gone at once: anything drawn over these apps blacks out their video.
            if (fade != null) fade.cancel();
            apply(0);
        } else if (fade != null && fade.isStarted() && Math.abs(fadingTo - t) < .002f) {
            // already heading there
        } else if (Math.abs(t - shown) < .02f && (fade == null || !fade.isStarted())) {
            apply(t);
            ramping = t > 0 && t < MAX_ALPHA * prefs.tintStrength() / 100f - .001f;
        } else {
            fadeTo(t, ms, delay);
        }
        handler.postDelayed(tick, ramping || (fade != null && fade.isStarted()) ? RAMP_TICK_MS : IDLE_TICK_MS);
    }

    private void fadeTo(float t, long ms, long delay) {
        if (fade != null) fade.cancel();
        fadingTo = t;
        fade = ValueAnimator.ofFloat(shown, t);
        fade.setDuration(ms);
        fade.setStartDelay(delay);
        fade.setInterpolator(t < shown && ms >= 2000 ? null : Ui.STD); // the off-time fade is linear, 2 s
        fade.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        fade.start();
    }

    private void apply(float a) {
        shown = a;
        if (a <= .002f) {
            removeTint();
            notifyPicture(0);
            return;
        }
        if (tintView == null) addTint();
        if (tintView == null) { notifyPicture(0); return; }
        notifyPicture(a);
        if (WINDOW_ALPHA) {
            if (Math.abs(tintLp.alpha - a) >= 1 / 512f) {
                tintLp.alpha = a;
                try {
                    wm.updateViewLayout(tintView, tintLp);
                } catch (RuntimeException e) {
                    Log.e(TAG, "tint: update failed", e);
                }
            }
        } else {
            tintView.setAlpha(a);
        }
    }

    private void addTint() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WINDOW_ALPHA ? PixelFormat.RGB_565 : PixelFormat.TRANSLUCENT);
        lp.windowAnimations = 0;
        lp.setTitle("Fresco tint");
        View v = new View(service);
        v.setBackgroundColor(0xFF000000 | COLOR);
        if (WINDOW_ALPHA) lp.alpha = shown;
        else v.setAlpha(shown);
        try {
            wm.addView(v, lp);
            tintView = v;
            tintLp = lp;
            // A newer toast must stay above the tint.
            if (toast != null) toast.raise();
        } catch (RuntimeException e) {
            Log.e(TAG, "tint: could not add overlay", e);
        }
    }

    private void removeTint() {
        if (tintView == null) return;
        try {
            wm.removeView(tintView);
        } catch (RuntimeException e) {
            Log.e(TAG, "tint: could not remove overlay", e);
        }
        tintView = null;
        tintLp = null;
    }

    // ------------------------------------------------------------------ the toast

    /**
     * The confirmation pill, top centre. It lives in its own small translucent overlay window above
     * the tint (the tint's window is opaque and faded by window alpha, so it can't hold the pill).
     */
    private final class ToastWindow {
        private static final int M = 72; // room for the pill's shadow
        private FrameLayout frame;
        private LinearLayout pill;
        private ImageView moon;
        private GradientDrawable disc;
        private TextView t1, t2;
        private final Runnable hide = this::hide;
        private final Runnable remove = this::remove;

        void show(boolean on, String subtitle) {
            if (frame == null) build();
            if (frame.getParent() == null && !add()) return;
            handler.removeCallbacks(hide);
            handler.removeCallbacks(remove);
            disc.setColor(on ? Ui.withAlpha(Ui.AMBER, .14f) : Ui.white(.08f));
            moon.setImageResource(on ? R.drawable.ic_moon : R.drawable.ic_moon_hollow);
            moon.setColorFilter(on ? Ui.AMBER : Ui.withAlpha(0xFFF6EC, .82f));
            t1.setText(on ? "Bedtime tint on" : "Bedtime tint off");
            t2.setText(subtitle);
            pill.post(() -> {
                pill.setPivotX(pill.getWidth() / 2f);
                pill.setPivotY(pill.getHeight() / 2f);
            });
            Anim.fromTo(pill, View.ALPHA, 0, 1, 360, Ui.OUT, 0);
            Anim.fromTo(pill, View.TRANSLATION_Y, -24, 0, 360, Ui.OUT, 0);
            Anim.fromTo(pill, View.SCALE_X, .96f, 1, 360, Ui.OUT, 0);
            Anim.fromTo(pill, View.SCALE_Y, .96f, 1, 360, Ui.OUT, 0);
            Anim.fromTo(moon, View.ROTATION, on ? -40 : 40, 0, 560, Ui.OUT, 40);
            handler.postDelayed(hide, 2160);
        }

        private void hide() {
            Anim.to(pill, View.ALPHA, 0, 240, Ui.IN, 0);
            Anim.to(pill, View.TRANSLATION_Y, -12, 240, Ui.IN, 0);
            handler.postDelayed(remove, 260);
        }

        void remove() {
            handler.removeCallbacks(hide);
            handler.removeCallbacks(remove);
            if (frame != null && frame.getParent() != null) {
                try {
                    wm.removeView(frame);
                } catch (RuntimeException e) {
                    Log.e(TAG, "toast: could not remove", e);
                }
            }
        }

        /** Re-adds the pill above a tint window that was just created; its timers carry on. */
        void raise() {
            if (frame == null || frame.getParent() == null) return;
            try {
                wm.removeViewImmediate(frame);
            } catch (RuntimeException e) {
                return;
            }
            add();
        }

        private boolean add() {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = 80 - M;
            lp.windowAnimations = 0;
            lp.setTitle("Fresco toast");
            try {
                wm.addView(frame, lp);
                return true;
            } catch (RuntimeException e) {
                Log.e(TAG, "toast: could not add", e);
                return false;
            }
        }

        private void build() {
            Ui.initFonts(service);
            frame = new FrameLayout(service);
            frame.setClipChildren(false);
            // The padding is room for the shadow; clipping to it cut the shadow to a square box.
            frame.setClipToPadding(false);
            frame.setPadding(M, M, M, M);
            pill = new LinearLayout(service);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(16, 0, 32, 0);
            pill.setBackground(new PillDrawable());
            frame.addView(pill, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 96));

            FrameLayout discView = new FrameLayout(service);
            disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            discView.setBackground(disc);
            moon = new ImageView(service);
            discView.addView(moon, new FrameLayout.LayoutParams(32, 32, Gravity.CENTER));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(64, 64);
            dlp.rightMargin = 24;
            pill.addView(discView, dlp);

            FrameLayout txt = new FrameLayout(service);
            t1 = Ui.role(service, Ui.TypeRole.ROW_LABEL, 0xFFFFF6EC);
            t2 = Ui.role(service, Ui.TypeRole.BODY, Ui.withAlpha(0xFFECD6, .78f));
            float capT = Ui.capHeight(t1.getPaint()), capB = Ui.capHeight(t2.getPaint());
            float top = (96 - capT - 12 - capB) / 2;
            Ui.baseline(t1, Math.round(top + capT));
            Ui.baseline(t2, Math.round(top + capT + 12 + capB));
            txt.addView(t1, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 96));
            txt.addView(t2, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, 96));
            pill.addView(txt, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 96));
        }
    }

    /** Smoked pill #18110C at 86 %, inner top highlight, faint outline, soft shadow outside it. */
    private static final class PillDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        PillDrawable() {
            shadowPaint.setColor(0xFF000000);
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height(), r = h / 2;
            Path pill = new Path();
            pill.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
            // No clip-out under the pill: Fire OS's renderer cut the whole bounding box out of the shadow,
            // which showed as a square around the pill. At 86 % the pill hides the shadow beneath it anyway.
            Shadow.get((int) w, (int) h, r, new float[]{18, 44, .38f}).draw(c, w, h, shadowPaint);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.withAlpha(0x18110C, .86f));
            c.drawPath(pill, p);
            Path shifted = new Path();
            shifted.addRoundRect(new RectF(0, 1.5f, w, h + 1.5f), r, r, Path.Direction.CW);
            Path crescent = new Path(pill);
            crescent.op(shifted, Path.Op.DIFFERENCE);
            p.setColor(Ui.withAlpha(0xFFECD6, .16f));
            c.drawPath(crescent, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(Ui.withAlpha(0xFFECD6, .08f));
            c.drawRoundRect(.75f, .75f, w - .75f, h - .75f, r - .75f, r - .75f, p);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
