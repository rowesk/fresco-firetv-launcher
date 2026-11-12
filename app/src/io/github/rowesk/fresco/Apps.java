package io.github.rowesk.fresco;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The other apps on the stick: which can be launched, what they're called, their marks for the dock,
 * and how to open them. Shared by the dock, the settings pickers and the remote-button remaps.
 */
final class Apps {
    private Apps() {}

    /** The dock holds one to this many apps. */
    static final int DOCK_MAX = 4;

    /** A fresh install docks the first two of these that are installed. */
    private static final String[] DOCK_DEFAULTS = {
            "com.stremio.one",
            "com.amazon.firetv.youtube",
            "com.netflix.ninja",
            "com.amazon.firebat",          // Prime Video
            "com.disney.disneyplus",
            "com.plexapp.android",
            "org.jellyfin.androidtv",
            "org.xbmc.kodi",
            "uk.co.bbc.iplayer",
    };

    /** Amazon's own home and settings: never offered as apps. */
    private static final String[] HIDDEN = {
            "com.amazon.tv.launcher",
            "com.amazon.tv.settings",
            "com.amazon.tv.settings.v2",
            "com.android.settings",
    };

    static final class App {
        final String name, pkg;

        App(String name, String pkg) {
            this.name = name;
            this.pkg = pkg;
        }
    }

    /** Every app with a TV launcher entry (or, failing that, a phone one), sorted by name. */
    static List<App> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        HashSet<String> seen = new HashSet<>();
        seen.add(c.getPackageName());
        for (String h : HIDDEN) seen.add(h);
        List<App> out = new ArrayList<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                out.add(new App(String.valueOf(ri.loadLabel(pm)), pkg));
            }
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    static boolean installed(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Stremio and YouTube keep their own names; others use their label, or the package if it's gone. */
    static String label(Context c, String pkg) {
        if (FrescoService.STREMIO.getPackageName().equals(pkg)) return "Stremio";
        if (FrescoService.YOUTUBE.getPackageName().equals(pkg)) return "YouTube";
        PackageManager pm = c.getPackageManager();
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * Opens the app as its launcher tile would: the TV entry, else the phone one. Stremio and YouTube
     * keep the exact screens the launcher has always opened. Null if the app can't be launched.
     */
    static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return null;
        ComponentName known = FrescoService.STREMIO.getPackageName().equals(pkg) ? FrescoService.STREMIO
                : FrescoService.YOUTUBE.getPackageName().equals(pkg) ? FrescoService.YOUTUBE : null;
        if (known != null) i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(known);
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    /** The mono mark (rest) and the colour mark (focus): hand-drawn for Stremio and YouTube, the app's icon otherwise. */
    static Bitmap[] marks(Context c, String pkg) {
        if (FrescoService.STREMIO.getPackageName().equals(pkg)) return new Bitmap[]{Marks.stremio(false), Marks.stremio(true)};
        if (FrescoService.YOUTUBE.getPackageName().equals(pkg)) return new Bitmap[]{Marks.youtube(false), Marks.youtube(true)};
        PackageManager pm = c.getPackageManager();
        Drawable d;
        try {
            d = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            d = pm.getDefaultActivityIcon();
        }
        // Only the two small renders are kept; the icon drawable itself goes with this call.
        return Marks.icon(d);
    }

    /**
     * The dock's apps, in order: the saved choice minus anything since uninstalled. A fresh install
     * saves the default once, so installing an app later never rearranges the dock by itself.
     */
    static List<String> dock(Context c) {
        Prefs prefs = Prefs.get(c);
        List<String> saved = prefs.dockApps();
        if (saved == null) {
            List<String> d = defaultDock(c);
            if (!d.isEmpty()) prefs.setDockApps(d);
            return d;
        }
        List<String> out = new ArrayList<>();
        for (String pkg : saved) {
            if (out.size() < DOCK_MAX && !out.contains(pkg) && launchIntent(c, pkg) != null) out.add(pkg);
        }
        return out.isEmpty() ? defaultDock(c) : out;
    }

    /** The first two installed apps from DOCK_DEFAULTS, topped up from every launchable app. */
    private static List<String> defaultDock(Context c) {
        List<String> out = new ArrayList<>();
        for (String pkg : DOCK_DEFAULTS) {
            if (out.size() < 2 && launchIntent(c, pkg) != null) out.add(pkg);
        }
        if (out.size() < 2) {
            for (App a : launchable(c)) if (out.size() < 2 && !out.contains(a.pkg)) out.add(a.pkg);
        }
        return out;
    }
}
