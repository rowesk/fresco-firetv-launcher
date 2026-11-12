package io.github.rowesk.fresco;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Remote Keys, moved into the launcher's process, plus the bedtime tint.
 *
 * The Netflix and Prime Video buttons can each open an app chosen in settings, and the Amazon Music
 * button can toggle the tint (Prefs.BUTTON_*). A button left unchanged is never touched: its app
 * opens as usual. Fire OS handles the app-shortcut buttons before accessibility services see them,
 * so we can't catch, or consume, the key itself. Instead we watch for the screen each button opens
 * and swap it for the chosen app. A button press is invisible to us, whereas opening the same app
 * from a launcher is always preceded by a key we do see (Select), so "app opened with no recent key"
 * means "opened by the remote button". See FIRE-TV-STICK.md for the button table.
 *
 * It also replaces Amazon's home screen. After Home, Android holds back launches from other apps
 * for 5 seconds, except launches made by the app on screen. So when Amazon home appears we click
 * its own tile for Fresco, and Amazon home opens us with no delay. Fire OS blocks apps from
 * using the device's own ADB, which would otherwise be the way around the delay. Meanwhile a black
 * cover hides Amazon home, and the launcher takes it down on its first frame (which is black too).
 *
 * Because this service runs in the launcher's process, Android keeps the process alive, so Home
 * always finds the launcher warm.
 */
public class FrescoService extends AccessibilityService {
    private static final String TAG = "Fresco";

    /** A visible key this recent means the user opened the app on purpose. */
    private static final long USER_KEY_GRACE_MS = 1500;
    /**
     * The same for a match on any screen of an app (Netflix): a cold start can take a few seconds to
     * show its first screen after the Select that opened it, so a recent key counts for longer.
     */
    private static final long APP_KEY_GRACE_MS = 4000;
    /** After redirecting, keep bouncing back to the target if the stock app resurfaces. */
    private static final long BOUNCE_WINDOW_MS = 5000;
    private static final long KILL_DELAY_MS = 3000;
    /** Amazon home can report its window more than once per Home press. */
    private static final long HOME_DEBOUNCE_MS = 1500;
    /**
     * Amazon home's tiles load just after its window appears, so keep looking for ours until the
     * deadline. Each look makes Amazon home walk its views, so polling much faster slows it down.
     */
    private static final long HOME_SEARCH_POLL_MS = 60;
    private static final long HOME_SEARCH_MS = 2000;
    /**
     * After the stick wakes, Amazon home is already in front (no window change for us to see) and its
     * rows take a while to load, so look for longer. Checked 2026-11-09: Home did nothing after a wake.
     */
    private static final long WAKE_CHECK_DELAY_MS = 500;
    private static final long WAKE_SEARCH_MS = 6000;
    /**
     * Normally the launcher takes the cover down on its first frame. If that signal never comes, the
     * cover goes this long after the launcher's window appears (its first frame is black anyway).
     */
    private static final long COVER_REVEAL_DELAY_MS = 150;
    /** The cover always comes down after this, whatever happens. */
    private static final long COVER_MAX_MS = 2500;

    private static final String AMAZON_HOME_PACKAGE = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_CLASS = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    /** Fire OS routes app-button launches through this Appstore screen; it is never "the app you were in". */
    private static final String APPSTORE_LAUNCH_CLASS = "com.amazon.venezia.pdi.AppLaunchActivity";

    static final ComponentName STREMIO =
            new ComponentName("com.stremio.one", "com.stremio.tv.MainActivity");
    static final ComponentName YOUTUBE =
            new ComponentName("com.amazon.firetv.youtube", "dev.cobalt.app.MainActivity");

    static final String NETFLIX_PACKAGE = "com.netflix.ninja";
    static final String PRIME_VIDEO_PACKAGE = "com.amazon.firebat";

    private static final Redirect[] REDIRECTS = {
            // Netflix button, Netflix installed: Netflix itself. Its first screen isn't known, so any of
            // them counts, but only when Netflix wasn't already in front.
            new Redirect(Prefs.BUTTON_NETFLIX, NETFLIX_PACKAGE, null, null),
            // Netflix button, Netflix not installed: Fire OS opens this Appstore screen instead.
            new Redirect(Prefs.BUTTON_NETFLIX, "com.amazon.venezia", APPSTORE_LAUNCH_CLASS, NETFLIX_PACKAGE),
            // Prime Video button: Prime Video's splash screen.
            new Redirect(Prefs.BUTTON_PRIME_VIDEO, PRIME_VIDEO_PACKAGE, "com.amazon.pyrocore.IgnitionActivity", null),
    };

    /**
     * Amazon Music button → toggle the bedtime tint (unless set to unchanged in settings), then put the
     * user back where they were. Matched on package only (plus "no visible key"), because its first
     * window may be a splash screen; the class is logged so it can be pinned down on the device.
     */
    private static final String MUSIC_PACKAGE = "com.amazon.bueller.music";
    static final int RETURN_RELAUNCH = 0, RETURN_BACK = 1;
    /**
     * RETURN_RELAUNCH brings the previous app's task back to the front, as its launcher tile would.
     * RETURN_BACK sends one global Back to Amazon Music instead. Try both on the device.
     */
    private static final int MUSIC_RETURN = RETURN_RELAUNCH;

    /**
     * After we open a Stremio detail link, click its first non-trailer source for up to this long.
     * Each look walks Stremio's view tree over IPC, so it polls gently while the sources load.
     */
    private static final long STREAM_SEARCH_MS = 10_000;
    private static final long STREAM_SEARCH_DELAY_MS = 600;
    private static final long STREAM_SEARCH_POLL_MS = 400;
    /** Source cards read like "1080p · ⚡ Cached · TorBox · Comet". */
    private static final Pattern STREAM_CARD = Pattern.compile(
            "(?i)^\\s*(4k|8k|2160p|1440p|1080p|720p|576p|480p|360p|uhd|fhd|hd|sd)\\b.*", Pattern.DOTALL);
    /** Some addons put their name first ("Torrentio 4k HDR"); a resolution anywhere also counts. */
    private static final Pattern RESOLUTION = Pattern.compile("(?i)\\b(4k|2160p|1440p|1080p|720p|576p|480p)\\b");

    private static volatile FrescoService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideCover = this::hideCover;
    private View cover;
    /** Searches block on another app's UI, so they run here rather than on the thread drawing the cover. */
    private final HandlerThread finderThread = new HandlerThread("tile-finder");
    private Handler finder;
    private final Runnable findTile = this::findTile;
    private final Runnable findStream = this::findStream;
    /** Non-zero while we're looking for our tile on Amazon home. */
    private volatile long homeSearchUntil;
    /** Non-zero while we're looking for a Stremio source card to click. */
    private volatile long streamSearchUntil;
    private long lastHomeRedirectAt;
    private long lastKeyAt;
    private Redirect pending;
    private String pendingTarget;
    private long pendingUntil;
    private long musicUntil;
    private ComponentName lastApp;
    private ComponentName musicReturn;
    private final HashMap<String, Boolean> activities = new HashMap<>();
    private String homeTile;
    private TintController tint;
    /** True while Amazon home is in front and we also listen for its focus moves (see watchAmazonHome). */
    private boolean watchingHome;
    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (finder != null) finder.postDelayed(() -> checkAmazonHome("after wake", WAKE_SEARCH_MS), WAKE_CHECK_DELAY_MS);
        }
    };
    private boolean screenOnRegistered;

    static FrescoService get() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        homeTile = getString(R.string.app_name);
        if (finder == null) {
            finderThread.start();
            finder = new Handler(finderThread.getLooper());
        }
        if (tint == null) tint = new TintController(this);
        tint.start();
        instance = this;
        Log.i(TAG, "service connected");
        if (!screenOnRegistered) {
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
            screenOnRegistered = true;
        }
        // After a restart Amazon home may already be up before we were bound: handle it like a Home press.
        finder.postDelayed(() -> checkAmazonHome("at connect", WAKE_SEARCH_MS), 300);
    }

    /**
     * Until a window changes, the "active window" is unknown (just after we connect, or after a wake),
     * so getRootInActiveWindow() is null. The window list is current, though: take the focused app window.
     */
    private AccessibilityNodeInfo focusedAppRoot() {
        AccessibilityNodeInfo fallback = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = w.getRoot();
            if (r == null) continue;
            if (w.isFocused() || w.isActive()) return r;
            if (fallback == null) fallback = r;
        }
        return fallback;
    }

    /** Runs on finderThread. If Amazon home is in front with no window change to tell us, act as on Home. */
    private void checkAmazonHome(String when, long searchMs) {
        checkAmazonHome(when, searchMs, 6);
    }

    /** The window tree can be missing just after we connect or the screen wakes, so look a few times. */
    private void checkAmazonHome(String when, long searchMs, int triesLeft) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) root = focusedAppRoot();
        if (root == null) {
            if (triesLeft > 1 && finder != null) {
                finder.postDelayed(() -> checkAmazonHome(when, searchMs, triesLeft - 1), 500);
            } else {
                Log.i(TAG, "home: no window to check " + when);
            }
            return;
        }
        if (AMAZON_HOME_PACKAGE.contentEquals(root.getPackageName())) {
            handler.post(() -> {
                // A window change may have started the hand-over already.
                long now = SystemClock.uptimeMillis();
                if (homeSearchUntil != 0 || cover != null || now - lastHomeRedirectAt < HOME_DEBOUNCE_MS) return;
                Log.i(TAG, "home: Amazon home already showing " + when);
                onAmazonHome(now, searchMs);
            });
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        lastKeyAt = SystemClock.uptimeMillis();
        if (streamSearchUntil != 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.i(TAG, "stremio: key pressed, leaving the sources alone");
            stopStreamSearch();
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        long now = SystemClock.uptimeMillis();
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // Home pressed while Amazon home was already up: it only moves its focus back to the start.
            // Not within 5 s of the last try, which may still be waiting on Android's 5-second hold.
            if (watchingHome && AMAZON_HOME_PACKAGE.contentEquals(pkg) && homeSearchUntil == 0
                    && now - lastHomeRedirectAt > 5000) {
                Log.i(TAG, "home: focus moved on Amazon home");
                onAmazonHome(now, HOME_SEARCH_MS);
            }
            return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        Log.i(TAG, "window " + pkg + "/" + cls);

        if (AMAZON_HOME_PACKAGE.contentEquals(pkg) && AMAZON_HOME_CLASS.contentEquals(cls)) {
            if (now - lastHomeRedirectAt > HOME_DEBOUNCE_MS) onAmazonHome(now, HOME_SEARCH_MS);
            return;
        }

        String wasInFront = lastApp == null ? null : lastApp.getPackageName();
        boolean activity = isActivity(pkg.toString(), cls.toString());
        if (activity) {
            watchAmazonHome(false);
            if (tint != null) tint.setForeground(pkg.toString());
            if (!MUSIC_PACKAGE.contentEquals(pkg) && !APPSTORE_LAUNCH_CLASS.contentEquals(cls)) {
                lastApp = new ComponentName(pkg.toString(), cls.toString());
            }
        }

        if (getPackageName().contentEquals(pkg)) {
            if (cover != null && HomeActivity.class.getName().contentEquals(cls)) {
                handler.removeCallbacks(hideCover);
                handler.postDelayed(hideCover, COVER_REVEAL_DELAY_MS);
            }
            return;
        }

        Prefs prefs = Prefs.get(this);
        if (MUSIC_PACKAGE.contentEquals(pkg) && prefs.musicTint()) {
            onMusic(cls, now);
            return;
        }

        if (pending != null && now < pendingUntil && pending.sourcePackage.contentEquals(pkg)) {
            Log.i(TAG, pkg + " resurfaced, bouncing back to " + pendingTarget);
            launch(pendingTarget);
            return;
        }

        for (Redirect r : REDIRECTS) {
            if (!r.sourcePackage.contentEquals(pkg)) continue;
            if (r.sourceClass != null ? !r.sourceClass.contentEquals(cls) : !activity || pkg.toString().equals(wasInFront)) continue;
            String target = prefs.buttonTarget(r.button);
            // Unchanged, or set to the app the button opens anyway: leave it be.
            if (target == null || target.contentEquals(pkg)) return;
            if (r.unlessInstalled != null && Apps.installed(this, r.unlessInstalled)) continue;
            if (now - lastKeyAt < (r.sourceClass == null ? APP_KEY_GRACE_MS : USER_KEY_GRACE_MS)) {
                Log.i(TAG, cls + " opened right after a key press, leaving it alone");
                return;
            }
            if (Apps.launchIntent(this, target) == null) {
                Log.w(TAG, cls + " opened by remote button, but " + target + " isn't installed; leaving it alone");
                return;
            }
            Log.i(TAG, cls + " opened by remote button, redirecting to " + target);
            redirect(r, target, now);
            return;
        }
    }

    /** Our overlays and other apps' dialogs also report windows; only activities say who is in front. */
    private boolean isActivity(String pkg, String cls) {
        String key = pkg + "/" + cls;
        Boolean known = activities.get(key);
        if (known == null) {
            try {
                getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
                known = true;
            } catch (PackageManager.NameNotFoundException e) {
                known = false;
            }
            activities.put(key, known);
        }
        return known;
    }

    private void onAmazonHome(long now, long searchMs) {
        lastHomeRedirectAt = now;
        watchAmazonHome(true);
        showCover();
        // Start decoding the wallpaper now, so it's ready by the time the tile click lands.
        Wallpapers.get(this).prewarm();
        startHomeSearch(now, searchMs);
    }

    /**
     * Focus events come from every app on each D-pad press, so we only ask for them while Amazon home is
     * in front, where they are the only sign of a Home press.
     */
    private void watchAmazonHome(boolean on) {
        if (watchingHome == on) return;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        watchingHome = on;
        info.eventTypes = on ? info.eventTypes | AccessibilityEvent.TYPE_VIEW_FOCUSED
                : info.eventTypes & ~AccessibilityEvent.TYPE_VIEW_FOCUSED;
        setServiceInfo(info);
    }

    private void redirect(final Redirect r, String target, long now) {
        pending = r;
        pendingTarget = target;
        pendingUntil = now + BOUNCE_WINDOW_MS;
        launch(target);
        killLater(r.sourcePackage);
    }

    private void killLater(final String pkg) {
        handler.postDelayed(() -> {
            ActivityManager am = getSystemService(ActivityManager.class);
            if (am != null) am.killBackgroundProcesses(pkg);
        }, KILL_DELAY_MS);
    }

    // ---- Amazon Music button: the bedtime tint ----

    private void onMusic(CharSequence cls, long now) {
        if (now < musicUntil) {
            if (MUSIC_RETURN == RETURN_RELAUNCH) {
                Log.i(TAG, "music: resurfaced (" + cls + "), returning again");
                returnFromMusic();
            }
            return;
        }
        if (now - lastKeyAt < USER_KEY_GRACE_MS) {
            Log.i(TAG, "music: " + cls + " opened right after a key press, leaving it alone");
            return;
        }
        Log.i(TAG, "music: button opened " + cls + ", toggling the tint and returning to " + lastApp);
        musicUntil = now + BOUNCE_WINDOW_MS;
        musicReturn = lastApp;
        if (tint != null) tint.toggleFromMusic(musicReturn == null ? getPackageName() : musicReturn.getPackageName());
        returnFromMusic();
        killLater(MUSIC_PACKAGE);
    }

    private void returnFromMusic() {
        if (MUSIC_RETURN == RETURN_BACK) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }
        ComponentName to = musicReturn;
        Intent intent = null;
        if (to != null && !getPackageName().equals(to.getPackageName())) {
            PackageManager pm = getPackageManager();
            intent = pm.getLeanbackLaunchIntentForPackage(to.getPackageName());
            if (intent == null) intent = pm.getLaunchIntentForPackage(to.getPackageName());
        }
        if (intent == null) {
            intent = new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_RETURN, true);
        }
        // Like pressing the app's tile: its existing task comes back to the front as it was.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "music: could not return to " + to, e);
        }
    }

    // ---- Home: click our tile on Amazon home ----

    private void startHomeSearch(long now, long searchMs) {
        homeSearchUntil = now + searchMs;
        requestTileSearch(0);
    }

    private void requestTileSearch(long delayMs) {
        if (finder == null) return;
        finder.removeCallbacks(findTile);
        finder.postDelayed(findTile, delayMs);
    }

    /** Runs on finderThread. */
    private void findTile() {
        long deadline = homeSearchUntil;
        if (deadline == 0) return;
        if (SystemClock.uptimeMillis() > deadline) {
            homeSearchUntil = 0;
            handler.post(hideCover);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && AMAZON_HOME_PACKAGE.contentEquals(root.getPackageName())) {
                // Android holds this launch until 5 s after the Home press, but it does arrive.
                Log.i(TAG, "home: no " + homeTile + " tile on Amazon home, opening the launcher directly");
                Intent intent = new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "home: could not open the launcher", e);
                }
            }
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && AMAZON_HOME_PACKAGE.contentEquals(root.getPackageName())) {
            for (AccessibilityNodeInfo tile : root.findAccessibilityNodeInfosByText(homeTile)) {
                if (tile.isClickable() && homeTile.contentEquals(tile.getContentDescription())
                        && tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "home: clicked " + homeTile + " tile");
                    homeSearchUntil = 0;
                    return;
                }
            }
        }
        requestTileSearch(HOME_SEARCH_POLL_MS);
    }

    // ---- the black cover ----

    private void showCover() {
        if (cover != null) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        lp.windowAnimations = 0;
        View view = new View(this);
        view.setBackgroundColor(Color.BLACK);
        try {
            getSystemService(WindowManager.class).addView(view, lp);
            cover = view;
            handler.postDelayed(hideCover, COVER_MAX_MS);
        } catch (Exception e) {
            Log.e(TAG, "could not show cover", e);
        }
    }

    private void hideCover() {
        handler.removeCallbacks(hideCover);
        if (cover == null) return;
        View view = cover;
        cover = null;
        try {
            getSystemService(WindowManager.class).removeView(view);
        } catch (Exception e) {
            Log.e(TAG, "could not remove cover", e);
        }
    }

    /** True while the black cover is up: Home was pressed and the tile click is on its way. */
    boolean homeInFlight() {
        return cover != null;
    }

    /** The launcher drew its first (black) frame: the hand-over is invisible, so the cover can go. */
    void launcherDrawn() {
        handler.post(hideCover);
    }

    // ---- Stremio: one-press play ----

    /** Called right after the launcher opens a stremio:///detail link. */
    void expectStremioSources() {
        handler.post(() -> {
            setIncludeNotImportantViews(true);
            streamSearchUntil = SystemClock.uptimeMillis() + STREAM_SEARCH_MS;
            if (finder == null) return;
            finder.removeCallbacks(findStream);
            finder.postDelayed(findStream, STREAM_SEARCH_DELAY_MS);
        });
    }

    private void stopStreamSearch() {
        streamSearchUntil = 0;
        if (finder != null) finder.removeCallbacks(findStream);
        handler.post(() -> setIncludeNotImportantViews(false));
    }

    /**
     * Stremio's source cards may be views the framework marks "not important for accessibility",
     * so include those, but only during this search; Amazon home's tile search keeps the lean tree.
     */
    private void setIncludeNotImportantViews(boolean on) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        int flags = on ? info.flags | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                : info.flags & ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        if (flags == info.flags) return;
        info.flags = flags;
        setServiceInfo(info);
    }

    /** Runs on finderThread. */
    private void findStream() {
        long deadline = streamSearchUntil;
        if (deadline == 0) return;
        if (SystemClock.uptimeMillis() > deadline) {
            Log.i(TAG, "stremio: no source card within " + STREAM_SEARCH_MS + " ms, leaving you on the page");
            stopStreamSearch();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && STREMIO.getPackageName().contentEquals(root.getPackageName())) {
            AccessibilityNodeInfo card = firstSource(root, 0);
            if (card != null) {
                String text = TextUtils.join(" | ", texts(card, new ArrayList<>(), 0));
                if (card.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "stremio: clicked source " + card.getClassName() + " \"" + text + "\"");
                    stopStreamSearch();
                    return;
                }
                Log.w(TAG, "stremio: click refused on \"" + text + "\"");
            }
        }
        finder.postDelayed(findStream, STREAM_SEARCH_POLL_MS);
    }

    /**
     * The first clickable node, in screen order, whose text reads like a source and isn't the trailer.
     * Children are tried first, so a clickable container around the cards never wins over a card.
     */
    private AccessibilityNodeInfo firstSource(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 40) return null;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo hit = firstSource(node.getChild(i), depth + 1);
            if (hit != null) return hit;
        }
        if (node.isClickable()) {
            List<String> t = texts(node, new ArrayList<>(), 0);
            if (!t.isEmpty()) {
                String all = TextUtils.join(" ", t);
                if (!all.toLowerCase(Locale.ROOT).contains("trailer")
                        && (STREAM_CARD.matcher(t.get(0)).matches() || RESOLUTION.matcher(all).find())) {
                    return node;
                }
            }
        }
        return null;
    }

    private static List<String> texts(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || depth > 8 || out.size() > 12) return out;
        CharSequence t = node.getText();
        if (t != null && t.toString().trim().length() > 0) out.add(t.toString().trim());
        else if (node.getContentDescription() != null && depth == 0) out.add(node.getContentDescription().toString().trim());
        for (int i = 0; i < node.getChildCount(); i++) texts(node.getChild(i), out, depth + 1);
        return out;
    }

    // ---- the tint, for launcher settings ----

    void previewTint(boolean on, int strength) {
        if (tint != null) tint.preview(on, strength);
    }

    /** Fast-forward on home: the same switch as the Amazon Music button. */
    void toggleTint() {
        if (tint != null) tint.toggleFromMusic(getPackageName());
    }

    // ---- shared ----

    private void launch(String target) {
        Intent intent = Apps.launchIntent(this, target);
        if (intent == null) {
            Log.e(TAG, "could not launch " + target + ": not installed");
            return;
        }
        // TASK_ON_HOME: Back from the target returns home, not to the stock app it replaced.
        intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "could not launch " + target, e);
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (screenOnRegistered) {
            unregisterReceiver(screenOn);
            screenOnRegistered = false;
        }
        homeSearchUntil = 0;
        streamSearchUntil = 0;
        finderThread.quitSafely();
        if (tint != null) tint.stop();
        hideCover();
        super.onDestroy();
    }

    /** A remote button's screen: which button (a Prefs.BUTTON_* key) and how to recognise what it opened. */
    private static final class Redirect {
        final String button;
        final String sourcePackage;
        /** Null: any screen of the package, if the app wasn't already in front. */
        final String sourceClass;
        /** The screen only means this button while that package isn't installed. */
        final String unlessInstalled;

        Redirect(String button, String sourcePackage, String sourceClass, String unlessInstalled) {
            this.button = button;
            this.sourcePackage = sourcePackage;
            this.sourceClass = sourceClass;
            this.unlessInstalled = unlessInstalled;
        }
    }
}
