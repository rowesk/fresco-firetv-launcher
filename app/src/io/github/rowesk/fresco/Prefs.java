package io.github.rowesk.fresco;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Every setting lives in one SharedPreferences file, shared_prefs/fresco.xml, so it can be
 * copied off the stick with run-as. The launcher and the service share this process, so they
 * see each other's changes at once; the service listens for them.
 */
final class Prefs {
    static final String FILE = "fresco";

    static final String TINT_ENABLED = "tint_enabled";
    static final String TINT_START = "tint_start";
    static final String TINT_FULL = "tint_full";
    static final String TINT_OFF = "tint_off";
    static final String TINT_STRENGTH = "tint_strength";
    static final String SKIP_APPS = "skip_apps";
    /** Music-button override: 0 none, 1 on until `tint_manual_until`, -1 off until then. */
    static final String TINT_MANUAL = "tint_manual";
    static final String TINT_MANUAL_UNTIL = "tint_manual_until";
    static final String STREMIO_AUTH = "stremio_auth_key";
    static final String STREMIO_SYNCED_AT = "stremio_synced_at";
    /** Package name of the dock app launched last. */
    static final String LAST_APP = "last_dock_app";
    /** One of WallpaperStyle.IDS. */
    static final String WALLPAPER_STYLE = "wallpaper_style";
    /** The dock's apps in order, as comma-separated package names; absent until the first default is saved. */
    static final String DOCK_APPS = "dock_apps";
    /** The package the remote's Netflix / Prime Video button opens instead; absent or empty leaves the button unchanged. */
    static final String BUTTON_NETFLIX = "button_netflix";
    static final String BUTTON_PRIME_VIDEO = "button_prime_video";
    /** True: the Amazon Music button toggles the bedtime tint. False: it opens Amazon Music as usual. */
    static final String BUTTON_MUSIC_TINT = "button_music_tint";

    static final class SkipApp {
        final String name;
        final String pkg;
        boolean on;

        SkipApp(String name, String pkg, boolean on) {
            this.name = name;
            this.pkg = pkg;
            this.on = on;
        }
    }

    private static Prefs instance;
    final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static synchronized Prefs get(Context context) {
        if (instance == null) instance = new Prefs(context);
        return instance;
    }

    boolean tintEnabled() { return sp.getBoolean(TINT_ENABLED, true); }
    int tintStart() { return sp.getInt(TINT_START, 21 * 60 + 30); }
    int tintFull() { return sp.getInt(TINT_FULL, 22 * 60 + 30); }
    int tintOff() { return sp.getInt(TINT_OFF, 7 * 60); }
    int tintStrength() { return sp.getInt(TINT_STRENGTH, 70); }

    void setTintEnabled(boolean on) { sp.edit().putBoolean(TINT_ENABLED, on).apply(); }

    void setTimes(int start, int full, int off) {
        sp.edit().putInt(TINT_START, start).putInt(TINT_FULL, full).putInt(TINT_OFF, off).apply();
    }

    void setTintStrength(int s) { sp.edit().putInt(TINT_STRENGTH, s).apply(); }

    int tintManual() { return sp.getInt(TINT_MANUAL, 0); }
    long tintManualUntil() { return sp.getLong(TINT_MANUAL_UNTIL, 0); }

    void setTintManual(int mode, long until) {
        sp.edit().putInt(TINT_MANUAL, mode).putLong(TINT_MANUAL_UNTIL, until).apply();
    }

    /** Copy-protected apps black out their video under any overlay, so the tint steps aside for them. */
    List<SkipApp> skipApps() {
        List<SkipApp> out = new ArrayList<>();
        String json = sp.getString(SKIP_APPS, null);
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    out.add(new SkipApp(o.getString("name"), o.getString("pkg"), o.optBoolean("on", true)));
                }
                return out;
            } catch (JSONException e) {
                out.clear();
            }
        }
        out.add(new SkipApp("Prime Video", "com.amazon.firebat", true));
        out.add(new SkipApp("BBC iPlayer", "uk.co.bbc.iplayer", true));
        out.add(new SkipApp("ITVX", "air.ITVMobilePlayer", true));
        out.add(new SkipApp("Channel 4", "com.channel4.ondemand", true));
        out.add(new SkipApp("My5", "com.mobileiq.demand5", true));
        return out;
    }

    void setSkipApps(List<SkipApp> apps) {
        JSONArray a = new JSONArray();
        try {
            for (SkipApp s : apps) a.put(new JSONObject().put("name", s.name).put("pkg", s.pkg).put("on", s.on));
        } catch (JSONException e) {
            return;
        }
        sp.edit().putString(SKIP_APPS, a.toString()).apply();
    }

    boolean isSkipped(String pkg) {
        if (pkg == null) return false;
        for (SkipApp s : skipApps()) if (s.on && s.pkg.equals(pkg)) return true;
        return false;
    }

    String authKey() {
        String k = sp.getString(STREMIO_AUTH, null);
        return k == null || k.isEmpty() ? null : k;
    }

    void setAuthKey(String key) { sp.edit().putString(STREMIO_AUTH, key).apply(); }
    long syncedAt() { return sp.getLong(STREMIO_SYNCED_AT, 0); }
    void setSyncedAt(long t) { sp.edit().putLong(STREMIO_SYNCED_AT, t).apply(); }

    /** Home lands on the last-launched dock app; null before the first launch. */
    String lastApp() { return sp.getString(LAST_APP, null); }
    void setLastApp(String pkg) { sp.edit().putString(LAST_APP, pkg).apply(); }

    /** Null when nothing is saved yet: Apps.dock() then picks and saves the default. */
    List<String> dockApps() {
        String s = sp.getString(DOCK_APPS, null);
        if (s == null) return null;
        List<String> out = new ArrayList<>();
        for (String pkg : s.split(",")) if (!pkg.trim().isEmpty()) out.add(pkg.trim());
        return out;
    }

    void setDockApps(List<String> pkgs) { sp.edit().putString(DOCK_APPS, android.text.TextUtils.join(",", pkgs)).apply(); }

    /** BUTTON_NETFLIX or BUTTON_PRIME_VIDEO: the package it opens instead, or null for unchanged. */
    String buttonTarget(String button) {
        String pkg = sp.getString(button, null);
        return pkg == null || pkg.isEmpty() ? null : pkg;
    }

    void setButtonTarget(String button, String pkg) {
        if (pkg == null) sp.edit().remove(button).apply();
        else sp.edit().putString(button, pkg).apply();
    }

    boolean musicTint() { return sp.getBoolean(BUTTON_MUSIC_TINT, true); }
    void setMusicTint(boolean on) { sp.edit().putBoolean(BUTTON_MUSIC_TINT, on).apply(); }

    String wallpaperStyle() { return sp.getString(WALLPAPER_STYLE, "nature"); }
    void setWallpaperStyle(String id) { sp.edit().putString(WALLPAPER_STYLE, id).apply(); }
}
