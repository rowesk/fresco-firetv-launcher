package io.github.rowesk.fresco;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Fresco's home screen: a photograph of the time of day, a small glass dock with one to four apps
 * (Apps.dock; Stremio and YouTube on a fresh install that has them), the clock and the settings gear.
 * Down dips the sheet to Continue watching, when Stremio is signed in.
 *
 * Every arrival from Home is revealed from black: the service's cover is up, our own black veil
 * draws first, the service drops its cover on that frame, then the reveal runs (~470 ms). When the
 * launcher leaves the screen it drops its bitmaps (the photo, its blur, the posters) and detaches
 * its views, so the process sits at about 15 MB until the next Home.
 */
public class HomeActivity extends Activity implements StremioClient.Listener, SettingsController.Host {
    private static final String TAG = "Fresco";
    /** The Music button sends us back here with this: not a Home press, so no reset and no reveal. */
    static final String EXTRA_RETURN = "io.github.rowesk.fresco.RETURN";
    /**
     * For README screenshots: a wallpaper key to show instead of the scheduled one, e.g.
     * `adb shell am start -n io.github.rowesk.fresco/.HomeActivity --es preview_wallpaper art/everyday/evening-1`.
     * It lasts until the next Home press or time of day. Unknown keys are ignored.
     */
    static final String EXTRA_PREVIEW_WALLPAPER = "preview_wallpaper";

    /**
     * What "Fire TV settings" opens. On Fire OS the standard settings action resolves to Amazon's own
     * settings app. If not, set an explicit component here, e.g. "com.amazon.tv.settings.v2/.tv.MainSettingsActivity"
     * (check with: adb shell cmd package resolve-activity -a android.settings.SETTINGS).
     */
    static final String FIRE_TV_SETTINGS_ACTION = Settings.ACTION_SETTINGS;
    static final String FIRE_TV_SETTINGS_COMPONENT = null;

    private static final int HOME = 0, AMBIENT = 1, SETTINGS = 2;
    private static final int DOCK = 0, GEAR = 1, ROW = 2;
    private static final int DIP = 400;
    /** Dock cells: 208 wide with an 8 px gap, so each app sits 216 px along; 12 px padding around them. */
    private static final int CELL = 208, PITCH = 216, PAD = 12, DOCK_H = 232;
    /** Room around the dock and gear for their shadows, inside the views so group fades keep them. */
    private static final int DM = 72, GM = 40;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private StremioClient stremio;
    private Wallpapers wallpapers;
    private Wallpapers.Frame frame;

    private FrameLayout content, stage, plane, dock, dockBump, gearWrap, gearBump;
    private BitmapView wall, wallBlur, wallBlurNext;
    private ScrimView scrimTop, scrimBottom, scrimRow;
    private ClockView clock;
    private GlassView dockGlass, droplet, gearGlass;
    private ImageView gearIcon, peekChevron;
    private Cell[] cells = new Cell[0];
    private List<String> dockApps = new ArrayList<>();
    private LinearLayout peek;
    private TextView peekText;
    private ContinueRow row;
    private SettingsController settings;
    private View veil;

    private int mode = HOME, zone = DOCK, app;
    private boolean started, resumed, attached, bound, arrived, homeArrival = true, rowDataPending, changing;
    /** From the start of a wallpaper change until its sharp photo is up: the screen must stay on the blur. */
    private boolean awaitingPhoto;
    private long lastMove;
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            onMinute();
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.initFonts(this);
        prefs = Prefs.get(this);
        stremio = StremioClient.get(this);
        wallpapers = Wallpapers.get(this);
        String preview = getIntent().getStringExtra(EXTRA_PREVIEW_WALLPAPER);
        if (preview != null) wallpapers.preview(preview);
        build();
        setContentView(content);
        stremio.setListener(this);
        applyRowData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean ret = intent.getBooleanExtra(EXTRA_RETURN, false);
        String preview = intent.getStringExtra(EXTRA_PREVIEW_WALLPAPER);
        boolean newPicture = preview != null ? wallpapers.preview(preview) : !ret && wallpapers.endPreview();
        // Still decoding for onStart: retarget that load (its callback binds whatever arrives).
        if (newPicture && started && !bound) wallpapers.ensureCurrent(null);
        if (!ret) {
            homeArrival = true;
            if (arrived) arrive();
        }
        // Already showing a picture: move to the new one with the usual change.
        if (newPicture && bound) checkWallpaper();
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        wallpapers.setInUse(true);
        if (!attached) {
            content.addView(stage, new FrameLayout.LayoutParams(1920, 1080));
            attached = true;
        }
        Anim.set(veil, View.ALPHA, 1);
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        clock.setTime(nowString(), false);

        // Our first frame is black, the same as the service's cover, so the cover can go on it.
        final ViewTreeObserver.OnDrawListener[] first = new ViewTreeObserver.OnDrawListener[1];
        first[0] = () -> main.post(() -> {
            if (first[0] == null) return;
            stage.getViewTreeObserver().removeOnDrawListener(first[0]);
            first[0] = null;
            FrescoService s = FrescoService.get();
            if (s != null) s.launcherDrawn();
        });
        stage.getViewTreeObserver().addOnDrawListener(first[0]);

        wallpapers.ensureCurrent(f2 -> {
            if (!started) return;
            bind(f2);
            maybeArrive();
        });
        // After the decode has started. A docked app may have been uninstalled while we were away.
        List<String> d = Apps.dock(this);
        if (!d.equals(dockApps)) setDock(d);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        maybeArrive();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    /**
     * A stopped singleTask activity gets onNewIntent after onStart, so whether this is a Home press is
     * only known by onResume. Arrive once both that and the bitmaps are in.
     */
    private void maybeArrive() {
        if (!resumed || !bound || arrived) return;
        arrived = true;
        arrive();
    }

    @Override
    protected void onStop() {
        started = false;
        bound = false;
        arrived = false;
        changing = false;
        awaitingPhoto = false;
        try {
            unregisterReceiver(timeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        row.stopBreathing();
        // Detaching discards every display list, so nothing still holds the bitmaps we drop next.
        if (attached) {
            content.removeView(stage);
            attached = false;
        }
        wall.setBitmap(null);
        wallBlur.setBitmap(null);
        wallBlurNext.setBitmap(null);
        GlassView.setBlur(null);
        row.releasePosters();
        settings.releaseImages();
        frame = null;
        wallpapers.setInUse(false);
        // Home pressed while we were in front: Amazon home is up under the cover and will click our
        // tile in a moment, so keep the decoded photo for that instead of decoding it again.
        FrescoService s = FrescoService.get();
        if (s != null && s.homeInFlight()) wallpapers.holdBriefly();
        else wallpapers.release();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stremio.setListener(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Back never falls through to Amazon home.
    }

    private void bind(Wallpapers.Frame f) {
        frame = f;
        bound = true;
        wall.setBitmap(f.sharp);
        wallBlur.setBitmap(f.blur);
        GlassView.setBlur(f.blur);
        GlassView.setLight(f.period);
        applyTuning(f.tuning);
        if (rowDataPending && zone != ROW) applyRowData();
        else row.loadPosters(stremio);
        settings.restoreImages();
    }

    /** After the bitmaps are bound: a Home press resets and reveals; a return just lifts the veil. */
    private void arrive() {
        if (homeArrival) {
            homeArrival = false;
            stremio.refresh(false);
            reveal();
        } else {
            Anim.to(veil, View.ALPHA, 0, 380, Ui.DEVELOP, 0);
            if (settings.isOpen()) settings.refreshAccount();
        }
    }

    private void applyTuning(Wallpapers.Tuning t) {
        scrimTop.setPeak(t.scrimTop);
        scrimBottom.setPeak(t.scrimBottom);
        scrimRow.setPeak(t.scrimRow);
        dockGlass.setTint(t.dock, t.frost);
        droplet.setMilk(t.milk, 1);
        gearGlass.setTint(t.gear, t.frost);
        gearGlass.milk = t.milkGear;
        gearGlass.invalidate();
        row.setSkeletonTint(t.dock * .8f, t.frost);
        settings.applyTuning(t);
    }

    // ------------------------------------------------------------------ building the stage

    private void build() {
        content = new FrameLayout(this);
        content.setBackgroundColor(0xFF000000);
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        stage.setBackgroundColor(0xFF000000);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        float k = size.x / 1920f;
        if (Math.abs(k - 1) > .001f) {
            stage.setPivotX(0);
            stage.setPivotY(0);
            stage.setScaleX(k);
            stage.setScaleY(k);
        }
        GlassView.setStage(stage);
        stage.getViewTreeObserver().addOnPreDrawListener(() -> {
            GlassView.syncAll();
            return true;
        });

        wall = Ui.add(stage, new BitmapView(this), 0, 0, 1920, 1080);
        wallBlur = Ui.add(stage, new BitmapView(this), 0, 0, 1920, 1080);
        wallBlur.setAlpha(0);
        wallBlurNext = Ui.add(stage, new BitmapView(this), 0, 0, 1920, 1080);
        wallBlurNext.setAlpha(0);
        scrimTop = Ui.add(stage, new ScrimView(this, ScrimView.TOP), 1920 - 980, 0, 980, 460);
        scrimBottom = Ui.add(stage, new ScrimView(this, ScrimView.BOTTOM), 0, 1080 - 475, 1920, 475);
        scrimRow = Ui.add(stage, new ScrimView(this, ScrimView.ROW), 0, 1080 - 756, 1920, 756);
        scrimRow.setAlpha(0);

        clock = Ui.add(stage, new ClockView(this), 1688 - 320, 84, 320, 72);

        gearWrap = Ui.add(stage, new FrameLayout(this), 1720 - GM, 80 - GM, 80 + 2 * GM, 80 + 2 * GM);
        gearWrap.setClipChildren(false);
        gearBump = Ui.add(gearWrap, new FrameLayout(this), 0, 0, 80 + 2 * GM, 80 + 2 * GM);
        gearBump.setClipChildren(false);
        gearGlass = Ui.add(gearBump, new GlassView(this, 40), GM, GM, 80, 80);
        gearGlass.mag = 1.06f;
        gearGlass.setShadow(10, 28, .22f);
        gearGlass.setMilk(.6f, 0);
        gearIcon = Ui.add(gearBump, new ImageView(this), GM + 22, GM + 22, 36, 36);
        gearIcon.setImageResource(R.drawable.ic_gear);
        gearIcon.setColorFilter(Ui.INK);

        plane = Ui.add(stage, new FrameLayout(this) {
            @Override
            public boolean hasOverlappingRendering() {
                // A group fade would need a full-screen offscreen layer (8 MB); its parts fade individually.
                return false;
            }
        }, 0, 0, 1920, 1080);
        plane.setClipChildren(false);

        // Sized to the apps by setDock: it grows to the right from x = 120.
        dock = Ui.add(plane, new FrameLayout(this), 120 - DM, 712 - DM, 0, DOCK_H + 2 * DM);
        dock.setClipChildren(false);
        dock.setPivotX(DM);
        dock.setPivotY(DM + DOCK_H);
        dockBump = Ui.add(dock, new FrameLayout(this), 0, 0, 0, DOCK_H + 2 * DM);
        dockBump.setClipChildren(false);
        dockGlass = Ui.add(dockBump, new GlassView(this, 64), DM, DM, 0, DOCK_H);
        dockGlass.mag = 1.05f;
        dockGlass.lensMag = 1.16f;
        dockGlass.setShadow(18, 48, .26f, 2, 6, .10f);
        droplet = Ui.add(dockBump, new GlassView(this, 52), DM + PAD, DM + PAD, CELL, CELL);
        droplet.mag = 1.10f;
        droplet.glint = true;
        droplet.setShadow(10, 26, .20f);

        peek = Ui.add(plane, new LinearLayout(this), 120, 968, FrameLayout.LayoutParams.WRAP_CONTENT, 32);
        peek.setOrientation(LinearLayout.HORIZONTAL);
        peek.setGravity(android.view.Gravity.CENTER_VERTICAL);
        peekChevron = new ImageView(this);
        peekChevron.setImageResource(R.drawable.ic_chev_down);
        peekChevron.setColorFilter(Ui.white(.86f));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(24, 24);
        clp.leftMargin = 4;
        clp.rightMargin = 10;
        peek.addView(peekChevron, clp);
        peekText = Ui.text(this, Ui.text500, 24, Ui.white(.86f));
        peekText.setShadowLayer(14, 0, 0, 0x66000000);
        peek.addView(peekText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 32));

        row = Ui.add(plane, new ContinueRow(this), 0, 1000, 1920, 420);
        row.setAlpha(0);

        FrameLayout settingsRoot = Ui.add(stage, new FrameLayout(this) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        }, 0, 0, 1920, 1080);
        settingsRoot.setClipChildren(false);
        settings = new SettingsController(this, this, settingsRoot);

        veil = Ui.add(stage, new View(this), 0, 0, 1920, 1080);
        veil.setBackgroundColor(0xFF000000);

        for (View v : new View[]{content, stage}) v.setFocusable(false);
        setDock(Apps.dock(this));
    }

    /**
     * Fills the dock with these apps, in order, and sizes it to them: 448 px for two (DESIGN.md), plus
     * 216 for each more. Focus goes to the last-launched app if it's still there.
     */
    private void setDock(List<String> pkgs) {
        dockApps = new ArrayList<>(pkgs);
        for (Cell c : cells) dockBump.removeView(c);
        cells = new Cell[dockApps.size()];
        for (int i = 0; i < cells.length; i++) {
            String pkg = dockApps.get(i);
            Bitmap[] marks = Apps.marks(this, pkg);
            cells[i] = Ui.add(dockBump, new Cell(this, marks[0], marks[1], Apps.label(this, pkg)),
                    DM + PAD + PITCH * i, DM + PAD, CELL, CELL);
        }
        int w = 2 * PAD + PITCH * Math.max(1, cells.length) - (PITCH - CELL);
        resize(dock, w + 2 * DM);
        resize(dockBump, w + 2 * DM);
        resize(dockGlass, w);
        // The dock's glass is solved over what it covers, so re-solve at the new width.
        Wallpapers.Tuning.dockWidth = w;
        if (frame != null && frame.tuning != null) {
            frame.tuning.solveDock();
            applyTuning(frame.tuning);
        }
        app = Math.max(0, dockApps.indexOf(prefs.lastApp()));
        dockFocus(app, false, app);
    }

    private static void resize(View v, int w) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        lp.width = w;
        v.setLayoutParams(lp);
    }

    // ------------------------------------------------------------------ keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
                return super.dispatchKeyEvent(e);
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean repeat = e.getRepeatCount() > 0;
        if (!bound) return true;
        if (mode == AMBIENT) {
            // Any key restores home and is used for nothing else.
            if (!repeat) exitAmbient();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (mode == HOME && !repeat) enterAmbient();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            // Rewind's partner: the bedtime tint switch, on at the chosen strength right now.
            FrescoService s = FrescoService.get();
            if (mode == HOME && !repeat && s != null) s.toggleTint();
            return true;
        }
        int k = map(code);
        if (k == 0) return true;
        if (mode == SETTINGS) {
            if (!settings.onKey(k, repeat)) closeSettings();
            return true;
        }
        homeKey(k, repeat);
        return true;
    }

    private static int map(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return SettingsController.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return SettingsController.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return SettingsController.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return SettingsController.RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return SettingsController.SELECT;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                return SettingsController.BACK;
        }
        return 0;
    }

    private void homeKey(int k, boolean repeat) {
        if (zone == DOCK) {
            switch (k) {
                case SettingsController.LEFT:
                    if (app > 0) dockFocus(app - 1, true, app);
                    else Anim.bump(dockBump, -1, 0);
                    break;
                case SettingsController.RIGHT:
                    if (app < cells.length - 1) dockFocus(app + 1, true, app);
                    else Anim.bump(dockBump, 1, 0);
                    break;
                case SettingsController.UP:
                    zone = GEAR;
                    dockFocus(app, true, app);
                    gearFocus(true, true);
                    break;
                case SettingsController.DOWN:
                    if (row.canOpen()) openRow(true);
                    else Anim.bump(dockBump, 0, 1);
                    break;
                case SettingsController.SELECT:
                    if (repeat || cells.length == 0) break;
                    Anim.press(cells[app], 1.04f, .09f);
                    final int which = app;
                    main.postDelayed(() -> launchApp(which), 110);
                    break;
            }
        } else if (zone == GEAR) {
            switch (k) {
                case SettingsController.DOWN:
                    zone = DOCK;
                    gearFocus(false, true);
                    dockFocus(app, true, app);
                    break;
                case SettingsController.SELECT:
                    if (!repeat) gearSelect();
                    break;
                case SettingsController.LEFT: Anim.bump(gearBump, -1, 0); break;
                case SettingsController.RIGHT: Anim.bump(gearBump, 1, 0); break;
                case SettingsController.UP: Anim.bump(gearBump, 0, -1); break;
            }
        } else {
            int n = row.state() == StremioClient.State.READY ? row.count() : 0;
            long now = System.currentTimeMillis();
            // Held D-pad: one step per 120 ms at most, each a short retargeting move (never a queue).
            if ((k == SettingsController.LEFT || k == SettingsController.RIGHT)) {
                if (repeat && now - lastMove < 120) return;
                lastMove = now;
            }
            switch (k) {
                case SettingsController.LEFT:
                    if (row.index() > 0) row.focus(row.index() - 1, true, true, repeat);
                    else if (!repeat) row.bump(-1, 0);
                    break;
                case SettingsController.RIGHT:
                    if (row.index() < n - 1) row.focus(row.index() + 1, true, true, repeat);
                    else if (!repeat) row.bump(1, 0);
                    break;
                case SettingsController.UP:
                case SettingsController.BACK:
                    closeRow(true);
                    break;
                case SettingsController.DOWN:
                    row.bump(0, 1);
                    break;
                case SettingsController.SELECT:
                    if (repeat || n == 0) break;
                    row.press();
                    final StremioClient.Item it = row.selected();
                    main.postDelayed(() -> openItem(it), 110);
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ dock, droplet, gear

    private void dockFocus(int i, boolean animate, int prev) {
        app = i;
        boolean focused = zone == DOCK && mode == HOME;
        float x = PITCH * i;
        if (!animate) {
            Anim.set(droplet, View.TRANSLATION_X, x);
            Anim.set(droplet, View.SCALE_X, focused ? 1 : .9f);
            Anim.set(droplet, View.SCALE_Y, focused ? 1 : .9f);
            Anim.set(droplet, View.ALPHA, focused ? 1 : 0);
        } else if (focused && prev != i && droplet.getAlpha() > 0) {
            // The droplet flows: it overshoots its travel a little, stretching, then settles.
            float x0 = droplet.getTranslationX();
            float[] f = {0, .42f, 1};
            Anim.keys(droplet, View.TRANSLATION_X, f, new float[]{x0, x0 + (x - x0) * .55f, x}, 320, Ui.DROP, 0);
            Anim.keys(droplet, View.SCALE_X, f, new float[]{1, 1.13f, 1}, 320, Ui.DROP, 0);
            Anim.keys(droplet, View.SCALE_Y, f, new float[]{1, .96f, 1}, 320, Ui.DROP, 0);
        } else {
            long ms = focused ? 260 : 180;
            android.animation.TimeInterpolator ease = focused ? Ui.OUT : Ui.IN;
            Anim.to(droplet, View.TRANSLATION_X, x, ms, ease, 0);
            Anim.scale(droplet, focused ? 1 : .9f, ms, ease, 0);
            Anim.to(droplet, View.ALPHA, focused ? 1 : 0, ms, ease, 0);
        }
        for (int j = 0; j < cells.length; j++) cells[j].focus(focused && j == i, animate);
    }

    private void gearFocus(boolean on, boolean animate) {
        if (animate) {
            Anim.scale(gearWrap, on ? 1.12f : 1, 240, Ui.STD, 0);
            Anim.to(gearGlass, GlassView.MILK, on ? 1 : 0, 200, Ui.STD, 0);
        } else {
            Anim.set(gearWrap, View.SCALE_X, on ? 1.12f : 1);
            Anim.set(gearWrap, View.SCALE_Y, on ? 1.12f : 1);
            Anim.set(gearGlass, GlassView.MILK, on ? 1 : 0);
        }
        gearIcon.setColorFilter(on ? Ui.INK_DARK : Ui.INK);
    }

    /** A quick dip, and the cog turns a sixth of a turn (it has six teeth) as settings open. */
    private void gearSelect() {
        float[] f = {0, .5f, 1};
        Anim.keys(gearWrap, View.SCALE_X, f, new float[]{1.12f, 1.02f, 1.12f}, 200, Ui.STD, 0);
        Anim.keys(gearWrap, View.SCALE_Y, f, new float[]{1.12f, 1.02f, 1.12f}, 200, Ui.STD, 0);
        Anim.fromTo(gearIcon, View.ROTATION, 0, 60, 420, Ui.OUT, 0);
        main.postDelayed(this::openSettings, 110);
    }

    // ------------------------------------------------------------------ the row

    private void openRow(boolean animate) {
        zone = ROW;
        go(plane, View.TRANSLATION_Y, -DIP, 460, Ui.STD, 0, animate);
        if (animate) Anim.alphaWithLayer(dock, 0, 300, Ui.IN, 0);
        else Anim.set(dock, View.ALPHA, 0);
        go(dock, View.SCALE_X, .94f, 300, Ui.IN, 0, animate);
        go(dock, View.SCALE_Y, .94f, 300, Ui.IN, 0, animate);
        go(peek, View.ALPHA, 0, 140, Ui.IN, 0, animate);
        go(row, View.ALPHA, 1, 260, Ui.OUT, 80, animate);
        go(scrimRow, View.ALPHA, 1, 460, Ui.STD, 0, animate);
        go(scrimBottom, View.ALPHA, 0, 300, Ui.STD, 0, animate);
        dockFocus(app, animate, app);
        if (animate) row.surface();
        row.focus(row.index(), true, animate, false);
    }

    private void closeRow(boolean animate) {
        zone = DOCK;
        go(plane, View.TRANSLATION_Y, 0, 400, Ui.STD, 0, animate);
        if (animate) Anim.alphaWithLayer(dock, 1, 380, Ui.STD, 0);
        else Anim.set(dock, View.ALPHA, 1);
        go(dock, View.SCALE_X, 1, 380, Ui.STD, 0, animate);
        go(dock, View.SCALE_Y, 1, 380, Ui.STD, 0, animate);
        go(peek, View.ALPHA, 1, 260, Ui.OUT, 160, animate);
        go(row, View.ALPHA, 0, 200, Ui.IN, 0, animate);
        go(scrimRow, View.ALPHA, 0, 380, Ui.STD, 0, animate);
        go(scrimBottom, View.ALPHA, 1, 380, Ui.STD, 0, animate);
        row.stopBreathing();
        row.focus(row.index(), false, animate, false);
        dockFocus(app, animate, app);
        if (rowDataPending) main.postDelayed(() -> {
            if (zone != ROW) applyRowData();
        }, 420);
    }

    private static void go(View v, android.util.Property<View, Float> p, float to, long ms,
                           android.animation.TimeInterpolator ease, long delay, boolean animate) {
        if (animate) Anim.to(v, p, to, ms, ease, delay);
        else Anim.set(v, p, to);
    }

    @Override
    public void onStremioChanged() {
        if (settings.isOpen()) settings.refreshAccount();
        if (zone == ROW || !bound) {
            rowDataPending = true;
            if (!bound) applyRowData();
            return;
        }
        applyRowData();
    }

    private void applyRowData() {
        rowDataPending = false;
        Wallpapers.Tuning t = frame != null ? frame.tuning : Wallpapers.Tuning.DEFAULT;
        StremioClient.State s = stremio.state();
        java.util.List<StremioClient.Item> items = stremio.items();
        // Rebuild only when something visible changed, so a background sync never flickers the row.
        StringBuilder sig = new StringBuilder(s.name());
        for (StremioClient.Item it : items) sig.append('|').append(it.id).append(it.videoId).append(it.timeOffset);
        if (!sig.toString().equals(rowSignature)) {
            rowSignature = sig.toString();
            row.setData(s, items, t.dock * .8f, t.frost);
            row.focus(0, false, false, false);
            updatePeek(s);
        }
        // New posters may have arrived with the sync.
        if (bound) row.loadPosters(stremio);
    }

    private String rowSignature;

    private void updatePeek(StremioClient.State s) {
        peek.setVisibility(s == StremioClient.State.NONE ? View.INVISIBLE : View.VISIBLE);
        peekChevron.setVisibility(s == StremioClient.State.SIGNED_OUT ? View.GONE : View.VISIBLE);
        if (s == StremioClient.State.READY) {
            StremioClient.Item it = stremio.items().get(0);
            SpannableStringBuilder b = new SpannableStringBuilder(it.name);
            b.setSpan(new FontSpan(Ui.text600), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(Ui.INK), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            String ep = it.episode();
            if (!ep.isEmpty()) b.append(" · ").append(ep);
            peekText.setTypeface(Ui.text500);
            peekText.setTextColor(Ui.white(.86f));
            peekText.setText(b);
        } else if (s == StremioClient.State.LOADING) {
            peekText.setTypeface(Ui.text500);
            peekText.setTextColor(Ui.white(.86f));
            peekText.setText("Continue watching");
        } else if (s == StremioClient.State.SIGNED_OUT) {
            peekText.setTypeface(Ui.text400);
            peekText.setTextColor(Ui.white(.82f));
            peekText.setText("Sign in to Stremio from a computer to see what you were watching");
        }
        if (zone == ROW && !row.canOpen()) closeRow(true);
    }

    // ------------------------------------------------------------------ ambient

    private void enterAmbient() {
        mode = AMBIENT;
        Anim.to(plane, View.ALPHA, 0, 380, Ui.AMBIENT, 0);
        Anim.to(plane, View.TRANSLATION_Y, (zone == ROW ? -DIP : 0) + 24, 380, Ui.AMBIENT, 0);
        Anim.alphaWithLayer(gearWrap, 0, 260, Ui.IN, 0);
        Anim.scale(gearWrap, .9f, 260, Ui.IN, 0);
        Anim.to(scrimBottom, View.ALPHA, 0, 600, Ui.STD, 0);
        Anim.to(scrimRow, View.ALPHA, 0, 600, Ui.STD, 0);
        // The clock glides into the corner the gear left, so the screen reads as finished.
        Anim.to(clock, View.TRANSLATION_X, 112, 720, Ui.OUT, 140);
    }

    private void exitAmbient() {
        mode = HOME;
        Anim.to(clock, View.TRANSLATION_X, 0, 360, Ui.STD, 0);
        Anim.to(plane, View.ALPHA, 1, 380, Ui.OUT, 60);
        Anim.to(plane, View.TRANSLATION_Y, zone == ROW ? -DIP : 0, 380, Ui.OUT, 60);
        Anim.alphaWithLayer(gearWrap, 1, 300, Ui.OUT, 80);
        Anim.scale(gearWrap, zone == GEAR ? 1.12f : 1, 300, Ui.OUT, 80);
        Anim.to(zone == ROW ? scrimRow : scrimBottom, View.ALPHA, 1, 380, Ui.STD, 0);
    }

    // ------------------------------------------------------------------ reveal

    private void resetHome() {
        mode = HOME;
        zone = DOCK;
        app = Math.max(0, dockApps.indexOf(prefs.lastApp()));
        if (settings.isOpen()) settings.closeNow();
        Anim.set(plane, View.TRANSLATION_Y, 0);
        Anim.set(plane, View.ALPHA, 1);
        Anim.set(dock, View.ALPHA, 1);
        Anim.set(dock, View.SCALE_X, 1);
        Anim.set(dock, View.SCALE_Y, 1);
        Anim.set(dock, View.TRANSLATION_Y, 0);
        dock.setLayerType(View.LAYER_TYPE_NONE, null);
        Anim.set(row, View.ALPHA, 0);
        Anim.set(peek, View.ALPHA, 1);
        Anim.set(peek, View.TRANSLATION_Y, 0);
        Anim.set(scrimRow, View.ALPHA, 0);
        Anim.set(scrimBottom, View.ALPHA, 1);
        Anim.set(scrimTop, View.ALPHA, 1);
        Anim.set(clock, View.TRANSLATION_X, 0);
        Anim.set(clock, View.ALPHA, 1);
        Anim.set(gearWrap, View.SCALE_X, 1);
        Anim.set(gearWrap, View.SCALE_Y, 1);
        Anim.set(gearWrap, View.ALPHA, 1);
        gearWrap.setLayerType(View.LAYER_TYPE_NONE, null);
        Anim.set(gearIcon, View.ROTATION, 0);
        Anim.set(wallBlur, View.ALPHA, 0);
        gearFocus(false, false);
        if (rowDataPending) applyRowData();
        row.stopBreathing();
        row.focus(0, false, false, false);
        dockFocus(app, false, app);
    }

    private void reveal() {
        resetHome();
        Anim.fromTo(veil, View.ALPHA, 1, 0, 380, Ui.DEVELOP, 0);
        Anim.fromTo(wall, View.SCALE_X, 1.04f, 1, 480, Ui.OUT, 0);
        Anim.fromTo(wall, View.SCALE_Y, 1.04f, 1, 480, Ui.OUT, 0);
        Anim.fromTo(clock, View.ALPHA, 0, 1, 260, Ui.OUT, 60);
        Anim.fromTo(gearWrap, View.SCALE_X, .9f, 1, 300, Ui.OUT, 90);
        Anim.fromTo(gearWrap, View.SCALE_Y, .9f, 1, 300, Ui.OUT, 90);
        Anim.set(gearWrap, View.ALPHA, 0);
        Anim.alphaWithLayer(gearWrap, 1, 300, Ui.OUT, 90);
        Anim.set(dock, View.ALPHA, 0);
        Anim.alphaWithLayer(dock, 1, 340, Ui.OUT, 100);
        Anim.fromTo(dock, View.TRANSLATION_Y, 22, 0, 340, Ui.OUT, 100);
        Anim.fromTo(peek, View.ALPHA, 0, 1, 300, Ui.OUT, 160);
        Anim.fromTo(peek, View.TRANSLATION_Y, 12, 0, 300, Ui.OUT, 160);
        Anim.fromTo(droplet, View.ALPHA, 0, 1, 280, Ui.OUT, 190);
        Anim.fromTo(droplet, View.SCALE_X, .9f, 1, 280, Ui.OUT, 190);
        Anim.fromTo(droplet, View.SCALE_Y, .9f, 1, 280, Ui.OUT, 190);
    }

    // ------------------------------------------------------------------ settings

    private void openSettings() {
        if (mode != HOME) return;
        mode = SETTINGS;
        Anim.to(wallBlur, View.ALPHA, 1, 340, Ui.STD, 0);
        Anim.to(plane, View.ALPHA, 0, 220, Ui.IN, 0);
        Anim.to(clock, View.ALPHA, 0, 200, Ui.IN, 0);
        Anim.alphaWithLayer(gearWrap, 0, 200, Ui.IN, 0);
        Anim.to(scrimTop, View.ALPHA, 0, 300, Ui.STD, 0);
        Anim.to(scrimBottom, View.ALPHA, 0, 300, Ui.STD, 0);
        settings.open();
    }

    private void closeSettings() {
        settings.close();
        mode = HOME;
        zone = GEAR;
        // Closed in the middle of a wallpaper change (a style was just picked): the new photo isn't up
        // yet, so stay on the blur; the change refocuses itself when the photo arrives.
        if (!awaitingPhoto) Anim.to(wallBlur, View.ALPHA, 0, 360, Ui.STD, 60);
        Anim.to(plane, View.ALPHA, 1, 320, Ui.OUT, 120);
        Anim.to(clock, View.ALPHA, 1, 300, Ui.OUT, 120);
        Anim.alphaWithLayer(gearWrap, 1, 300, Ui.OUT, 120);
        Anim.to(scrimTop, View.ALPHA, 1, 300, Ui.STD, 60);
        Anim.to(scrimBottom, View.ALPHA, 1, 300, Ui.STD, 60);
        Anim.set(gearIcon, View.ROTATION, 0);
        gearFocus(true, true);
    }

    @Override
    public void previewTint(boolean on, int strength) {
        FrescoService s = FrescoService.get();
        if (s != null) s.previewTint(on, strength);
    }

    @Override
    public void tintSettingsChanged() {
        // The service listens to the settings file itself.
    }

    @Override
    public void openFireTvSettings() {
        Intent i = new Intent(FIRE_TV_SETTINGS_ACTION);
        if (FIRE_TV_SETTINGS_COMPONENT != null) i.setComponent(ComponentName.unflattenFromString(FIRE_TV_SETTINGS_COMPONENT));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "could not open Fire TV settings", e);
        }
    }

    @Override
    public void wallpaperStyleChanged() {
        checkWallpaper();
    }

    @Override
    public void dockChanged() {
        setDock(Apps.dock(this));
    }

    @Override
    public void syncNow() {
        stremio.refresh(true);
    }

    @Override
    public int nowMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    // ------------------------------------------------------------------ launching

    private void launchApp(int which) {
        if (which >= dockApps.size()) return;
        String pkg = dockApps.get(which);
        prefs.setLastApp(pkg);
        Intent i = Apps.launchIntent(this, pkg);
        try {
            if (i == null) throw new IllegalStateException("not installed");
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "could not launch " + pkg, e);
        }
    }

    /** Opens the exact episode; then the service clicks its first real source (one-press play). */
    private void openItem(StremioClient.Item it) {
        if (it == null) return;
        prefs.setLastApp(FrescoService.STREMIO.getPackageName());
        Intent i = new Intent(Intent.ACTION_VIEW, it.link()).setPackage(FrescoService.STREMIO.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.i(TAG, "stremio: opening " + it.link());
        try {
            startActivity(i);
            FrescoService s = FrescoService.get();
            if (s != null) s.expectStremioSources();
        } catch (Exception e) {
            Log.e(TAG, "could not open " + it.link(), e);
        }
    }

    // ------------------------------------------------------------------ the minute

    private static String nowString() {
        Calendar c = Calendar.getInstance();
        return Ui.two(c.get(Calendar.HOUR_OF_DAY)) + ":" + Ui.two(c.get(Calendar.MINUTE));
    }

    private void onMinute() {
        clock.setTime(nowString(), true);
        checkWallpaper();
    }

    /** The time of day turned or the style changed: move to the new picture if it differs. */
    private void checkWallpaper() {
        if (!bound || frame == null || changing) return;
        String key = wallpapers.currentKey();
        if (!key.equals(frame.key)) changeWallpaper(key);
    }

    /**
     * The time of day or the wallpaper style changed while home is showing: defocus into the blurred copy, swap, refocus
     * (~2.3 s). The two sharp photos never coexist: the old one goes while the screen is blurred,
     * so the peak is one photo plus two 480x270 blurs.
     */
    private void changeWallpaper(final String key) {
        changing = true;
        awaitingPhoto = true;
        Log.i(TAG, "wallpaper: changing to " + key);
        wallpapers.loadBlur(key, nf -> {
            if (!started || !bound) {
                if (nf.blur != null) nf.blur.recycle();
                return;
            }
            // Settings may have closed while the blur decoded; the blur is up already if they were open.
            final boolean blurred = mode == SETTINGS || wallBlur.getAlpha() == 1;
            Runnable crossfade = () -> {
                if (!started || !bound) {
                    if (nf.blur != null) nf.blur.recycle();
                    return;
                }
                wall.setBitmap(null);
                wallpapers.dropSharp();
                wallBlurNext.setBitmap(nf.blur);
                Anim.fromTo(wallBlurNext, View.ALPHA, 0, 1, 700, Ui.STD, 0);
                GlassView.setNext(nf.blur);
                ValueAnimator mix = ValueAnimator.ofFloat(0, 1);
                mix.setDuration(700);
                mix.setInterpolator(Ui.STD);
                mix.addUpdateListener(a -> GlassView.setNextMix((float) a.getAnimatedValue()));
                mix.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        if (!started || !bound) return;
                        wallBlur.setBitmap(nf.blur);
                        Anim.set(wallBlur, View.ALPHA, 1);
                        Anim.set(wallBlurNext, View.ALPHA, 0);
                        wallBlurNext.setBitmap(null);
                        GlassView.setBlur(nf.blur);
                        GlassView.setLight(nf.period);
                        applyTuning(nf.tuning);
                        wallpapers.loadSharp(nf, f2 -> {
                            if (!started || !bound) {
                                if (f2.sharp != null) f2.sharp.recycle();
                                return;
                            }
                            wallpapers.commit(f2);
                            frame = f2;
                            wall.setBitmap(f2.sharp);
                            awaitingPhoto = false;
                            if (mode != SETTINGS) Anim.to(wallBlur, View.ALPHA, 0, 900, Ui.OUT, 120);
                            main.postDelayed(() -> {
                                changing = false;
                                // Another style was picked while this change ran: follow it now.
                                if (started) checkWallpaper();
                            }, 1100);
                        });
                    }
                });
                mix.start();
            };
            if (blurred) {
                crossfade.run();
            } else {
                Anim.to(wallBlur, View.ALPHA, 1, 600, Ui.STD, 0);
                main.postDelayed(crossfade, 620);
            }
        });
    }

    // ------------------------------------------------------------------ small views

    /** An app in the dock: white mark and white label at rest; colour mark and dark label on milk glass (Apps.marks). */
    private static final class Cell extends FrameLayout {
        final ImageView mono, color;
        final TextView labelW, labelK;

        Cell(Context c, Bitmap monoMark, Bitmap colorMark, String name) {
            super(c);
            setClipChildren(false);
            mono = Ui.add(this, new ImageView(c), 56 - Marks.PAD, 34 - Marks.PAD, Marks.SIZE + 2 * Marks.PAD, Marks.SIZE + 2 * Marks.PAD);
            mono.setImageBitmap(monoMark);
            color = Ui.add(this, new ImageView(c), 56 - Marks.PAD, 34 - Marks.PAD, Marks.SIZE + 2 * Marks.PAD, Marks.SIZE + 2 * Marks.PAD);
            color.setImageBitmap(colorMark);
            color.setAlpha(0f);
            labelW = label(c, Ui.INK);
            labelW.setShadowLayer(2.6f, 0, 1, 0x40000000);
            labelW.setAlpha(.64f);
            labelK = label(c, Ui.INK_DARK);
            labelK.setAlpha(0);
            labelW.setText(name);
            labelK.setText(name);
        }

        private TextView label(Context c, int color) {
            TextView t = Ui.text(c, Ui.text500, 28, color);
            t.setGravity(android.view.Gravity.CENTER);
            // A long app name ends in an ellipsis inside the droplet's rounded corners.
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            return Ui.add(this, t, 14, 146, 208 - 28, 36);
        }

        /** Two stacked marks and labels crossfade (alpha only, never a colour animation). */
        void focus(boolean on, boolean animate) {
            go(mono, View.ALPHA, on ? 0 : 1, 200, Ui.STD, 0, animate);
            go(color, View.ALPHA, on ? 1 : 0, 240, Ui.STD, 0, animate);
            go(labelW, View.ALPHA, on ? 0 : .64f, 200, Ui.STD, 0, animate);
            go(labelK, View.ALPHA, on ? 1 : 0, 220, Ui.STD, 0, animate);
            go(this, View.SCALE_X, on ? 1.04f : 1, 260, Ui.STD, 0, animate);
            go(this, View.SCALE_Y, on ? 1.04f : 1, 260, Ui.STD, 0, animate);
        }
    }

    /** Draws one bitmap stretched to its bounds: the photo, or a blurred copy filling the screen. */
    private static final class BitmapView extends View {
        private Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final RectF dst = new RectF();

        BitmapView(Context c) {
            super(c);
        }

        void setBitmap(Bitmap b) {
            bitmap = b;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (bitmap == null || bitmap.isRecycled()) return;
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(bitmap, null, dst, paint);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /** The static scrims: their peak alpha is solved once per wallpaper; only their view alpha animates. */
    private static final class ScrimView extends View {
        static final int TOP = 0, BOTTOM = 1, ROW = 2;
        private final int kind;
        private final Paint paint = new Paint(Paint.DITHER_FLAG);
        private float peak = .2f;

        ScrimView(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        void setPeak(float p) {
            peak = p;
            shade(getWidth(), getHeight());
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            shade(w, h);
        }

        private int black(float a) {
            return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24;
        }

        private void shade(int w, int h) {
            if (w == 0) return;
            if (kind == TOP) {
                // Radial, 980 x 460 at the top-right corner: peak, 0.45 × at 42 %, clear at 72 %.
                RadialGradient g = new RadialGradient(0, 0, 1, new int[]{black(peak), black(peak * .45f), 0, 0},
                        new float[]{0, .42f, .72f, 1}, Shader.TileMode.CLAMP);
                Matrix m = new Matrix();
                m.setScale(w, h);
                m.postTranslate(w, 0);
                g.setLocalMatrix(m);
                paint.setShader(g);
            } else if (kind == BOTTOM) {
                // From the bottom: peak, 0.5 × at 18 %, clear at 44 % of the screen.
                paint.setShader(new LinearGradient(0, h, 0, 0, new int[]{black(peak), black(peak * .5f), 0},
                        new float[]{0, 18f / 44f, 1}, Shader.TileMode.CLAMP));
            } else {
                // From the bottom: peak, 0.8 × at 30 %, 0.35 × at 52 %, clear at 70 %.
                paint.setShader(new LinearGradient(0, h, 0, 0, new int[]{black(peak), black(peak * .8f), black(peak * .35f), 0},
                        new float[]{0, 30f / 70f, 52f / 70f, 1}, Shader.TileMode.CLAMP));
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), paint);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /** Sets a typeface on part of a text (TypefaceSpan(Typeface) needs API 28). */
    private static final class FontSpan extends MetricAffectingSpan {
        private final Typeface face;

        FontSpan(Typeface face) {
            this.face = face;
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(face);
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(face);
        }
    }
}
