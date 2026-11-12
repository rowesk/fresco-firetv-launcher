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
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

/**
 * Launcher settings: heading and context column on the left, one glass sheet on the right holding
 * six destinations and their sub-pages. Layout follows the refined handoff: content-sized fixed
 * glass panes, whole-row scrolling, the schedule fold, push/pop pages and sliding values.
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

    private static final int SHEET_W = 1000, SHEET_H = 920, ROW_H = 88;
    private static final String MAIN = "main", SKIP = "skip", PICK = "pick", WALLS = "walls",
            TINT = "tint", REMOTE = "remote", DOCK = "dock", DOCK_ADD = "dockadd", BUTTON = "button:";
    private static final int THUMB_W = 208, THUMB_H = 116;

    private final Context ctx;
    private final Host host;
    private final Prefs prefs;
    private final StremioClient stremio;
    private final Wallpapers wallpapers;
    final FrameLayout root;
    private final FrameLayout left, context, sheetGroup, page, scroll, rowsBox, dropBump;
    private GlassView sheetGlass;
    private final GlassView drop;
    private final java.util.Map<Integer, GlassView> sheets = new java.util.HashMap<>();
    private Wallpapers.Tuning tuning;
    private final SettingsPreviewTuning previewTuning = new SettingsPreviewTuning();
    private int sheetHeight = SHEET_H;
    private boolean removeArmed;
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

        left = Ui.add(root, new FrameLayout(context), 120, 0, 600, 1000);
        left.setClipChildren(false);
        this.context = Ui.add(left, new FrameLayout(context), 0, 0, 600, 1000);
        this.context.setClipChildren(false);

        sheetGroup = Ui.add(root, new FrameLayout(context), 800, 80, SHEET_W, SHEET_H);
        sheetGroup.setClipChildren(false);

        viewport = Ui.add(sheetGroup, new Viewport(context), 0, 0, SHEET_W, SHEET_H);
        page = Ui.add(viewport, new FrameLayout(context), 0, 0, SHEET_W, SHEET_H);
        page.setClipChildren(false);
        scroll = Ui.add(page, new FrameLayout(context), 0, 0, SHEET_W, 4000);
        scroll.setClipChildren(false);
        dropBump = Ui.add(scroll, new FrameLayout(context), 0, 0, SHEET_W, ROW_H);
        dropBump.setClipChildren(false);
        drop = Ui.add(dropBump, new GlassView(context, 36), 12, 0, 976, ROW_H);
        drop.mag = 1.10f;
        drop.setShadow(10, 26, .20f);
        drop.setDropletLight(true);
        rowsBox = Ui.add(scroll, new FrameLayout(context), 0, 0, SHEET_W, 4000);
        rowsBox.setClipChildren(false);
        root.setVisibility(View.GONE);
    }

    boolean isOpen() { return open; }

    void applyTuning(Wallpapers.Tuning t) {
        tuning = t;
        root.setBackgroundColor(Ui.withAlpha(0x04080C, t.scrimSettings));
        for (GlassView glass : sheets.values()) glass.setTint(t.sheet, t.frost * .6f);
        drop.setMilk(t.rowMilk, 1);
        if (open && current() != null) { ctxSec = null; setContext(current().section, false); }
    }

    /** Each size is rasterised once; page/fold transitions only cross-fade these fixed panes. */
    private void sizeSheet(int height) {
        height = Math.min(SHEET_H, height);
        final GlassView old = sheetGlass;
        GlassView next = sheets.get(height);
        if (next == null) {
            next = new GlassView(ctx, 48);
            next.mag = 1.03f;
            next.sheen = GlassView.SHEEN_SOFT;
            next.setShadow(24, 60, .30f);
            next.setAlpha(0);
            sheetGroup.addView(next, 0, Ui.at(0, 0, SHEET_W, height));
            sheets.put(height, next);
            if (tuning != null) next.setTint(tuning.sheet, tuning.frost * .6f);
        }
        sheetGlass = next;
        sheetHeight = height;
        next.setVisibility(View.VISIBLE);
        if (old != next) {
            if (old != null) Anim.to(old, View.ALPHA, 0, 280, Ui.STD, 0).addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (sheetGlass != old) old.setVisibility(View.INVISIBLE);
                }
            });
            Anim.to(next, View.ALPHA, 1, 280, Ui.STD, 0);
        }
        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) viewport.getLayoutParams();
        if (vp.height != height) { vp.height = height; viewport.setLayoutParams(vp); }
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
        removeArmed = false;
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
        previewTuning.clear();
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
        if (ctxSec != null && ctxSec.endsWith(":stremio")) {
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

    /** Host may call this before consuming an unmapped/media/volume key while settings is open. */
    void disarmRemoval() {
        if (!removeArmed) return;
        removeArmed = false;
        Row cur = current();
        if (cur != null) fillRow(cur);
    }

    /** Returns false when Back on the main page should close settings. */
    boolean onKey(int key, boolean repeat) {
        if (key != SELECT) disarmRemoval();
        if (swapping) return true;
        long now = System.currentTimeMillis();
        if ((key == UP || key == DOWN) && repeat && now - lastMove < 110) return true;
        if (key == UP || key == DOWN) lastMove = now;
        List<Row> fr = focRows();
        switch (key) {
            case UP:
                if (sIdx > 0) focus(sIdx - 1, true, repeat);
                else if (!repeat) Anim.bump(drop, 0, -1);
                return true;
            case DOWN:
                if (sIdx < fr.size() - 1) focus(sIdx + 1, true, repeat);
                else if (!repeat) Anim.bump(drop, 0, 1);
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
        boolean dep, below, gap;
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
        TextView label, valText;
        ImageView lead, chevL, chevR, go, ext;
        FrameLayout num;
        TextView[] numTx;
        int numCur;
        FrameLayout val, lab;
        SettingsMark mark;
        Switch sw;
        View dot, track, fill;
        Thumb thumb;
        ImageView check;

        Row(String kind) {
            this.kind = kind;
        }
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
            for (Apps.App a : installedApps()) {
                Row r = row("pick", "skip");
                r.app = a;
                m.add(r);
            }
            if (m.isEmpty()) m.add(row("none", "skip"));
            return m;
        }
        if (DOCK.equals(pg)) {
            for (int i = 0; i < dock.size(); i++) {
                Row r = row("dockapp", "dock-page");
                r.dockIndex = i;
                m.add(r);
            }
            Row add = row(dock.size() < Apps.DOCK_MAX ? "dockadd" : "dockfull", "dock-page"); add.gap = true; m.add(add);
            return m;
        }
        if (DOCK_ADD.equals(pg)) {
            for (Apps.App a : Apps.launchable(ctx)) {
                if (dock.contains(a.pkg)) continue;
                Row r = row("dockpick", "dock-add");
                r.app = a;
                m.add(r);
            }
            if (m.isEmpty()) m.add(row("none", "dock-add"));
            return m;
        }
        if (pg.startsWith(BUTTON)) {
            String key = pg.substring(BUTTON.length());
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
                r.gap = m.size() == 1;
                m.add(r);
            }
            return m;
        }
        if (WALLS.equals(pg)) {
            for (int i = 0; i < WallpaperStyle.IDS.length; i++) {
                Row r = row("wall", "wall-" + WallpaperStyle.IDS[i]);
                r.style = i;
                m.add(r);
            }
            return m;
        }
        if (SKIP.equals(pg)) {
            for (int i = 0; i < skip.size(); i++) {
                Row r = row("skip", "skip");
                r.skipIndex = i;
                m.add(r);
            }
            Row add = row("add", "skip"); add.gap = true; m.add(add);
            return m;
        }
        if (TINT.equals(pg)) {
            m.add(row("enabled", "tint"));
            for (String k : new String[]{"start", "full", "off", "strength"}) {
                Row r = row(k, "tint"); r.dep = true; r.gap = "start".equals(k); m.add(r);
            }
            Row add = row("skiplist", "skip"); add.below = true; add.gap = true; m.add(add);
            return m;
        }
        if (REMOTE.equals(pg)) {
            for (String key : new String[]{Prefs.BUTTON_NETFLIX, Prefs.BUTTON_PRIME_VIDEO}) {
                Row r = row("button", "buttons"); r.button = key; m.add(r);
            }
            Row music = row("music", "buttons"); music.gap = true; m.add(music);
            return m;
        }
        m.add(row("dockrow", "dock"));
        m.add(row("walls", "wall"));
        m.add(row("tintrow", "tint"));
        m.add(row("remoterow", "buttons"));
        m.add(row("account", "stremio"));
        Row fire = row("firetv", "firetv"); fire.gap = true; m.add(fire);
        return m;
    }

    private void renderRows() {
        ++fold;
        for (Row r : rows) Anim.cancelAll(r.view);
        rowsBox.removeAllViews();
        rows.clear();
        rows.addAll(model());
        collapsing = false;
        depsGone = !enabled;
        for (Row r : rows) {
            r.view = buildRow(r);
            if (r.dep && !enabled) r.view.setVisibility(View.GONE);
            rowsBox.addView(r.view, Ui.at(0, 0, SHEET_W, r.height));
        }
        layoutRows();
        sIdx = Math.max(0, Math.min(sIdx, focRows().size() - 1));
    }

    /** Places rows top to bottom. The layout only changes here: at render, and once at the end of a fold. */
    private void layoutRows() {
        int y = 12;
        for (Row r : rows) {
            if (r.view.getVisibility() == View.GONE) continue;
            if (r.gap && y > 12) y += 32;
            r.top = y;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) r.view.getLayoutParams();
            lp.topMargin = y;
            lp.height = r.height;
            r.view.setLayoutParams(lp);
            y += r.height;
        }
        contentH = y + 12;
        int h = contentH;
        if (h > SHEET_H) {
            int inner = 0;
            for (Row r : rows) if (r.top + r.height - 12 <= SHEET_H - 24) inner = r.top + r.height - 12;
            h = inner + 24;
        }
        sizeSheet(h);
        int dropHeight = WALLS.equals(pages.get(pages.size() - 1)) ? 148 : ROW_H;
        for (View v : new View[]{drop, dropBump}) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            if (lp.height != dropHeight) { lp.height = dropHeight; v.setLayoutParams(lp); }
        }
    }

    private int contentH;

    private List<Row> focRows() {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (!(r.dep && !enabled)) out.add(r);
        return out;
    }

    private Row current() {
        List<Row> fr = focRows();
        return sIdx < fr.size() ? fr.get(sIdx) : null;
    }

    private int groupH() {
        int n = 0;
        for (Row r : rows) if (r.dep) n++;
        return n * ROW_H + (n > 0 ? 32 : 0);
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
        for (Row x : rows) setFocused(x, x == r);
        float y = logicalTop(r);
        float prevY = Float.isNaN(dropY) ? y : dropY;
        dropY = y;
        if (animate && prevY != y && !fast) {
            float cur = dropBump.getTranslationY();
            float[] f = {0, .4f, 1};
            Anim.keys(dropBump, View.TRANSLATION_Y, f, new float[]{cur, cur + (y - cur) * .55f, y}, 300, Ui.DROP, 0);
            Anim.keys(dropBump, View.SCALE_X, f, new float[]{1, .985f, 1}, 300, Ui.DROP, 0);
            Anim.keys(dropBump, View.SCALE_Y, f, new float[]{1, 1.12f, 1}, 300, Ui.DROP, 0);
        } else if (animate) {
            Anim.to(dropBump, View.TRANSLATION_Y, y, 150, Ui.HELD, 0);
            Anim.to(dropBump, View.SCALE_X, 1, 150, Ui.HELD, 0);
            Anim.to(dropBump, View.SCALE_Y, 1, 150, Ui.HELD, 0);
        } else {
            Anim.set(dropBump, View.TRANSLATION_Y, y);
            Anim.set(dropBump, View.SCALE_X, 1);
            Anim.set(dropBump, View.SCALE_Y, 1);
        }
        scrollTo(scrollFor(y), animate, fast);
        setContext(r.section, animate);
        boolean wantPreview = "strength".equals(r.kind);
        if (wantPreview || previewing) host.previewTint(wantPreview, strength);
        previewing = wantPreview;
        if (ctxGraph != null) ctxGraph.setMarker(r.kind);
    }

    private float scrollFor(float y) {
        if (logicalContentH() <= sheetHeight) return 0;
        List<Row> visible = focRows();
        int i = Math.max(0, Math.min(sIdx, visible.size() - 1));
        Row next = visible.get(Math.min(visible.size() - 1, i + 1));
        int need = next.top + next.height - (sheetHeight - 12);
        int offset = 0;
        if (need > 0) for (Row r : visible) if (r.top - 12 >= need) { offset = r.top - 12; break; }
        return -offset;
    }

    private void scrollTo(float target, boolean animate, boolean fast) {
        scrollTarget = target;
        if (animate) Anim.to(scroll, View.TRANSLATION_Y, target, fast ? 180 : 420, fast ? Ui.HELD : Ui.STD, 0);
        else Anim.set(scroll, View.TRANSLATION_Y, target);
        // Soft fade at whichever edge has content clipped beyond it.
        viewport.setFades(target < -1, target > sheetHeight - logicalContentH() + 1, animate);
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
        positionStepper(r);
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
            case "tintrow": pushPage(TINT); break;
            case "remoterow": pushPage(REMOTE); break;
            case "dockapp":
                if (!removeArmed) { removeArmed = true; fillRow(r); }
                else { removeArmed = false; removeDockApp(r); }
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
                r.check.setAlpha(a.on ? 1f : 0f);
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
        int next = sIdx + dir;
        renderRows();
        ctxSec = null;
        sIdx = next;
        focus(next, true, false);
    }

    /** Select takes the app off the dock; the last one stays, since the dock is never empty. */
    private void removeDockApp(Row r) {
        if (dock.size() <= 1) {
            fillRow(r);
            Anim.press(r.lab, 1, .04f);
            return;
        }
        dock.remove(r.dockIndex);
        saveDock();
        int idx = sIdx;
        renderRows();
        ctxSec = null;
        focus(idx, true, false);
    }

    private void toggleMusic(Row r, int dir) {
        prefs.setMusicTint(!prefs.musicTint());
        r.sw.set(prefs.musicTint(), true);
        ctxSec = null; setContext("buttons", true);
    }

    private static String buttonName(String key) {
        return Prefs.BUTTON_NETFLIX.equals(key) ? "Netflix" : "Prime Video";
    }

    private String buttonValue(String key) {
        String pkg = prefs.buttonTarget(key);
        return pkg == null ? buttonName(key) : Apps.label(ctx, pkg);
    }

    private String dockValue() { return dock.size() + (dock.size() == 1 ? " app" : " apps"); }

    private String remoteValue() {
        return prefs.buttonTarget(Prefs.BUTTON_NETFLIX) == null && prefs.buttonTarget(Prefs.BUTTON_PRIME_VIDEO) == null
                ? "Default" : buttonValue(Prefs.BUTTON_NETFLIX) + ", " + buttonValue(Prefs.BUTTON_PRIME_VIDEO);
    }

    private String buttonsBody() {
        return "The Netflix and Prime Video buttons can open any app instead. "
                + (prefs.musicTint() ? "The Amazon Music button turns the bedtime tint on or off."
                : "Amazon Music opens as usual; fast-forward on home turns the tint on or off.");
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
        SettingsThumbnails.load(ctx, keys, (i, b) -> {
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
        final int gh = deps.size() * ROW_H + 32;
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
        ctxSec = null;
        setContext("tint", true);
    }

    // ------------------------------------------------------------------ pages

    private void pushPage(String pg) {
        removeArmed = false;
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
                if (focusOn == null && WALLS.equals(pages.get(pages.size() - 1))) {
                    for (int j = 0; j < fr.size(); j++) {
                        if (fr.get(j).style == style) { idx = j; break; }
                    }
                }
                if (focusOn == null && pages.get(pages.size() - 1).startsWith(BUTTON)) {
                    for (int j = 0; j < fr.size(); j++) {
                        Row choice = fr.get(j);
                        String target = prefs.buttonTarget(choice.button);
                        if (choice.app == null ? target == null : choice.app.pkg.equals(target)) { idx = j; break; }
                    }
                }
                ctxSec = null;
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

    private String pageId() { return pages.get(pages.size() - 1); }

    private Bitmap[] marks(String pkg) { return Marks.row(ctx, pkg); }

    private void setContext(String sec, boolean animate) {
        Row cur = current();
        String pg = pageId();
        String key = pg + ":" + sec + ((DOCK.equals(pg) || DOCK_ADD.equals(pg)) ? ":" + sIdx : "");
        if (key.equals(ctxSec)) { if (ctxGraph != null) ctxGraph.setMarker(cur.kind); return; }
        ctxSec = key;
        final View old = ctxView;
        FrameLayout v = new FrameLayout(ctx);
        v.setClipChildren(false);
        String title = "Settings", eyebrow = "Fresco";
        if (!MAIN.equals(pg)) {
            eyebrow = "Settings";
            if (DOCK.equals(pg)) title = "Dock";
            else if (DOCK_ADD.equals(pg)) { title = "Choose an app"; eyebrow = "Dock"; }
            else if (WALLS.equals(pg)) title = "Wallpapers";
            else if (TINT.equals(pg)) title = "Bedtime tint";
            else if (REMOTE.equals(pg)) title = "Remote buttons";
            else if (SKIP.equals(pg)) { title = "Skip the tint in"; eyebrow = "Bedtime tint"; }
            else if (PICK.equals(pg)) { title = "Choose an app"; eyebrow = "Skip the tint in"; }
            else { title = buttonName(pg.substring(BUTTON.length())) + " button"; eyebrow = "Remote buttons"; }
        }
        TextView e = SettingsType.text(ctx, eyebrow, Ui.text600, 26, Ui.white(.86f));
        float eb = 80 + SettingsType.cap(e.getPaint());
        SettingsType.baseline(v, e, 0, eb, 600, true);
        if (!MAIN.equals(pg)) Ui.add(v, glyph("left"), -24, Math.round(eb - SettingsType.cap(e.getPaint()) / 2 - 12), 24, 24).setColorFilter(Ui.white(.86f), PorterDuff.Mode.SRC_IN);
        TextView h = SettingsType.text(ctx, title, Ui.display400, 60, Ui.INK);
        h.setLetterSpacing(-1f / 60f);
        float tb = eb + 24 + SettingsType.cap(h.getPaint());
        SettingsType.baseline(v, h, 0, tb, 660, true);
        String item = null, body;
        ctxGraph = null;
        boolean graph = TINT.equals(pg) || (MAIN.equals(pg) && "tint".equals(sec));
        boolean preview = "dock".equals(sec) || DOCK.equals(pg) || DOCK_ADD.equals(pg);
        if (DOCK.equals(pg)) body = "The dock shows these apps from left to right. Left and Right move the focused app along the dock; Select takes it off.";
        else if (DOCK_ADD.equals(pg)) body = "The app you choose goes at the right-hand end of the dock. The dock holds up to four.";
        else if (TINT.equals(pg)) body = tintBody();
        else if (pg.startsWith(BUTTON)) {
            String n = buttonName(pg.substring(BUTTON.length()));
            body = "Choose what the " + n + " button opens. By default it opens " + n + ".";
        } else if ("dock".equals(sec)) { item = "Dock"; body = "One to four apps sit in the glass dock on home. Home opens on the one you used last."; }
        else if ("wall".equals(sec)) { item = "Wallpapers"; body = "The picture behind home follows the time of day, from morning to night. Choose photographs, paintings, abstract colour or space, or let it pick."; }
        else if (sec.startsWith("wall-")) { int i = WallpaperStyle.fromId(sec.substring(5)); item = WallpaperStyle.NAMES[i]; body = styleBody(i); }
        else if ("tint".equals(sec)) { if (MAIN.equals(pg)) item = "Bedtime tint"; body = tintBody(); }
        else if ("buttons".equals(sec)) { if (MAIN.equals(pg)) item = "Remote buttons"; body = buttonsBody(); }
        else if ("stremio".equals(sec)) { item = "Stremio"; body = stremio.signedIn() ? "Continue watching refreshes in the background, so home never waits for the internet." : "Not signed in. Sign in from a computer: see the README. Only the login token is kept on the stick."; }
        else if ("firetv".equals(sec)) { item = "Fire TV settings"; body = "Amazon's own settings: display and sound, network, apps, remotes and devices. They open outside the launcher; Back brings you straight back here."; }
        else { body = "Copy-protected apps black out their video under anything drawn over it, so the tint steps aside while one is open and comes back after."; }
        float y = tb + 48;
        TextView paragraph = SettingsType.text(ctx, body, Ui.text400, 26, Ui.white(.86f));
        float first = y + SettingsType.cap(paragraph.getPaint());
        if (item != null) {
            TextView it = SettingsType.text(ctx, item, Ui.text500, 32, Ui.INK);
            float ib = y + SettingsType.cap(it.getPaint());
            SettingsType.baseline(v, it, 0, ib, 600, true);
            Rect ascenders = new Rect(); paragraph.getPaint().getTextBounds("bdfhkl", 0, 6, ascenders);
            first = ib + 16 + Math.max(-ascenders.top, SettingsType.cap(paragraph.getPaint()));
        }
        paragraph.setSingleLine(false);
        paragraph.setMaxLines(3);
        paragraph.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
        paragraph.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        Ui.lineHeight(paragraph, 38);
        SettingsType.baseline(v, paragraph, 0, first, 600, true);
        ctxBody = paragraph;
        float extraY = first + (Math.max(1, paragraph.getLineCount()) - 1) * 38 + 32;
        if (graph) {
            ctxGraph = Ui.add(v, new Graph(ctx), 0, Math.round(extraY), 600, 264);
            ctxGraph.setMarker(cur == null ? null : cur.kind);
        }
        if (preview) {
            ArrayList<String> apps = new ArrayList<>(dock);
            int focused = DOCK.equals(pg) && cur != null ? cur.dockIndex : -1;
            if (DOCK_ADD.equals(pg) && cur != null && cur.app != null) { focused = apps.size(); apps.add(cur.app.pkg); }
            addPreview(v, Math.round(extraY), apps, focused);
        }
        context.addView(v, Ui.at(0, 0, 600, 1000));
        ctxView = v;
        if (old != null) {
            if (animate) Anim.to(old, View.ALPHA, 0, 120, Ui.IN, 0).addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { context.removeView(old); }
            });
            else context.removeView(old);
        }
        if (animate) { Anim.fromTo(v, View.ALPHA, 0, 1, 280, Ui.OUT, 80); Anim.fromTo(v, View.TRANSLATION_Y, 10, 0, 280, Ui.OUT, 80); }
    }

    private void addPreview(FrameLayout parent, int y, List<String> apps, int focused) {
        int width = apps.size() * 72 + 8;
        FrameLayout frame = Ui.add(parent, new FrameLayout(ctx), 0, y, width, 80);
        frame.setClipChildren(false);
        GlassView g = Ui.add(frame, new GlassView(ctx, 24), 0, 0, width, 80);
        g.lensMag = 1.16f; g.setLensBand(4); g.setShadow(6, 15, .26f, 1, 2, .10f);
        float previewMilk = .7f;
        if (tuning != null) {
            Wallpapers.Frame wallpaper = wallpapers.current();
            float[] material = previewTuning.at(wallpaper == null ? null : wallpaper.blur, tuning, 120, y, width);
            g.setTint(material[0], tuning.frost);
            previewMilk = material[1];
        }
        if (focused >= 0) {
            GlassView milk = Ui.add(frame, new GlassView(ctx, 16), 8 + focused * 72, 8, 64, 64);
            milk.mag = 1.10f; milk.glint = true; milk.setShadow(3, 8, .2f);
            milk.setMilk(previewMilk, 1);
        }
        for (int i = 0; i < apps.size(); i++) Ui.add(frame, new SettingsMark(ctx, marks(apps.get(i)), 1f), 16 + i * 72, 16, 48, 48).setFocusedMark(i == focused);
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
        return b;
    }

    private String tintBody() {
        String button = prefs.musicTint() ? "The Amazon Music button" : "Fast-forward on home";
        String base = enabled ? "Dims and warms every app at night. " + button + " turns it on or off; a second press goes back to the schedule."
                : "Off in the evenings. " + button + " still turns it on until " + Ui.hhmm(prefs.tintOff()) + ".";
        return base;
    }

    // ------------------------------------------------------------------ building rows

    private ImageView glyph(String name) {
        ImageView v = new ImageView(ctx); v.setImageDrawable(new SettingsGlyph(name)); return v;
    }

    private void label(Row r, String text, int x) {
        r.label = SettingsType.text(ctx, text, Ui.text500, 32, Ui.INK);
        int max = "music".equals(r.kind) ? 800 : 620;
        r.label.setMaxWidth(max);
        r.label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        SettingsType.centred(r.lab, r.label, x, max, r.height);
    }

    private TextView valueText(Row r, String text) {
        TextView t = SettingsType.text(ctx, text, Ui.text400, 32, Ui.white(.78f));
        t.setGravity(Gravity.TOP | Gravity.RIGHT);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        boolean destination = "dockrow".equals(r.kind) || "walls".equals(r.kind) || "tintrow".equals(r.kind)
                || "button".equals(r.kind) || "skiplist".equals(r.kind);
        int width = "remoterow".equals(r.kind) ? 530 : destination ? 440 : 600;
        SettingsType.centred(r.val, t, 600 - width, width, r.height);
        return t;
    }

    private String ownPackage(String button) {
        return Prefs.BUTTON_NETFLIX.equals(button) ? FrescoService.NETFLIX_PACKAGE : FrescoService.PRIME_VIDEO_PACKAGE;
    }

    private View buildRow(Row r) {
        r.height = "wall".equals(r.kind) ? 148 : ROW_H;
        FrameLayout f = new FrameLayout(ctx); f.setClipChildren(false);
        r.lab = Ui.add(f, new FrameLayout(ctx), 0, 0, SHEET_W, r.height);
        r.val = Ui.add(f, new FrameLayout(ctx), 312, 0, 640, r.height);
        r.val.setClipChildren(false);
        String text = "", pkg = null;
        int lx = 48;
        switch (r.kind) {
            case "dockapp": pkg = dock.get(r.dockIndex); text = Apps.label(ctx, pkg); break;
            case "button": pkg = ownPackage(r.button); text = buttonName(r.button) + " button"; break;
            case "music": pkg = "com.amazon.bueller.music"; text = "Amazon Music button toggles the tint"; break;
            case "skip": pkg = skip.get(r.skipIndex).pkg; text = skip.get(r.skipIndex).name; break;
            case "choice": pkg = r.app == null ? ownPackage(r.button) : r.app.pkg; text = r.app == null ? buttonName(r.button) : r.app.name; break;
            case "dockpick": case "pick": pkg = r.app.pkg; text = r.app.name; break;
            case "dockrow": text = "Dock"; break;
            case "walls": text = "Wallpapers"; break;
            case "tintrow": text = "Bedtime tint"; break;
            case "remoterow": text = "Remote buttons"; break;
            case "account": text = "Stremio"; break;
            case "firetv": text = "Fire TV settings"; break;
            case "enabled": text = "Every evening"; break;
            case "start": text = "Starts fading in"; break;
            case "full": text = "Full strength by"; break;
            case "off": text = "Turns off"; break;
            case "strength": text = "Strength"; break;
            case "skiplist": text = "Skip the tint in"; break;
            case "add": case "dockadd":
                text = "Add an app"; r.lead = Ui.add(r.lab, glyph("plus"), 56, (r.height - 32) / 2, 32, 32); lx = 120; break;
            case "dockfull": text = "The dock holds four apps"; break;
            case "wall":
                text = WallpaperStyle.NAMES[r.style];
                r.thumb = Ui.add(r.lab, new Thumb(ctx), 28, 16, THUMB_W, THUMB_H); setThumb(r); lx = 260; break;
            default: text = "dock-add".equals(r.section) ? "Every app is already in the dock" : "Every installed app is already on the list";
        }
        if (pkg != null) { r.mark = Ui.add(r.lab, new SettingsMark(ctx, marks(pkg)), 48, (r.height - 48) / 2, 48, 48); lx = 120; }
        label(r, text, lx);
        switch (r.kind) {
            case "enabled": case "music":
                r.sw = Ui.add(r.val, new Switch(ctx, "enabled".equals(r.kind)), 564, (r.height - 42) / 2, 76, 42);
                r.sw.set("enabled".equals(r.kind) ? enabled : prefs.musicTint(), false); break;
            case "start": case "full": case "off": case "strength": case "dockapp":
                chevrons(r);
                if ("dockapp".equals(r.kind)) { r.valText = valueText(r, "Press again to remove"); r.valText.setVisibility(View.GONE); }
                break;
            case "wall": case "choice": case "skip":
                r.check = Ui.add(r.val, glyph("check"), 616, (r.height - 24) / 2, 24, 24);
                if ("wall".equals(r.kind)) r.check.setAlpha(r.style == style ? 1f : 0f);
                else if ("skip".equals(r.kind)) { r.valText = valueText(r, ""); r.check.setAlpha(skip.get(r.skipIndex).on ? 1f : 0f); }
                else { String target = prefs.buttonTarget(r.button); r.check.setAlpha((r.app == null ? target == null : r.app.pkg.equals(target)) ? 1f : 0f); if (r.app == null) r.valText = valueText(r, "Default"); }
                break;
            case "dockpick": case "pick": r.go = Ui.add(r.val, glyph("plus"), 616, (r.height - 24) / 2, 24, 24); break;
            case "dockrow": case "walls": case "tintrow": case "remoterow": case "button": case "skiplist":
                r.valText = valueText(r, "");
                r.go = Ui.add(r.val, glyph("right"), 616, (r.height - 24) / 2, 24, 24); break;
            case "firetv": r.valText = valueText(r, "Amazon's own"); r.ext = Ui.add(r.val, glyph("ext"), 616, (r.height - 24) / 2, 24, 24); break;
            case "account": r.valText = valueText(r, stremio.signedIn() ? "Connected" : "Not signed in"); r.dot = Ui.add(r.val, new View(ctx), 0, (r.height - 12) / 2, 12, 12); break;
        }
        r.view = f; fillRow(r); return f;
    }

    private void chevrons(Row r) {
        r.num = Ui.add(r.val, new FrameLayout(ctx), 0, 0, 600, r.height);
        r.num.setClipChildren(true);
        r.numTx = new TextView[2];
        for (int i = 0; i < 2; i++) {
            TextView t = SettingsType.text(ctx, numText(r), Ui.text400, 32, Ui.white(.78f));
            t.setGravity(Gravity.TOP | Gravity.RIGHT);
            SettingsType.centred(r.num, t, 0, 600, r.height); t.setAlpha(i == 0 ? 1f : 0f); r.numTx[i] = t;
        }
        r.chevL = Ui.add(r.val, glyph("left"), 0, (r.height - 24) / 2, 24, 24);
        r.chevR = Ui.add(r.val, glyph("right"), 616, (r.height - 24) / 2, 24, 24);
        if ("strength".equals(r.kind)) {
            FrameLayout track = Ui.add(r.val, new FrameLayout(ctx), 0, (r.height - 10) / 2, 320, 10);
            track.setOutlineProvider(round(5)); track.setClipToOutline(true); r.track = track;
            r.fill = Ui.add(track, new View(ctx), 0, 0, 320, 10); r.fill.setPivotX(0); r.fill.setScaleX(strength / 100f);
        }
        positionStepper(r);
    }

    private void positionStepper(Row r) {
        Paint p = r.numTx[r.numCur].getPaint(); String value = numText(r);
        Rect bounds = new Rect(); p.getTextBounds(value, 0, 1, bounds);
        float x = 600 - p.measureText(value) + bounds.left - 16 - 24;
        r.chevL.setTranslationX(x);
        if (r.track != null) r.track.setTranslationX(x - 16 - 320);
    }

    private static ViewOutlineProvider round(final float r) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        };
    }

    /** Row ink flips to dark when the droplet arrives: one redraw of that row, not an animation. */
    private void setFocused(Row r, boolean f) {
        if (r.focused == f) return;
        r.focused = f;
        fillRow(r);
    }

    private void fillRow(Row r) {
        boolean f = r.focused;
        int ink = f ? Ui.INK_DARK : Ui.INK;
        int secondary = Ui.withAlpha(ink, f ? .70f : .78f);
        r.label.setTextColor(ink);
        r.label.setAlpha("none".equals(r.kind) || "dockfull".equals(r.kind) ? (f ? .70f : .62f) : 1);
        if (r.mark != null) r.mark.setFocusedMark(f);
        if (r.lead != null) r.lead.setColorFilter(ink);
        if (r.valText != null) r.valText.setTextColor(secondary);
        if (r.numTx != null) for (TextView t : r.numTx) t.setTextColor(secondary);
        if (r.chevL != null) {
            r.chevL.setColorFilter(secondary, PorterDuff.Mode.SRC_IN); r.chevR.setColorFilter(secondary, PorterDuff.Mode.SRC_IN);
            boolean show = f && !("dockapp".equals(r.kind) && removeArmed);
            r.chevL.setAlpha(show ? 1f : 0f); r.chevR.setAlpha(show ? 1f : 0f);
        }
        if ("dockapp".equals(r.kind)) {
            boolean armed = f && removeArmed;
            r.valText.setVisibility(armed ? View.VISIBLE : View.GONE); r.valText.setTextColor(ink);
            r.num.setVisibility(armed ? View.INVISIBLE : View.VISIBLE);
            if (!armed) { setNumText(r, numText(r)); positionStepper(r); }
        }
        if (r.check != null) r.check.setColorFilter(ink);
        if (r.go != null) r.go.setColorFilter(secondary, PorterDuff.Mode.SRC_IN);
        if (r.ext != null) r.ext.setColorFilter(secondary, PorterDuff.Mode.SRC_IN);
        if (r.sw != null) r.sw.setInk(f);
        if (r.track != null) {
            r.track.setBackground(Switch.pill(Ui.withAlpha(ink, .24f), 5));
            r.fill.setBackground(Switch.pill(f ? 0xFFC26A12 : Ui.AMBER, 5));
        }
        if ("account".equals(r.kind)) {
            GradientDrawable dot = new GradientDrawable(); dot.setShape(GradientDrawable.OVAL);
            dot.setColor(secondary);
            r.dot.setBackground(stremio.signedIn() ? dot : new SettingsGlyph("dot", secondary));
            r.valText.setText(stremio.signedIn() ? "Connected" : "Not signed in");
            r.dot.setTranslationX(600 - r.valText.getPaint().measureText(r.valText.getText().toString()) - 24);
        }
        if ("skiplist".equals(r.kind)) {
            int n = 0; for (Prefs.SkipApp a : skip) if (a.on) n++;
            r.valText.setText(n == skip.size() ? n + (n == 1 ? " app" : " apps") : n + " of " + skip.size() + " apps");
        }
        if ("skip".equals(r.kind)) r.valText.setText(skip.get(r.skipIndex).on ? "Skipped" : "Tinted");
        if ("walls".equals(r.kind)) r.valText.setText(WallpaperStyle.NAMES[style]);
        if ("dockrow".equals(r.kind)) r.valText.setText(dockValue());
        if ("button".equals(r.kind)) r.valText.setText(buttonValue(r.button));
        if ("tintrow".equals(r.kind)) r.valText.setText(enabled ? Ui.hhmm(start) + " – " + Ui.hhmm(off) : "Off");
        if ("remoterow".equals(r.kind)) r.valText.setText(remoteValue());
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
            setOutlineProvider(round(20));
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
            int gap = 8, cw = (w - gap) / 2, ch = (h - gap) / 2;
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
            trackOff.setBackground(pill(Ui.withAlpha(c, .24f), 21));
            trackOn.setBackground(pill(amber ? (focused ? 0xFFC26A12 : Ui.AMBER) : 0xFF000000 | c, 21));
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
     * The schedule graph: 17:11 → 10:11. The amber area ramps from start to full, holds at the chosen
     * strength and drops at off. The focused time shows as a dashed marker; a tick marks now.
     * Redraws only when a value or the focused row changes.
     */
    private final class Graph extends View {
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
            final float W = 600, labelBase = SettingsType.cap(labelPaint()), top = labelBase + 12;
            final float base = top + 150, a0 = 17 * 60 + 11, a1 = a0 + 17 * 60;
            float s = ev(start), f = Math.max(ev(full), s), o = off + 1440;
            float yTop = base - 150 * strength / 100f;
            path.reset(); path.moveTo(0, base);
            path.lineTo(x(s, a0, a1, W), base); path.lineTo(x(f, a0, a1, W), yTop);
            path.lineTo(x(o, a0, a1, W), yTop); path.lineTo(Math.min(W, x(o, a0, a1, W) + 8), base); path.lineTo(W, base);
            line.setStyle(Paint.Style.STROKE); line.setPathEffect(null); line.setStrokeWidth(2); line.setColor(Ui.white(.24f));
            c.drawLine(0, base, W, base, line);
            if (enabled) {
                area.setShader(new LinearGradient(0, yTop, 0, base, Ui.withAlpha(Ui.AMBER, .55f), Ui.withAlpha(Ui.AMBER, .04f), Shader.TileMode.CLAMP));
                Path filled = new Path(path); filled.close(); c.drawPath(filled, area);
            }
            line.setStrokeWidth(3); line.setColor(enabled ? Ui.AMBER : Ui.white(.24f)); c.drawPath(path, line);
            text.setTypeface(Ui.text500); text.setTextSize(22); text.setColor(Ui.white(.86f));
            line.setStrokeWidth(2); line.setColor(Ui.white(.24f));
            for (int h = 18; h <= 33; h += 3) {
                float tx = x(h * 60, a0, a1, W);
                c.drawText(Ui.hhmm(h * 60), tx, base + 40, text); c.drawLine(tx, base + 4, tx, base + 12, line);
            }
            float now = ev(host.nowMinutes());
            if (now >= a0 && now <= a1) {
                float nx = x(now, a0, a1, W); line.setStrokeWidth(3); line.setColor(Ui.INK);
                c.drawLine(nx, base - 6, nx, base + 6, line);
                // Keep the small "now" run inside the same 600 px measure.
                float half = text.measureText("now") / 2; c.drawText("now", Math.max(half, Math.min(W - half, nx)), base + 72, text);
            }
            if (marker != null) {
                float m = "start".equals(marker) ? s : "full".equals(marker) ? f : o, mx = x(m, a0, a1, W);
                line.setStrokeWidth(2); line.setColor(Ui.INK); line.setPathEffect(new DashPathEffect(new float[]{3, 6}, 0));
                c.drawLine(mx, top, mx, base, line); line.setPathEffect(null);
                area.setShader(null); area.setColor(Ui.INK); c.drawCircle(mx, "start".equals(marker) ? base : yTop, 7, area);
                text.setTextSize(26); text.setColor(Ui.INK);
                String label = Ui.hhmm((int) m); float half = text.measureText(label) / 2;
                c.drawText(label, Math.max(half, Math.min(W - half, mx)), labelBase, text);
            }
        }

        private Paint labelPaint() { text.setTypeface(Ui.text500); text.setTextSize(26); return text; }

        private float x(float m, float a0, float a1, float w) {
            return Math.max(0, Math.min(w, (m - a0) / (a1 - a0) * w));
        }
    }
}
