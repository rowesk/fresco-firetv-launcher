package io.github.rowesk.fresco;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Launcher settings: heading and context column on the left, one glass sheet on the right holding
 * the main page and its sub-pages ("Dock" and its app picker, "Wallpapers", "Skip the tint in" and its
 * app picker, and a picker for each remappable remote button). Built to the approved prototype: the
 * droplet, the 150 px focus band, the schedule fold, push/pop pages, sliding values.
 *
 * The sheet content scrolls by translationY only; layout changes once at the end of a fold.
 */
final class SettingsController {
    interface Host {
        void previewTint(boolean on, int strength);
        void tintSettingsChanged();
        void openFireTvSettings();
        void syncNow();
        int nowMinutes();
        void wallpaperStyleChanged();
        /** The dock's apps or their order changed (Prefs.DOCK_APPS is already saved). */
        void dockChanged();
    }

    static final int UP = 1, DOWN = 2, LEFT = 3, RIGHT = 4, SELECT = 5, BACK = 6;

    private static final int SHEET_W = 1000, SHEET_H = 936, ROW_H = 88, BAND = 150;
    private static final String MAIN = "main", SKIP = "skip", PICK = "pick", WALLS = "walls",
            DOCK = "dock", DOCK_ADD = "dockadd", BUTTON = "button:";
    private static final int THUMB_W = 112, THUMB_H = 63;

    private final Context ctx;
    private final Host host;
    private final Prefs prefs;
    private final StremioClient stremio;
    private final Wallpapers wallpapers;
    final FrameLayout root;
    private final FrameLayout left, context, sheetGroup, page, scroll, rowsBox, dropBump;
    private final GlassView sheetGlass, drop;
    private final Viewport viewport;

    private final List<Row> rows = new ArrayList<>();
    private final ArrayList<String> pages = new ArrayList<>();
    private final ArrayList<Row> returns = new ArrayList<>();
    private List<Prefs.SkipApp> skip;
    private List<String> dock;
    private int sIdx;
    private float dropY = Float.NaN;
    private float scrollTarget;
    private boolean collapsing, depsGone, swapping, open;
    private int fold;
    private String ctxSec;
    private View ctxView;
    private Graph ctxGraph;
    private TextView ctxBody;
    private int repeatCount;
    private long lastMove;
    private boolean previewing;
    /** The four styles' pictures for now, small, only while the Wallpapers page is up. */
    private final Bitmap[] thumbs = new Bitmap[4];
    private int thumbGen;
    private int style;

    // Bedtime values, mirrored from Prefs while settings are open.
    private boolean enabled;
    private int start, full, off, strength;

    SettingsController(Context context, Host host, FrameLayout root) {
        this.ctx = context;
        this.host = host;
        this.root = root;
        prefs = Prefs.get(context);
        stremio = StremioClient.get(context);
        wallpapers = Wallpapers.get(context);

        left = Ui.add(root, new FrameLayout(context), 120, 96, 600, 900);
        TextView h1 = Ui.text(context, Ui.display400, 56, Ui.INK);
        h1.setText("Launcher settings");
        h1.setLetterSpacing(-.01f);
        h1.setShadowLayer(13, 0, 2, 0x4D000000);
        // 80 tall, centred on the old 64 px box, so descenders (the g) aren't clipped.
        left.setClipChildren(false);
        Ui.add(left, h1, 0, -8, 600, 80);
        this.context = Ui.add(left, new FrameLayout(context), 0, 184, 600, 700);

        sheetGroup = Ui.add(root, new FrameLayout(context), 800, 72, SHEET_W, SHEET_H);
        sheetGroup.setClipChildren(false);
        sheetGlass = Ui.add(sheetGroup, new GlassView(context, 48), 0, 0, SHEET_W, SHEET_H);
        sheetGlass.mag = 1.03f;
        sheetGlass.sheen = GlassView.SHEEN_SOFT;
        sheetGlass.setShadow(24, 60, .30f);
        viewport = Ui.add(sheetGroup, new Viewport(context), 0, 0, SHEET_W, SHEET_H);
        page = Ui.add(viewport, new FrameLayout(context), 0, 0, SHEET_W, SHEET_H);
        page.setClipChildren(false);
        scroll = Ui.add(page, new FrameLayout(context), 0, 0, SHEET_W, 4000);
        scroll.setClipChildren(false);
        dropBump = Ui.add(scroll, new FrameLayout(context), 0, 0, SHEET_W, ROW_H);
        dropBump.setClipChildren(false);
        drop = Ui.add(dropBump, new GlassView(context, 34), 14, 0, 972, ROW_H);
        drop.mag = 1.06f;
        drop.setShadow(8, 22, .18f);
        rowsBox = Ui.add(scroll, new FrameLayout(context), 0, 0, SHEET_W, 4000);
        rowsBox.setClipChildren(false);
        root.setVisibility(View.GONE);
    }

    boolean isOpen() { return open; }

    void applyTuning(Wallpapers.Tuning t) {
        root.setBackgroundColor(Ui.withAlpha(0x04080C, t.scrimSettings));
        sheetGlass.setTint(t.sheet, t.frost * .6f);
        drop.setMilk(t.milkSheet, 1);
    }

    // ------------------------------------------------------------------ open / close

    void open() {
        open = true;
        readPrefs();
        skip = prefs.skipApps();
        dock = Apps.dock(ctx);
        pages.clear();
        pages.add(MAIN);
        returns.clear();
        ctxSec = null;
        context.removeAllViews();
        ctxView = null;
        Anim.set(page, View.ALPHA, 1);
        Anim.set(page, View.TRANSLATION_X, 0);
        scrollTarget = 0;
        Anim.set(scroll, View.TRANSLATION_Y, 0);
        dropY = Float.NaN;
        sIdx = 0;
        renderRows();
        root.setVisibility(View.VISIBLE);
        Anim.fromTo(root, View.ALPHA, 0, 1, 260, Ui.OUT, 60);
        Anim.fromTo(sheetGroup, View.TRANSLATION_X, 48, 0, 440, Ui.OUT, 60);
        Anim.fromTo(sheetGroup, View.ALPHA, 0, 1, 440, Ui.OUT, 60);
        Anim.fromTo(left, View.TRANSLATION_X, -24, 0, 440, Ui.OUT, 60);
        focus(0, false, false);
        for (int i = 0; i < rows.size() && i < 10; i++) {
            View v = rows.get(i).view;
            Anim.fromTo(v, View.TRANSLATION_X, 28, 0, 360, Ui.OUT, 140 + 20L * i);
            Anim.fromTo(v, View.ALPHA, 0, 1, 360, Ui.OUT, 140 + 20L * i);
        }
    }

    void close() {
        open = false;
        endPreview();
        Anim.to(root, View.ALPHA, 0, 200, Ui.IN, 0).addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (open) return;
                root.setVisibility(View.GONE);
                releaseThumbs();
            }
        });
        Anim.to(sheetGroup, View.TRANSLATION_X, 32, 220, Ui.IN, 0);
    }

    /** Home pressed while settings were open: gone at once, no animation. */
    void closeNow() {
        open = false;
        endPreview();
        Anim.set(root, View.ALPHA, 0);
        root.setVisibility(View.GONE);
        releaseThumbs();
    }

    /** The launcher left the screen (its views are detached): drop the thumbnails like every other image. */
    void releaseImages() {
        releaseThumbs();
    }

    /** Back on screen with the Wallpapers page still up: decode its thumbnails again. */
    void restoreImages() {
        if (open && WALLS.equals(pages.get(pages.size() - 1)) && thumbs[0] == null) loadThumbs();
    }

    private void endPreview() {
        if (previewing) host.previewTint(false, 0);
        previewing = false;
    }

    /** Back from Amazon's settings, or a sync finished: refresh what may have changed. */
    void refreshAccount() {
        for (Row r : rows) if ("account".equals(r.kind)) fillRow(r);
        if ("stremio".equals(ctxSec)) {
            ctxSec = null;
            Row cur = current();
            if (cur != null) setContext(cur.section, false);
        }
    }

    private void readPrefs() {
        enabled = prefs.tintEnabled();
        start = prefs.tintStart();
        full = prefs.tintFull();
        off = prefs.tintOff();
        strength = prefs.tintStrength();
        style = wallpapers.style();
    }

    // ------------------------------------------------------------------ keys

    /** Returns false when Back on the main page should close settings. */
    boolean onKey(int key, boolean repeat) {
        if (swapping) return true;
        long now = System.currentTimeMillis();
        if ((key == UP || key == DOWN) && repeat && now - lastMove < 110) return true;
        if (key == UP || key == DOWN) lastMove = now;
        List<Row> fr = focRows();
        switch (key) {
            case UP:
                if (sIdx > 0) focus(sIdx - 1, true, repeat);
                else if (!repeat) Anim.bump(dropBump, 0, -1);
                return true;
            case DOWN:
                if (sIdx < fr.size() - 1) focus(sIdx + 1, true, repeat);
                else if (!repeat) Anim.bump(dropBump, 0, 1);
                return true;
            case LEFT:
            case RIGHT:
                adjust(key == LEFT ? -1 : 1, repeat);
                return true;
            case SELECT:
                if (!repeat) select();
                return true;
            case BACK:
                if (pages.size() > 1) {
                    popPage(null);
                    return true;
                }
                return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ rows

    private static final class Row {
        final String kind;
        String section;
        String header;
        boolean back, dep, below;
        int skipIndex = -1;
        int dockIndex = -1;
        int style = -1;
        /** A Prefs.BUTTON_* key, for a remote button's row and its picker's rows. */
        String button;
        Apps.App app;
        View view;
        int top, height;
        boolean focused;
        // parts
        TextView label, small, valText;
        ImageView lead, chevL, chevR, go, ext;
        FrameLayout num;
        TextView[] numTx;
        int numCur;
        LinearLayout val, lab;
        Switch sw;
        View dot, track, fill;
        Thumb thumb;
        ImageView check;

        Row(String kind) {
            this.kind = kind;
        }
    }

    private Row header(String text, boolean back) {
        Row r = new Row("header");
        r.header = text;
        r.back = back;
        return r;
    }

    private Row row(String kind, String section) {
        Row r = new Row(kind);
        r.section = section;
        return r;
    }

    private List<Row> model() {
        List<Row> m = new ArrayList<>();
        String pg = pages.get(pages.size() - 1);
        if (PICK.equals(pg)) {
            m.add(header("Choose an app to skip", false));
            for (Apps.App a : installedApps()) {
                Row r = row("pick", "skip");
                r.app = a;
                m.add(r);
            }
            if (m.size() == 1) m.add(row("none", "skip"));
            return m;
        }
        if (DOCK.equals(pg)) {
            m.add(header("Dock", true));
            for (int i = 0; i < dock.size(); i++) {
                Row r = row("dockapp", "dock-page");
                r.dockIndex = i;
                m.add(r);
            }
            m.add(row(dock.size() < Apps.DOCK_MAX ? "dockadd" : "dockfull", "dock-page"));
            return m;
        }
        if (DOCK_ADD.equals(pg)) {
            m.add(header("Choose an app for the dock", false));
            for (Apps.App a : Apps.launchable(ctx)) {
                if (dock.contains(a.pkg)) continue;
                Row r = row("dockpick", "dock-add");
                r.app = a;
                m.add(r);
            }
            if (m.size() == 1) m.add(row("none", "dock-add"));
            return m;
        }
        if (pg.startsWith(BUTTON)) {
            String key = pg.substring(BUTTON.length());
            m.add(header(buttonName(key) + " button", true));
            Row unchanged = row("choice", pg);
            unchanged.button = key;
            m.add(unchanged);
            // The button's own app is what "Unchanged" opens, so it isn't offered twice.
            String own = Prefs.BUTTON_NETFLIX.equals(key) ? FrescoService.NETFLIX_PACKAGE : FrescoService.PRIME_VIDEO_PACKAGE;
            for (Apps.App a : Apps.launchable(ctx)) {
                if (a.pkg.equals(own)) continue;
                Row r = row("choice", pg);
                r.button = key;
                r.app = a;
                m.add(r);
            }
            return m;
        }
        if (WALLS.equals(pg)) {
            m.add(header("Wallpapers", true));
            for (int i = 0; i < WallpaperStyle.IDS.length; i++) {
                Row r = row("wall", "wall-" + WallpaperStyle.IDS[i]);
                r.style = i;
                m.add(r);
            }
            return m;
        }
        if (SKIP.equals(pg)) {
            m.add(header("Skip the tint in", true));
            for (int i = 0; i < skip.size(); i++) {
                Row r = row("skip", "skip");
                r.skipIndex = i;
                m.add(r);
            }
            m.add(row("add", "skip"));
            return m;
        }
        // "Skip the tint in" stays when the schedule is off: the Music button can still turn the tint on.
        m.add(header("Stremio", false));
        m.add(row("account", "stremio"));
        m.add(header("Home screen", false));
        m.add(row("dockrow", "dock"));
        m.add(row("walls", "wall"));
        m.add(header("Bedtime tint", false));
        m.add(row("enabled", "tint"));
        for (String k : new String[]{"start", "full", "off", "strength"}) {
            Row r = row(k, "tint");
            r.dep = true;
            m.add(r);
        }
        m.add(row("skiplist", "skip"));
        m.add(header("Remote buttons", false));
        for (String key : new String[]{Prefs.BUTTON_NETFLIX, Prefs.BUTTON_PRIME_VIDEO}) {
            Row r = row("button", "buttons");
            r.button = key;
            m.add(r);
        }
        m.add(row("music", "buttons"));
        m.add(header("Fire TV", false));
        m.add(row("firetv", "firetv"));
        boolean after = false;
        for (Row r : m) {
            if (r.dep) after = true;
            else if (after) r.below = true;
        }
        return m;
    }

    private void renderRows() {
        for (Row r : rows) Anim.cancelAll(r.view);
        rowsBox.removeAllViews();
        rows.clear();
        rows.addAll(model());
        collapsing = false;
        depsGone = !enabled;
        for (Row r : rows) {
            r.view = r.header != null ? buildHeader(r) : buildRow(r);
            if (r.dep && !enabled) r.view.setVisibility(View.GONE);
            rowsBox.addView(r.view, Ui.at(0, 0, SHEET_W, r.height));
        }
        layoutRows();
        sIdx = Math.max(0, Math.min(sIdx, focRows().size() - 1));
    }

    /** Places rows top to bottom. The layout only changes here: at render, and once at the end of a fold. */
    private void layoutRows() {
        int y = 28;
        boolean first = true;
        for (Row r : rows) {
            if (r.view.getVisibility() == View.GONE) continue;
            if (r.header != null) {
                r.height = first ? 48 : 72;
                r.view.setPadding(r.view.getPaddingLeft(), first ? 6 : 30, 0, 10);
            }
            first = false;
            r.top = y;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) r.view.getLayoutParams();
            lp.topMargin = y;
            lp.height = r.height;
            r.view.setLayoutParams(lp);
            y += r.height;
        }
        contentH = y + 40;
    }

    private int contentH;

    private List<Row> focRows() {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (r.header == null && !(r.dep && !enabled)) out.add(r);
        return out;
    }

    private Row current() {
        List<Row> fr = focRows();
        return sIdx < fr.size() ? fr.get(sIdx) : null;
    }

    private int groupH() {
        int n = 0;
        for (Row r : rows) if (r.dep) n++;
        return n * ROW_H;
    }

    /** Where a row will sit once any fold in flight has committed. */
    private int logicalTop(Row r) {
        return r.top - (collapsing && r.below ? groupH() : 0);
    }

    private int logicalContentH() {
        return contentH - (collapsing ? groupH() : 0);
    }

    // ------------------------------------------------------------------ focus and scrolling

    private void focus(int i, boolean animate, boolean fast) {
        List<Row> fr = focRows();
        if (fr.isEmpty()) return;
        sIdx = Math.max(0, Math.min(fr.size() - 1, i));
        Row r = fr.get(sIdx);
        for (Row x : rows) if (x.header == null) setFocused(x, x == r);
        float y = logicalTop(r);
        float prevY = Float.isNaN(dropY) ? y : dropY;
        dropY = y;
        if (animate && prevY != y && !fast) {
            float cur = drop.getTranslationY();
            float[] f = {0, .4f, 1};
            Anim.keys(drop, View.TRANSLATION_Y, f, new float[]{cur, cur + (y - cur) * .55f, y}, 300, Ui.DROP, 0);
            Anim.keys(drop, View.SCALE_X, f, new float[]{1, .985f, 1}, 300, Ui.DROP, 0);
            Anim.keys(drop, View.SCALE_Y, f, new float[]{1, 1.12f, 1}, 300, Ui.DROP, 0);
        } else if (animate) {
            Anim.to(drop, View.TRANSLATION_Y, y, 150, Ui.HELD, 0);
            Anim.to(drop, View.SCALE_X, 1, 150, Ui.HELD, 0);
            Anim.to(drop, View.SCALE_Y, 1, 150, Ui.HELD, 0);
        } else {
            Anim.set(drop, View.TRANSLATION_Y, y);
            Anim.set(drop, View.SCALE_X, 1);
            Anim.set(drop, View.SCALE_Y, 1);
        }
        scrollTo(scrollFor(y), animate, fast);
        setContext(r.section, animate);
        boolean wantPreview = "strength".equals(r.kind);
        if (wantPreview || previewing) host.previewTint(wantPreview, strength);
        previewing = wantPreview;
        if (ctxGraph != null) ctxGraph.setMarker(r.kind);
    }

    private float scrollFor(float y) {
        float t = scrollTarget;
        if (y + t < BAND) t = BAND - y;
        if (y + ROW_H + t > SHEET_H - BAND) t = SHEET_H - BAND - ROW_H - y;
        return Math.min(0, Math.max(Math.min(0, SHEET_H - logicalContentH()), t));
    }

    private void scrollTo(float target, boolean animate, boolean fast) {
        scrollTarget = target;
        if (animate) Anim.to(scroll, View.TRANSLATION_Y, target, fast ? 180 : 420, fast ? Ui.HELD : Ui.STD, 0);
        else Anim.set(scroll, View.TRANSLATION_Y, target);
        // Soft fade at whichever edge has content clipped beyond it.
        viewport.setFades(target < -1, target > SHEET_H - logicalContentH() + 1, animate);
    }

    // ------------------------------------------------------------------ values

    private static int ev(int m) {
        return m < 12 * 60 ? m + 1440 : m;
    }

    private void adjust(int dir, boolean repeat) {
        Row r = current();
        if (r == null) return;
        repeatCount = repeat ? repeatCount + 1 : 0;
        if ("start".equals(r.kind) || "full".equals(r.kind) || "off".equals(r.kind)) {
            int step = repeatCount > 6 ? 30 : 15;
            int v = value(r.kind) + dir * step;
            if ("off".equals(r.kind)) {
                v = Math.max(4 * 60, Math.min(12 * 60, v));
            } else {
                v = ((v % 1440) + 1440) % 1440;
                int e = Math.max(17 * 60, Math.min(26 * 60, ev(v)));
                v = e % 1440;
                // start ≤ full: pushing one past the other carries it along, and its value bumps to show it.
                if ("start".equals(r.kind) && ev(v) > ev(full)) {
                    full = v;
                    carry("full", dir);
                }
                if ("full".equals(r.kind) && ev(v) < ev(start)) {
                    start = v;
                    carry("start", dir);
                }
            }
            if (v == value(r.kind)) {
                Anim.bump(r.val, dir, 0);
                return;
            }
            setValue(r.kind, v);
            slideNum(r, dir);
            prefs.setTimes(start, full, off);
            host.tintSettingsChanged();
            if (ctxGraph != null) ctxGraph.invalidate();
        } else if ("strength".equals(r.kind)) {
            int v = Math.max(10, Math.min(100, strength + dir * 10));
            if (v == strength) {
                Anim.bump(r.val, dir, 0);
                return;
            }
            strength = v;
            slideNum(r, dir);
            Anim.to(r.fill, View.SCALE_X, strength / 100f, 220, Ui.STD, 0);
            prefs.setTintStrength(strength);
            host.tintSettingsChanged();
            host.previewTint(true, strength);
            if (ctxGraph != null) ctxGraph.invalidate();
        } else if ("music".equals(r.kind)) {
            if (!repeat) toggleMusic(r, dir);
        } else if ("dockapp".equals(r.kind)) {
            if (!repeat) moveDockApp(r, dir);
        } else {
            Anim.bump(r.view, dir, 0);
        }
    }

    private void carry(String kind, int dir) {
        for (Row x : rows) {
            if (kind.equals(x.kind)) {
                setNumText(x, Ui.hhmm(value(kind)));
                Anim.bump(x.val, dir, 0);
            }
        }
    }

    private int value(String kind) {
        switch (kind) {
            case "start": return start;
            case "full": return full;
            case "off": return off;
            default: return strength;
        }
    }

    private void setValue(String kind, int v) {
        switch (kind) {
            case "start": start = v; break;
            case "full": full = v; break;
            case "off": off = v; break;
        }
    }

    private String numText(Row r) {
        if ("music".equals(r.kind)) return prefs.musicTint() ? "Bedtime tint" : "Unchanged";
        if ("dockapp".equals(r.kind)) return (r.dockIndex + 1) + " of " + dock.size();
        return "strength".equals(r.kind) ? strength + "%" : Ui.hhmm(value(r.kind));
    }

    /** The old value slides out the way of the key and the new one slides in after it. */
    private void slideNum(Row r, int dir) {
        TextView old = r.numTx[r.numCur];
        r.numCur = 1 - r.numCur;
        TextView nu = r.numTx[r.numCur];
        nu.setText(numText(r));
        Anim.fromTo(old, View.TRANSLATION_X, old.getTranslationX(), -dir * 18, 120, Ui.IN, 0);
        Anim.to(old, View.ALPHA, 0, 120, Ui.IN, 0);
        Anim.fromTo(nu, View.TRANSLATION_X, dir * 18, 0, 200, Ui.OUT, 0);
        Anim.fromTo(nu, View.ALPHA, 0, 1, 200, Ui.OUT, 0);
    }

    private void setNumText(Row r, String s) {
        TextView cur = r.numTx[r.numCur];
        Anim.cancelAll(cur);
        cur.setText(s);
        cur.setAlpha(1);
        cur.setTranslationX(0);
        TextView other = r.numTx[1 - r.numCur];
        Anim.cancelAll(other);
        other.setAlpha(0);
    }

    private void select() {
        Row r = current();
        if (r == null) return;
        switch (r.kind) {
            case "enabled":
                setTintEnabled(!enabled);
                break;
            case "skiplist":
                pushPage(SKIP);
                break;
            case "walls":
                pushPage(WALLS);
                break;
            case "dockrow":
                pushPage(DOCK);
                break;
            case "dockapp":
                removeDockApp(r);
                break;
            case "dockadd":
                pushPage(DOCK_ADD);
                break;
            case "dockpick": {
                dock.add(r.app.pkg);
                saveDock();
                Row target = new Row("dockapp");
                target.dockIndex = dock.size() - 1;
                popPage(target);
                break;
            }
            case "button":
                pushPage(BUTTON + r.button);
                break;
            case "choice":
                prefs.setButtonTarget(r.button, r.app == null ? null : r.app.pkg);
                popPage(null);
                break;
            case "music":
                toggleMusic(r, 1);
                break;
            case "dockfull":
                Anim.press(r.lab, 1, .04f);
                break;
            case "wall":
                chooseStyle(r);
                break;
            case "skip": {
                Prefs.SkipApp a = skip.get(r.skipIndex);
                a.on = !a.on;
                prefs.setSkipApps(skip);
                host.tintSettingsChanged();
                r.sw.set(a.on, true);
                r.valText.setText(a.on ? "Skipped" : "Tinted");
                break;
            }
            case "account":
                if (stremio.signedIn()) {
                    host.syncNow();
                    fillRow(r);
                }
                break;
            case "firetv":
                host.openFireTvSettings();
                break;
            case "add":
                pushPage(PICK);
                break;
            case "pick":
                skip.add(new Prefs.SkipApp(r.app.name, r.app.pkg, true));
                prefs.setSkipApps(skip);
                host.tintSettingsChanged();
                Row target = new Row("skip");
                target.skipIndex = skip.size() - 1;
                popPage(target);
                break;
            case "start":
            case "full":
            case "off":
            case "strength":
                Anim.bump(r.val, 0, 0);
                break;
        }
    }

    // ------------------------------------------------------------------ dock and remote buttons

    private void saveDock() {
        prefs.setDockApps(dock);
        host.dockChanged();
    }

    /**
     * Left/Right moves the app along the dock. The rows stay where they are: the two names swap and
     * the droplet follows the app to its new row.
     */
    private void moveDockApp(Row r, int dir) {
        int j = r.dockIndex + dir;
        if (j < 0 || j >= dock.size()) {
            Anim.bump(r.val, dir, 0);
            return;
        }
        dock.set(r.dockIndex, dock.set(j, dock.get(r.dockIndex)));
        saveDock();
        for (Row x : rows) if (x.dockIndex == r.dockIndex || x.dockIndex == j) x.label.setText(Apps.label(ctx, dock.get(x.dockIndex)));
        focus(sIdx + dir, true, false);
    }

    /** Select takes the app off the dock; the last one stays, since the dock is never empty. */
    private void removeDockApp(Row r) {
        if (dock.size() <= 1) {
            Anim.press(r.lab, 1, .04f);
            return;
        }
        dock.remove(r.dockIndex);
        saveDock();
        int idx = sIdx;
        renderRows();
        focus(idx, true, false);
    }

    private void toggleMusic(Row r, int dir) {
        prefs.setMusicTint(!prefs.musicTint());
        slideNum(r, dir);
        if (ctxBody != null && "buttons".equals(ctxSec)) ctxBody.setText(buttonsBody());
    }

    private static String buttonName(String key) {
        return Prefs.BUTTON_NETFLIX.equals(key) ? "Netflix" : "Prime Video";
    }

    private String buttonValue(String key) {
        String pkg = prefs.buttonTarget(key);
        return pkg == null ? "Unchanged" : Apps.label(ctx, pkg);
    }

    private String dockValue() {
        StringBuilder b = new StringBuilder();
        for (String pkg : dock) b.append(b.length() == 0 ? "" : ", ").append(Apps.label(ctx, pkg));
        return b.toString();
    }

    private String buttonsBody() {
        return "The Netflix and Prime Video buttons can open any app instead. Unchanged leaves a button alone, "
                + "so it opens its own app as usual. "
                + (prefs.musicTint() ? "The Amazon Music button turns the bedtime tint on or off."
                : "The Amazon Music button opens Amazon Music; fast-forward on home still turns the bedtime tint on or off.");
    }

    // ------------------------------------------------------------------ wallpapers

    /**
     * Applies at once: the check moves and the wallpaper behind the sheet changes with the usual
     * wallpaper-change transition, so the choice is seen, not just stored.
     */
    private void chooseStyle(Row r) {
        if (r.style == style) {
            Anim.press(r.check, 1, .12f);
            return;
        }
        for (Row x : rows) {
            if (x.check != null && x.style == style) Anim.to(x.check, View.ALPHA, 0, 120, Ui.IN, 0);
        }
        Anim.fromTo(r.check, View.ALPHA, 0, 1, 280, Ui.OUT, 0);
        Anim.fromTo(r.check, View.SCALE_X, .6f, 1, 280, Ui.OUT, 0);
        Anim.fromTo(r.check, View.SCALE_Y, .6f, 1, 280, Ui.OUT, 0);
        style = r.style;
        prefs.setWallpaperStyle(WallpaperStyle.IDS[style]);
        host.wallpaperStyleChanged();
    }

    /** Decodes what each style would show now, at 1/16 size. Random reuses the four as a mosaic. */
    private void loadThumbs() {
        releaseThumbs();
        final int gen = thumbGen;
        Calendar now = Calendar.getInstance();
        String[] keys = new String[thumbs.length];
        for (int i = 0; i < keys.length; i++) keys[i] = wallpapers.keyFor(i, now);
        wallpapers.loadThumbs(keys, (i, b) -> {
            if (gen != thumbGen || !open) {
                if (b != null) b.recycle();
                return;
            }
            thumbs[i] = b;
            for (Row r : rows) if (r.thumb != null && (r.style == i || r.style == WallpaperStyle.RANDOM)) setThumb(r);
        });
    }

    private void setThumb(Row r) {
        if (r.style == WallpaperStyle.RANDOM) r.thumb.setParts(thumbs.clone());
        else r.thumb.setParts(thumbs[r.style]);
    }

    /** Detach first, then recycle: a recycled bitmap must never reach a draw. */
    private void releaseThumbs() {
        thumbGen++;
        for (Row r : rows) if (r.thumb != null) r.thumb.setParts((Bitmap[]) null);
        for (int i = 0; i < thumbs.length; i++) {
            if (thumbs[i] != null) thumbs[i].recycle();
            thumbs[i] = null;
        }
    }

    // ------------------------------------------------------------------ the schedule fold

    /**
     * "Every evening" off: the four schedule rows fold away bottom-up and everything below glides up
     * through the space they left, so text never slides over visible text. On reverses it. A fold
     * commits (GONE, translations reset, one relayout) only if it is still the latest request.
     */
    private void setTintEnabled(boolean on) {
        enabled = on;
        prefs.setTintEnabled(on);
        host.tintSettingsChanged();
        Row tog = null;
        List<Row> deps = new ArrayList<>(), below = new ArrayList<>();
        for (Row r : rows) {
            if ("enabled".equals(r.kind)) tog = r;
            if (r.dep) deps.add(r);
            if (r.below) below.add(r);
        }
        if (tog == null) return;
        tog.sw.set(on, true);
        final int gh = deps.size() * ROW_H;
        final int token = ++fold;
        if (!on) {
            collapsing = true;
            for (int j = 0; j < deps.size(); j++) {
                View v = deps.get(deps.size() - 1 - j).view;
                v.setPivotY(0);
                Anim.to(v, View.ALPHA, 0, 150, Ui.FOLD, 12L * j);
                Anim.to(v, View.TRANSLATION_Y, -14, 150, Ui.FOLD, 12L * j);
                Anim.to(v, View.SCALE_Y, .94f, 150, Ui.FOLD, 12L * j);
            }
            ObjectAnimator last = null;
            for (Row r : below) last = Anim.to(r.view, View.TRANSLATION_Y, -gh, 460, Ui.GLIDE, 110);
            Runnable commit = () -> {
                if (token != fold) return;
                for (Row r : deps) {
                    Anim.cancelAll(r.view);
                    r.view.setAlpha(1);
                    r.view.setTranslationY(0);
                    r.view.setScaleY(1);
                    r.view.setVisibility(View.GONE);
                }
                for (Row r : below) Anim.set(r.view, View.TRANSLATION_Y, 0);
                collapsing = false;
                depsGone = true;
                layoutRows();
            };
            if (last != null) {
                last.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        commit.run();
                    }
                });
            } else {
                commit.run();
            }
        } else {
            boolean wasCommitted = depsGone;
            collapsing = false;
            if (wasCommitted) {
                for (Row r : deps) {
                    r.view.setVisibility(View.VISIBLE);
                    r.view.setPivotY(0);
                    Anim.set(r.view, View.ALPHA, 0);
                    Anim.set(r.view, View.TRANSLATION_Y, -18);
                    Anim.set(r.view, View.SCALE_Y, .92f);
                }
                depsGone = false;
                layoutRows();
                for (Row r : below) Anim.set(r.view, View.TRANSLATION_Y, -gh);
            }
            for (Row r : below) Anim.to(r.view, View.TRANSLATION_Y, 0, 480, Ui.STD, 40);
            for (int j = 0; j < deps.size(); j++) {
                View v = deps.get(j).view;
                Anim.to(v, View.ALPHA, 1, 340, Ui.OUT, 170 + 50L * j);
                Anim.to(v, View.TRANSLATION_Y, 0, 340, Ui.OUT, 170 + 50L * j);
                Anim.to(v, View.SCALE_Y, 1, 340, Ui.OUT, 170 + 50L * j);
            }
        }
        sIdx = focRows().indexOf(tog);
        scrollTo(scrollFor(logicalTop(tog)), true, false);
        updateTintContext(true);
    }

    // ------------------------------------------------------------------ pages

    private void pushPage(String pg) {
        returns.add(current());
        pages.add(pg);
        // Decoding starts now, so the thumbnails are usually in by the time the page's rows are built.
        if (WALLS.equals(pg)) loadThumbs();
        swapPage(1, null);
    }

    private void popPage(Row focusOn) {
        Row back = returns.isEmpty() ? null : returns.remove(returns.size() - 1);
        pages.remove(pages.size() - 1);
        swapPage(-1, focusOn != null ? focusOn : back);
    }

    /** The page slides out the way you're going; the next arrives from the other side, rows staggered. */
    private void swapPage(final int dir, final Row focusOn) {
        swapping = true;
        endPreview();
        Anim.to(page, View.TRANSLATION_X, -dir * 72, 170, Ui.IN, 0);
        Anim.to(page, View.ALPHA, 0, 170, Ui.IN, 0).addListener(new AnimatorListenerAdapter() {
            boolean cancelled;

            @Override
            public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                swapping = false;
                if (cancelled || !open) return;
                if (MAIN.equals(pages.get(pages.size() - 1))) skip = prefs.skipApps();
                renderRows();
                // The old rows are gone, so nothing draws the thumbnails any more.
                if (!WALLS.equals(pages.get(pages.size() - 1))) releaseThumbs();
                List<Row> fr = focRows();
                int idx = 0;
                if (focusOn != null) {
                    for (int j = 0; j < fr.size(); j++) {
                        Row r = fr.get(j);
                        if (r.kind.equals(focusOn.kind) && (focusOn.skipIndex < 0 || r.skipIndex == focusOn.skipIndex)
                                && (focusOn.dockIndex < 0 || r.dockIndex == focusOn.dockIndex)
                                && (focusOn.button == null || focusOn.button.equals(r.button))) {
                            idx = j;
                            break;
                        }
                    }
                }
                scrollTarget = 0;
                Anim.set(scroll, View.TRANSLATION_Y, 0);
                dropY = Float.NaN;
                focus(idx, false, false);
                Anim.fromTo(page, View.TRANSLATION_X, dir * 104, 0, 440, Ui.OUT, 0);
                Anim.fromTo(page, View.ALPHA, 0, 1, 440, Ui.OUT, 0);
                for (int i = 0; i < rows.size() && i < 9; i++) {
                    Anim.fromTo(rows.get(i).view, View.TRANSLATION_X, dir * 28, 0, 420, Ui.OUT, 40 + 24L * i);
                }
            }
        });
    }

    /** Launchable apps not on the skip list yet. */
    private List<Apps.App> installedApps() {
        HashSet<String> listed = new HashSet<>();
        for (Prefs.SkipApp s : skip) listed.add(s.pkg);
        List<Apps.App> out = new ArrayList<>();
        for (Apps.App a : Apps.launchable(ctx)) if (!listed.contains(a.pkg)) out.add(a);
        return out;
    }

    // ------------------------------------------------------------------ context column

    private void setContext(String sec, boolean animate) {
        if (sec.equals(ctxSec)) return;
        ctxSec = sec;
        final View old = ctxView;
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.VERTICAL);
        LinearLayout h2 = new LinearLayout(ctx);
        h2.setOrientation(LinearLayout.HORIZONTAL);
        h2.setGravity(Gravity.CENTER_VERTICAL);
        String title, body;
        ctxGraph = null;
        switch (sec.startsWith("wall-") ? "wall-" : sec.startsWith(BUTTON) ? BUTTON : sec) {
            case "dock":
                title = "Dock";
                body = "One to four apps sit in the glass dock on home. Home opens on the one you used last.";
                break;
            case "dock-page":
                title = "Dock";
                body = "The dock shows these apps from left to right. Left and Right move the focused app along "
                        + "the dock; Select takes it off.";
                break;
            case "dock-add":
                title = "Dock";
                body = "The app you choose goes at the right-hand end of the dock. The dock holds up to four.";
                break;
            case "buttons":
                title = "Remote buttons";
                body = buttonsBody();
                break;
            case BUTTON: {
                String name = buttonName(sec.substring(BUTTON.length()));
                title = name + " button";
                body = "Choose what the " + name + " button opens. Unchanged leaves it alone, so it opens "
                        + name + " as usual.";
                break;
            }
            case "wall":
                title = "Wallpapers";
                body = "The picture behind home follows the time of day: morning, day, evening and night. "
                        + "Choose photographs, paintings, abstract colour or space, or let it pick.";
                break;
            case "wall-": {
                int i = WallpaperStyle.fromId(sec.substring(5));
                title = WallpaperStyle.NAMES[i];
                body = styleBody(i);
                break;
            }
            case "stremio":
                ImageView mark = new ImageView(ctx);
                mark.setImageBitmap(Marks.stremio(true));
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(52 + 52 * Marks.PAD * 2 / Marks.SIZE, 52 + 52 * Marks.PAD * 2 / Marks.SIZE);
                mlp.rightMargin = 18 - 52 * Marks.PAD / Marks.SIZE;
                mlp.leftMargin = -52 * Marks.PAD / Marks.SIZE;
                h2.addView(mark, mlp);
                title = "Stremio";
                body = stremio.signedIn()
                        ? "Continue watching refreshes in the background, so home never waits for the internet."
                        : "Not signed in. Sign in once from a computer (app/tools/stremio-login.sh); only the login token is kept on the stick.";
                break;
            case "tint":
                title = "Bedtime tint";
                body = tintBody();
                break;
            case "firetv":
                title = "Fire TV settings";
                body = "Amazon’s own settings: display and sound, network, apps, remotes and devices. They open outside the launcher; Back brings you straight back here.";
                break;
            default:
                title = "Skip the tint in";
                body = "Copy-protected apps black out their video when anything is drawn over it, so the tint steps aside while one of these is open and comes back when you leave.";
                break;
        }
        TextView t = Ui.text(ctx, Ui.display500, 40, Ui.INK);
        t.setText(title);
        t.setLetterSpacing(-.005f);
        h2.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 48));
        v.addView(h2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 52));
        TextView p = Ui.text(ctx, Ui.text400, 28, Ui.white(.74f));
        p.setSingleLine(false);
        p.setGravity(Gravity.TOP);
        Ui.lineHeight(p, 42);
        p.setText(body);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(560, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = 14;
        v.addView(p, plp);
        ctxBody = p;
        if ("tint".equals(sec)) {
            Graph g = new Graph(ctx);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(600, 256 + Graph.HEADROOM);
            glp.topMargin = 48 - Graph.HEADROOM;
            v.addView(g, glp);
            g.setAlpha(enabled ? 1 : .3f);
            Row cur = current();
            g.setMarker(cur == null ? null : cur.kind);
            ctxGraph = g;
        }
        context.addView(v, new FrameLayout.LayoutParams(600, FrameLayout.LayoutParams.WRAP_CONTENT));
        ctxView = v;
        if (old != null) {
            if (animate) {
                Anim.to(old, View.ALPHA, 0, 120, Ui.IN, 0).addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        context.removeView(old);
                    }
                });
            } else {
                context.removeView(old);
            }
        }
        if (animate) {
            Anim.fromTo(v, View.ALPHA, 0, 1, 280, Ui.OUT, 80);
            Anim.fromTo(v, View.TRANSLATION_Y, 10, 0, 280, Ui.OUT, 80);
        }
    }

    private String styleBody(int i) {
        String b;
        switch (i) {
            case WallpaperStyle.ART:
                b = "Classic paintings chosen for the light at each time of day, a new one each day. "
                        + "Through December, winter and Christmas paintings take over.";
                break;
            case WallpaperStyle.ABSTRACT:
                b = "Abstract colour and form for each time of day, a different set each day of the week.";
                break;
            case WallpaperStyle.SPACE:
                b = "Pictures of space for each time of day, a different set each day of the week.";
                break;
            case WallpaperStyle.RANDOM:
                return "Each time of day picks one of the four styles. It keeps that picture until the next "
                        + "time of day, even after a restart.";
            default:
                b = "Photographs of lakes, forests, coasts and snow for each time of day, a different set each day of the week.";
                break;
        }
        // Abstract and space may not be packed yet; then the style shows the nature photo instead.
        if (i != WallpaperStyle.NATURE && wallpapers.keyFor(i, Calendar.getInstance()).startsWith("nature/")) {
            b += " They aren't in this build yet, so it shows Nature.";
        }
        return b;
    }

    private String tintBody() {
        String button = prefs.musicTint() ? "The Amazon Music button" : "Fast-forward on home";
        String note = TintController.overrideNote(prefs);
        String base = enabled ? "Dims and warms every app at night. " + button + " turns it on or off; a second press goes back to the schedule."
                : "Off in the evenings. " + button + " still turns it on until " + Ui.hhmm(prefs.tintOff()) + ".";
        return note == null ? base : note + " " + base;
    }

    private void updateTintContext(boolean animate) {
        if (!"tint".equals(ctxSec)) return;
        if (ctxBody != null) ctxBody.setText(tintBody());
        if (ctxGraph != null) {
            if (animate) Anim.to(ctxGraph, View.ALPHA, enabled ? 1 : .3f, 320, Ui.STD, 0);
            else ctxGraph.setAlpha(enabled ? 1 : .3f);
            ctxGraph.invalidate();
        }
    }

    // ------------------------------------------------------------------ building rows

    private View buildHeader(Row r) {
        TextView t = Ui.text(ctx, Ui.text600, 24, Ui.white(.62f));
        t.setGravity(Gravity.TOP);
        t.setText(r.header);
        t.setPadding(48, 30, 0, 10);
        if (r.back) {
            Drawable d = icon(R.drawable.ic_chev_left, Ui.white(.62f));
            d.setBounds(0, 0, 26, 26);
            t.setCompoundDrawables(d, null, null, null);
            t.setCompoundDrawablePadding(6);
            t.setPadding(42, 30, 0, 10);
        }
        r.height = 72;
        return t;
    }

    private View buildRow(Row r) {
        r.height = ROW_H;
        FrameLayout f = new FrameLayout(ctx);
        f.setClipChildren(false);
        r.lab = new LinearLayout(ctx);
        r.lab.setOrientation(LinearLayout.HORIZONTAL);
        r.lab.setGravity(Gravity.CENTER_VERTICAL);
        f.addView(r.lab, Ui.at(48, 0, FrameLayout.LayoutParams.WRAP_CONTENT, ROW_H));
        r.val = new LinearLayout(ctx);
        r.val.setOrientation(LinearLayout.HORIZONTAL);
        r.val.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, ROW_H, Gravity.TOP | Gravity.END);
        vlp.rightMargin = 48;
        f.addView(r.val, vlp);

        switch (r.kind) {
            case "add":
            case "dockadd":
                r.lead = new ImageView(ctx);
                r.lead.setImageResource(R.drawable.ic_plus);
                r.lab.addView(r.lead, lp(30, 30, 0, 16));
                label(r, "Add an app");
                break;
            case "account":
                label(r, "Account");
                r.dot = new View(ctx);
                r.val.addView(r.dot, lp(12, 12, 0, 14));
                r.valText = valueText(r.val);
                break;
            case "enabled":
                label(r, "Every evening");
                r.sw = new Switch(ctx, true);
                r.sw.set(enabled, false);
                r.val.addView(r.sw, lp(76, 42, 0, 0));
                break;
            case "start":
            case "full":
            case "off":
                label(r, "start".equals(r.kind) ? "Starts fading in" : "full".equals(r.kind) ? "Full strength by" : "Turns off");
                chevrons(r, 92, null);
                break;
            case "strength":
                label(r, "Strength");
                chevrons(r, 76, ctx2 -> {
                    FrameLayout track = new FrameLayout(ctx);
                    track.setOutlineProvider(round(5));
                    track.setClipToOutline(true);
                    r.track = track;
                    View fill = new View(ctx);
                    r.fill = fill;
                    fill.setPivotX(0);
                    fill.setScaleX(strength / 100f);
                    track.addView(fill, new FrameLayout.LayoutParams(300, 10));
                    return track;
                });
                break;
            case "skiplist":
            case "walls":
            case "dockrow":
            case "button":
                label(r, "walls".equals(r.kind) ? "Wallpapers" : "skiplist".equals(r.kind) ? "Skip the tint in"
                        : "dockrow".equals(r.kind) ? "Dock" : buttonName(r.button) + " button");
                r.valText = valueText(r.val);
                // Four dock apps or a long app name end in an ellipsis rather than run into the label.
                r.valText.setMaxWidth(560);
                r.valText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                r.go = new ImageView(ctx);
                r.go.setImageResource(R.drawable.ic_chev_right);
                r.val.addView(r.go, lp(26, 26, 0, 0));
                break;
            case "wall":
                r.thumb = new Thumb(ctx);
                r.lab.addView(r.thumb, lp(THUMB_W, THUMB_H, 0, 28));
                setThumb(r);
                label(r, WallpaperStyle.NAMES[r.style]);
                r.check = new ImageView(ctx);
                r.check.setImageResource(R.drawable.ic_check);
                r.check.setAlpha(r.style == style ? 1f : 0f);
                r.val.addView(r.check, lp(32, 32, 0, 0));
                break;
            case "firetv":
                label(r, "Fire TV settings");
                r.small = Ui.text(ctx, Ui.text400, 22, Ui.white(.62f));
                r.small.setText("Amazon’s own");
                r.lab.addView(r.small, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 30, 16, 0));
                r.ext = new ImageView(ctx);
                r.ext.setImageResource(R.drawable.ic_ext);
                r.val.addView(r.ext, lp(30, 30, 0, 0));
                break;
            case "skip": {
                Prefs.SkipApp a = skip.get(r.skipIndex);
                label(r, a.name);
                r.valText = valueText(r.val);
                r.valText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 24);
                r.sw = new Switch(ctx, false);
                r.sw.set(a.on, false);
                r.val.addView(r.sw, lp(76, 42, 0, 0));
                break;
            }
            case "music":
                label(r, "Amazon Music button");
                chevrons(r, 184, null);
                break;
            case "dockapp":
                label(r, Apps.label(ctx, dock.get(r.dockIndex)));
                chevrons(r, 100, null);
                break;
            case "dockfull":
                label(r, "The dock holds four apps");
                break;
            case "choice":
                label(r, r.app == null ? "Unchanged" : r.app.name);
                if (r.app == null) {
                    r.small = Ui.text(ctx, Ui.text400, 22, Ui.white(.62f));
                    r.small.setText("Opens " + buttonName(r.button));
                    r.lab.addView(r.small, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 30, 16, 0));
                }
                r.check = new ImageView(ctx);
                r.check.setImageResource(R.drawable.ic_check);
                String target = prefs.buttonTarget(r.button);
                r.check.setAlpha((r.app == null ? target == null : r.app.pkg.equals(target)) ? 1f : 0f);
                r.val.addView(r.check, lp(32, 32, 0, 0));
                break;
            case "dockpick":
            case "pick":
                label(r, r.app.name);
                r.small = Ui.text(ctx, Ui.text400, 22, Ui.white(.62f));
                r.small.setText(r.app.pkg);
                r.lab.addView(r.small, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 30, 16, 0));
                r.go = new ImageView(ctx);
                r.go.setImageResource(R.drawable.ic_plus);
                r.val.addView(r.go, lp(28, 28, 0, 0));
                break;
            case "none":
                label(r, "dock-add".equals(r.section) ? "Every app is already in the dock" : "Every installed app is already on the list");
                break;
        }
        r.view = f;
        fillRow(r);
        return f;
    }

    private interface Extra {
        View make(Context c);
    }

    private void chevrons(Row r, int numW, Extra extra) {
        r.chevL = new ImageView(ctx);
        r.chevL.setImageResource(R.drawable.ic_chev_left);
        r.val.addView(r.chevL, lp(26, 26, 0, 14));
        r.num = new FrameLayout(ctx);
        r.num.setClipChildren(true);
        r.numTx = new TextView[2];
        for (int i = 0; i < 2; i++) {
            TextView t = Ui.text(ctx, Ui.text400, 30, Ui.white(.78f));
            t.setGravity(Gravity.CENTER);
            t.setFontFeatureSettings("tnum");
            t.setAlpha(i == 0 ? 1 : 0);
            r.num.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 40));
            r.numTx[i] = t;
        }
        r.numTx[0].setText(numText(r));
        if (extra != null) {
            LinearLayout str = new LinearLayout(ctx);
            str.setOrientation(LinearLayout.HORIZONTAL);
            str.setGravity(Gravity.CENTER_VERTICAL);
            str.addView(r.num, lp(numW, 40, 0, 20));
            str.addView(extra.make(ctx), lp(300, 10, 0, 0));
            r.val.addView(str, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 40, 0, 14));
        } else {
            r.val.addView(r.num, lp(numW, 40, 0, 14));
        }
        r.chevR = new ImageView(ctx);
        r.chevR.setImageResource(R.drawable.ic_chev_right);
        r.val.addView(r.chevR, lp(26, 26, 0, 0));
    }

    private void label(Row r, String text) {
        r.label = Ui.text(ctx, Ui.text500, 30, Ui.INK);
        r.label.setText(text);
        r.lab.addView(r.label, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 40, 0, 0));
    }

    private TextView valueText(LinearLayout val) {
        TextView t = Ui.text(ctx, Ui.text400, 30, Ui.white(.78f));
        t.setFontFeatureSettings("tnum");
        val.addView(t, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 40, 0, 14));
        return t;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int left, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.leftMargin = left;
        p.rightMargin = right;
        return p;
    }

    private static ViewOutlineProvider round(final float r) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        };
    }

    private Drawable icon(int res, int color) {
        Drawable d = ctx.getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    /** Row ink flips to dark when the droplet arrives: one redraw of that row, not an animation. */
    private void setFocused(Row r, boolean f) {
        if (r.focused == f) return;
        r.focused = f;
        fillRow(r);
    }

    private void fillRow(Row r) {
        boolean f = r.focused;
        int c = f ? Ui.INK_DARK : 0xFFFFFF;
        if (r.label != null) {
            r.label.setTextColor(f ? Ui.INK_DARK : Ui.INK);
            r.label.setAlpha("none".equals(r.kind) || "dockfull".equals(r.kind) ? .6f : f ? 1 : .86f);
        }
        if (r.small != null) r.small.setTextColor(Ui.withAlpha(c, .62f));
        if (r.lead != null) r.lead.setColorFilter(f ? Ui.INK_DARK : Ui.INK);
        if (r.valText != null) r.valText.setTextColor(Ui.withAlpha(c, .78f));
        if (r.numTx != null) for (TextView t : r.numTx) t.setTextColor(Ui.withAlpha(c, .78f));
        if (r.chevL != null) {
            r.chevL.setColorFilter(Ui.withAlpha(c, .7f));
            r.chevR.setColorFilter(Ui.withAlpha(c, .7f));
            r.chevL.setAlpha(f ? 1f : 0f);
            r.chevR.setAlpha(f ? 1f : 0f);
        }
        if (r.check != null) r.check.setColorFilter(Ui.withAlpha(c, .9f));
        if (r.go != null) {
            boolean chev = "skiplist".equals(r.kind) || "walls".equals(r.kind) || "dockrow".equals(r.kind) || "button".equals(r.kind);
            r.go.setColorFilter(Ui.withAlpha(c, chev ? .7f : .78f));
            r.go.setAlpha(chev ? .7f : 1f);
        }
        if (r.ext != null) r.ext.setColorFilter(Ui.withAlpha(c, .8f));
        if (r.sw != null) r.sw.setInk(f);
        if (r.track != null) {
            GradientDrawable t = new GradientDrawable();
            t.setColor(Ui.withAlpha(c, .2f));
            t.setCornerRadius(5);
            r.track.setBackground(t);
            GradientDrawable fl = new GradientDrawable();
            fl.setColor(f ? 0xFFC26A12 : Ui.AMBER);
            fl.setCornerRadius(5);
            r.fill.setBackground(fl);
        }
        if ("account".equals(r.kind)) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            if (stremio.signedIn()) {
                d.setColor(0xFF000000 | c);
                r.valText.setText(stremio.syncing() ? "Syncing…" : syncedText());
            } else {
                d.setColor(0);
                d.setStroke(2, Ui.withAlpha(c, .7f));
                r.valText.setText("Not signed in · sign in from a computer");
            }
            r.dot.setBackground(d);
        }
        if ("skiplist".equals(r.kind)) {
            int n = 0;
            for (Prefs.SkipApp a : skip) if (a.on) n++;
            r.valText.setText(n == skip.size() ? n + " apps" : n + " of " + skip.size() + " apps");
        }
        if ("skip".equals(r.kind)) r.valText.setText(skip.get(r.skipIndex).on ? "Skipped" : "Tinted");
        if ("walls".equals(r.kind)) r.valText.setText(WallpaperStyle.NAMES[style]);
        if ("dockrow".equals(r.kind)) r.valText.setText(dockValue());
        if ("button".equals(r.kind)) r.valText.setText(buttonValue(r.button));
    }

    private String syncedText() {
        long t = prefs.syncedAt();
        if (t == 0) return "Connected · not synced yet";
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        String hm = Ui.two(c.get(Calendar.HOUR_OF_DAY)) + ":" + Ui.two(c.get(Calendar.MINUTE));
        if (DateUtils.isToday(t)) return "Connected · synced " + hm;
        return "Connected · synced " + c.get(Calendar.DAY_OF_MONTH) + " "
                + c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.UK) + " " + hm;
    }

    // ------------------------------------------------------------------ parts

    /**
     * The sheet's viewport: clips to the rounded sheet and fades content softly at whichever edge has
     * more beyond it (56 px top, 72 px bottom), using View's own fading edges, which draw only the strip.
     */
    private static final class Viewport extends FrameLayout {
        private float top, bottom;
        private ValueAnimator anim;

        Viewport(Context c) {
            super(c);
            setOutlineProvider(round(48));
            setClipToOutline(true);
            // Fading edges are drawn in View.draw(), which a ViewGroup skips unless it says it draws.
            setWillNotDraw(false);
            setVerticalFadingEdgeEnabled(true);
            setFadingEdgeLength(72);
        }

        void setFades(boolean t, boolean b, boolean animate) {
            final float tt = t ? 56f / 72f : 0, tb = b ? 1 : 0;
            if (anim != null) anim.cancel();
            if (!animate) {
                top = tt;
                bottom = tb;
                invalidate();
                return;
            }
            final float t0 = top, b0 = bottom;
            if (t0 == tt && b0 == tb) return;
            anim = ValueAnimator.ofFloat(0, 1);
            anim.setDuration(260);
            anim.setInterpolator(Ui.STD);
            anim.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                top = t0 + (tt - t0) * f;
                bottom = b0 + (tb - b0) * f;
                invalidate();
            });
            anim.start();
        }

        @Override
        protected float getTopFadingEdgeStrength() {
            return top;
        }

        @Override
        protected float getBottomFadingEdgeStrength() {
            return bottom;
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /**
     * A wallpaper thumbnail with rounded corners: one picture, or for Random the four styles as a 2x2
     * mosaic. A faint placeholder shows until the pictures are decoded (and through the mosaic's gaps).
     */
    private static final class Thumb extends View {
        private Bitmap[] parts;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Rect dst = new Rect();

        Thumb(Context c) {
            super(c);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Ui.white(.14f));
            setBackground(bg);
            setOutlineProvider(round(12));
            setClipToOutline(true);
        }

        void setParts(Bitmap... p) {
            parts = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (parts == null) return;
            int w = getWidth(), h = getHeight();
            if (parts.length == 1) {
                draw(c, parts[0], 0, 0, w, h);
                return;
            }
            int gap = 2, cw = (w - gap) / 2, ch = (h - gap) / 2;
            for (int i = 0; i < parts.length && i < 4; i++) {
                int x = i % 2 == 0 ? 0 : cw + gap, y = i < 2 ? 0 : ch + gap;
                draw(c, parts[i], x, y, i % 2 == 0 ? cw : w - cw - gap, i < 2 ? ch : h - ch - gap);
            }
        }

        private void draw(Canvas c, Bitmap b, int x, int y, int w, int h) {
            if (b == null || b.isRecycled()) return;
            dst.set(x, y, x + w, y + h);
            c.drawBitmap(b, null, dst, paint);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /**
     * A switch made of layers that only translate and fade: the knob travels 34 px (220 ms) while
     * the on-track and on-knob crossfade over the off ones.
     */
    private static final class Switch extends FrameLayout {
        private final boolean amber;
        private final View trackOff, trackOn, knobOff, knobOn;
        private final FrameLayout knob;
        private boolean on;

        Switch(Context c, boolean amber) {
            super(c);
            this.amber = amber;
            trackOff = Ui.add(this, new View(c), 0, 0, 76, 42);
            trackOn = Ui.add(this, new View(c), 0, 0, 76, 42);
            knob = Ui.add(this, new FrameLayout(c), 5, 5, 32, 32);
            knobOff = Ui.add(knob, new View(c), 0, 0, 32, 32);
            knobOn = Ui.add(knob, new View(c), 0, 0, 32, 32);
            setInk(false);
        }

        void setInk(boolean focused) {
            int c = focused ? Ui.INK_DARK : 0xFFFFFF;
            trackOff.setBackground(pill(Ui.withAlpha(c, .2f), 21));
            trackOn.setBackground(pill(amber ? Ui.AMBER : Ui.withAlpha(c, .9f), 21));
            knobOff.setBackground(pill(0xFF000000 | c, 16));
            knobOn.setBackground(pill(amber ? 0xFF2A1606 : focused ? 0xFFFFFFFF : Ui.INK_DARK, 16));
        }

        void set(boolean on, boolean animate) {
            this.on = on;
            float a = on ? 1 : 0;
            if (animate) {
                Anim.to(knob, View.TRANSLATION_X, on ? 34 : 0, 220, Ui.STD, 0);
                Anim.to(trackOn, View.ALPHA, a, 220, Ui.STD, 0);
                Anim.to(knobOn, View.ALPHA, a, 220, Ui.STD, 0);
                Anim.to(knobOff, View.ALPHA, 1 - a, 220, Ui.STD, 0);
            } else {
                Anim.set(knob, View.TRANSLATION_X, on ? 34 : 0);
                Anim.set(trackOn, View.ALPHA, a);
                Anim.set(knobOn, View.ALPHA, a);
                Anim.set(knobOff, View.ALPHA, 1 - a);
            }
        }

        private static GradientDrawable pill(int color, float r) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(r);
            return d;
        }
    }

    /**
     * The schedule graph: 17:00 → 10:00. The amber area ramps from start to full, holds at the chosen
     * strength and drops at off. The focused time shows as a dashed marker; a tick marks now.
     * Redraws only when a value or the focused row changes.
     */
    private final class Graph extends View {
        /** Room above the plot for the marker's time label, which sits above the top of the ramp. */
        static final int HEADROOM = 32;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private String marker;

        Graph(Context c) {
            super(c);
            text.setTypeface(Ui.text500);
            text.setTextSize(22);
            text.setFontFeatureSettings("tnum");
            text.setTextAlign(Paint.Align.CENTER);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
        }

        void setMarker(String kind) {
            String m = "start".equals(kind) || "full".equals(kind) || "off".equals(kind) ? kind : null;
            if (m == null ? marker == null : m.equals(marker)) return;
            marker = m;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            c.translate(0, HEADROOM);
            final float W = 600, base = 176, top = 26, a0 = 17 * 60, a1 = 10 * 60 + 1440;
            float s = ev(start), f = Math.max(ev(full), s), o = off + 1440;
            float yTop = base - (base - top) * (strength / 100f);
            path.reset();
            path.moveTo(0, base);
            path.lineTo(x(s, a0, a1, W), base);
            path.lineTo(x(f, a0, a1, W), yTop);
            path.lineTo(x(o, a0, a1, W), yTop);
            path.lineTo(x(o, a0, a1, W) + 8, base);
            path.lineTo(W, base);

            line.setStyle(Paint.Style.STROKE);
            line.setPathEffect(null);
            line.setStrokeWidth(2);
            line.setColor(Ui.white(.28f));
            c.drawLine(0, base, W, base, line);

            int pathAlpha = Math.round((enabled ? 1 : .35f) * 255);
            area.setShader(new LinearGradient(0, yTop, 0, base, Ui.withAlpha(Ui.AMBER, .55f), Ui.withAlpha(Ui.AMBER, .04f), Shader.TileMode.CLAMP));
            area.setAlpha(pathAlpha);
            Path filled = new Path(path);
            filled.close();
            c.drawPath(filled, area);
            line.setStrokeWidth(3);
            line.setColor(Ui.AMBER);
            line.setAlpha(pathAlpha);
            c.drawPath(path, line);

            text.setColor(Ui.white(.52f));
            line.setStrokeWidth(2);
            line.setColor(Ui.white(.35f));
            for (int h = 18; h <= 33; h += 3) {
                float tx = x(h * 60, a0, a1, W);
                c.drawText(Ui.hhmm(h * 60), tx, base + 40, text);
                c.drawLine(tx, base + 4, tx, base + 12, line);
            }

            float now = ev(host.nowMinutes());
            if (now >= a0 && now <= a1) {
                float nx = x(now, a0, a1, W);
                line.setStrokeWidth(3);
                line.setColor(Ui.white(.8f));
                c.drawLine(nx, base - 6, nx, base + 6, line);
                text.setColor(Ui.white(.74f));
                c.drawText("now", nx, base + 72, text);
            }

            if (marker != null) {
                float m = "start".equals(marker) ? s : "full".equals(marker) ? f : o;
                float mx = x(m, a0, a1, W);
                line.setStrokeWidth(2);
                line.setColor(Ui.INK);
                line.setPathEffect(new DashPathEffect(new float[]{3, 6}, 0));
                c.drawLine(mx, top - 10, mx, base, line);
                line.setPathEffect(null);
                area.setShader(null);
                area.setColor(Ui.INK);
                c.drawCircle(mx, m == s ? base : yTop, 7, area);
                text.setColor(Ui.INK);
                text.setTypeface(Ui.text600);
                c.drawText(Ui.hhmm((int) m), mx, top - 22, text);
                text.setTypeface(Ui.text500);
            }
        }

        private float x(float m, float a0, float a1, float w) {
            return (m - a0) / (a1 - a0) * w;
        }
    }
}
