package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Continue watching, from the Stremio account's library. The API is undocumented; the calls and
 * field names follow stremio-core (src/types/api, src/types/library/library_item.rs):
 *   POST https://api.strem.io/api/datastoreGet  {authKey, collection: "libraryItem", ids: [], all: true}
 *   → {result: [LibraryItem]}  or  {error: {message, code}}
 * If Stremio changes it, the row simply disappears.
 *
 * The list and posters are cached on disk and refreshed in the background, so home never waits on
 * the network. Only the login token is kept on the stick; tools/stremio-login.sh drops it in
 * files/stremio-auth.json and we move it into the settings file.
 */
final class StremioClient {
    private static final String TAG = "Fresco";
    private static final String API = "https://api.strem.io/api/";
    static final long REFRESH_MIN_MS = 5 * 60_000L;
    static final long WINDOW_MS = 7L * 24 * 3600_000L;
    static final int MAX_ITEMS = 10;
    private static final int CACHE_ITEMS = 30;

    enum State { SIGNED_OUT, LOADING, READY, NONE }

    static final class Item {
        String id, type, name, poster, videoId;
        long timeOffset, duration, lastWatched;

        /**
         * `S17 E8` from a series video id like `tt0096697:17:8` (the item id, then season and episode);
         * `E5` where there are no seasons, like Kitsu's `kitsu:123:5`; `Film` for a movie.
         */
        String episode() {
            if ("movie".equals(type)) return "Film";
            if (videoId != null && videoId.startsWith(id + ":")) {
                String[] p = videoId.substring(id.length() + 1).split(":");
                if (p.length == 2 && p[0].matches("\\d+") && p[1].matches("\\d+")) {
                    return "S" + Integer.parseInt(p[0]) + " E" + Integer.parseInt(p[1]);
                }
                if (p.length == 1 && p[0].matches("\\d+")) return "E" + Integer.parseInt(p[0]);
            }
            return "series".equals(type) ? "Series" : "";
        }

        /** Time left, from state.duration − state.timeOffset (both ms). */
        String left() {
            if (duration <= 0) return null;
            long min = Math.max(1, Math.round((duration - timeOffset) / 60000.0));
            if (min < 60) return min + " min left";
            return (min / 60) + " h " + (min % 60) + " min left";
        }

        float progress() {
            return duration > 0 ? Math.max(0f, Math.min(1f, (float) timeOffset / duration)) : 0f;
        }

        /** stremio:///detail/<type>/<id>/<videoId>; a movie's video id is usually its own id. */
        Uri link() {
            String v = videoId != null ? videoId : id;
            return Uri.parse("stremio:///detail/" + Uri.encode(type) + "/" + Uri.encode(id, ":") + "/" + Uri.encode(v, ":"));
        }

        String posterKey() {
            return id.replaceAll("[^A-Za-z0-9._-]", "_");
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("type", type).put("name", name).put("poster", poster)
                    .put("videoId", videoId).put("timeOffset", timeOffset).put("duration", duration)
                    .put("lastWatched", lastWatched);
        }

        static Item fromJson(JSONObject o) {
            Item it = new Item();
            it.id = o.optString("id");
            it.type = o.optString("type");
            it.name = o.optString("name");
            it.poster = str(o, "poster");
            it.videoId = str(o, "videoId");
            it.timeOffset = o.optLong("timeOffset");
            it.duration = o.optLong("duration");
            it.lastWatched = o.optLong("lastWatched");
            return it;
        }
    }

    interface Listener {
        void onStremioChanged();
    }

    private static StremioClient instance;
    private final Context app;
    private final Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final File cacheFile, postersDir, authDrop;
    private List<Item> all = new ArrayList<>();
    private boolean hasCache;
    private boolean syncing;
    private long lastAttempt;
    private Listener listener;

    private StremioClient(Context context) {
        app = context.getApplicationContext();
        prefs = Prefs.get(app);
        cacheFile = new File(app.getFilesDir(), "continue.json");
        postersDir = new File(app.getFilesDir(), "posters");
        authDrop = new File(app.getFilesDir(), "stremio-auth.json");
        readCache();
    }

    static synchronized StremioClient get(Context context) {
        if (instance == null) instance = new StremioClient(context);
        return instance;
    }

    void setListener(Listener l) { listener = l; }
    boolean syncing() { return syncing; }
    boolean signedIn() { return prefs.authKey() != null; }

    State state() {
        if (!signedIn()) return State.SIGNED_OUT;
        if (!items().isEmpty()) return State.READY;
        if (!hasCache && (syncing || lastAttempt == 0)) return State.LOADING;
        return State.NONE;
    }

    /** Titles played in the last 7 days, most recent first (state.lastWatched is per show). */
    List<Item> items() {
        long since = System.currentTimeMillis() - WINDOW_MS;
        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            if (it.lastWatched >= since) out.add(it);
            if (out.size() == MAX_ITEMS) break;
        }
        return out;
    }

    File posterFile(Item it) {
        return new File(postersDir, it.posterKey() + ".img");
    }

    /** On Home arrival (at most every few minutes) or "Sync now" from settings. */
    void refresh(boolean force) {
        importDroppedToken();
        if (!signedIn()) {
            notifyChanged();
            return;
        }
        long now = System.currentTimeMillis();
        if (syncing || (!force && lastAttempt != 0 && now - lastAttempt < REFRESH_MIN_MS)) return;
        syncing = true;
        lastAttempt = now;
        final String key = prefs.authKey();
        notifyChanged();
        io.execute(() -> sync(key));
    }

    /** tools/stremio-login.sh can't safely edit live SharedPreferences, so it leaves the token here. */
    private void importDroppedToken() {
        if (!authDrop.exists()) return;
        try {
            JSONObject o = new JSONObject(readFile(authDrop));
            String key = str(o, "authKey");
            prefs.setAuthKey(key == null ? "" : key);
            if (key == null) {
                all = new ArrayList<>();
                hasCache = false;
                cacheFile.delete();
            }
            lastAttempt = 0;
            Log.i(TAG, key == null ? "stremio: signed out" : "stremio: new token imported");
        } catch (IOException | JSONException e) {
            Log.e(TAG, "stremio: unreadable token drop", e);
        }
        authDrop.delete();
    }

    private void sync(String key) {
        List<Item> fresh = null;
        boolean signedOut = false;
        try {
            JSONObject body = new JSONObject().put("authKey", key).put("collection", "libraryItem")
                    .put("ids", new JSONArray()).put("all", true);
            JSONObject res = post("datastoreGet", body);
            if (res.has("error") && !res.isNull("error")) {
                String msg = String.valueOf(res.opt("error"));
                Log.w(TAG, "stremio: datastoreGet error " + msg);
                signedOut = msg.toLowerCase(Locale.ROOT).contains("session");
            } else {
                fresh = parse(res.getJSONArray("result"));
                writeCache(fresh);
                fetchPosters(fresh);
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "stremio: sync failed: " + e);
        }
        final List<Item> result = fresh;
        final boolean dropKey = signedOut;
        main.post(() -> {
            syncing = false;
            if (dropKey) {
                prefs.setAuthKey("");
            } else if (result != null) {
                all = result;
                hasCache = true;
                prefs.setSyncedAt(System.currentTimeMillis());
            }
            notifyChanged();
        });
    }

    /**
     * Stremio's own Continue watching rule (LibraryItem::is_in_continue_watching plus the !is_live
     * filter in continue_watching_preview.rs): type != "other", not removed unless temp, timeOffset > 0,
     * and not live TV. Stremio orders by _mtime; we order by state.lastWatched, as the spec asks.
     */
    private static List<Item> parse(JSONArray a) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String type = o.optString("type");
            if (type.isEmpty() || "other".equals(type) || "tv".equals(type)) continue;
            if (o.optBoolean("removed") && !o.optBoolean("temp")) continue;
            JSONObject bh = o.optJSONObject("behaviorHints");
            if (bh != null && bh.optBoolean("isLive")) continue;
            JSONObject st = o.optJSONObject("state");
            if (st == null || st.optLong("timeOffset") <= 0) continue;
            long last = parseIso(str(st, "lastWatched"));
            if (last == 0) continue;
            Item it = new Item();
            it.id = o.optString("_id");
            it.type = type;
            it.name = o.optString("name");
            it.poster = str(o, "poster");
            it.videoId = str(st, "video_id");
            it.timeOffset = st.optLong("timeOffset");
            it.duration = st.optLong("duration");
            it.lastWatched = last;
            if (!it.id.isEmpty()) out.add(it);
        }
        out.sort((x, y) -> Long.compare(y.lastWatched, x.lastWatched));
        return out.size() > CACHE_ITEMS ? new ArrayList<>(out.subList(0, CACHE_ITEMS)) : out;
    }

    private void fetchPosters(List<Item> items) {
        postersDir.mkdirs();
        HashSet<String> keep = new HashSet<>();
        long since = System.currentTimeMillis() - WINDOW_MS;
        int n = 0;
        for (Item it : items) {
            if (it.lastWatched < since || n++ >= MAX_ITEMS) break;
            File f = posterFile(it);
            keep.add(f.getName());
            if (f.exists() || it.poster == null) continue;
            String url = it.poster.startsWith("//") ? "https:" + it.poster : it.poster;
            try {
                File tmp = new File(postersDir, f.getName() + ".part");
                download(url, tmp);
                if (!tmp.renameTo(f)) tmp.delete();
            } catch (IOException e) {
                Log.w(TAG, "stremio: poster for " + it.id + " failed: " + e);
            }
        }
        File[] old = postersDir.listFiles();
        if (old != null) for (File f : old) if (!keep.contains(f.getName())) f.delete();
    }

    /**
     * Decoded to 208x312: the 176x264 poster at its focused size (×1.12 lift, ×1.05 artwork zoom), so it
     * stays sharp when lifted. Only that copy is kept, as a hardware bitmap (no second CPU copy).
     */
    static Bitmap decodePoster(File f) {
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 208 && o.outHeight / (sample * 2) >= 312) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        // Centre-crop to 2:3 and scale.
        int w = b.getWidth(), h = b.getHeight();
        int cw = Math.min(w, h * 2 / 3), ch = Math.min(h, w * 3 / 2);
        Bitmap crop = Bitmap.createBitmap(b, (w - cw) / 2, (h - ch) / 2, cw, ch);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, 208, 312, true);
        if (crop != b && crop != scaled) crop.recycle();
        if (b != scaled) b.recycle();
        if (Build.VERSION.SDK_INT >= 26) {
            Bitmap hw = scaled.copy(Bitmap.Config.HARDWARE, false);
            if (hw != null) {
                scaled.recycle();
                return hw;
            }
        }
        return scaled;
    }

    private void readCache() {
        if (!cacheFile.exists()) return;
        try {
            JSONArray a = new JSONObject(readFile(cacheFile)).getJSONArray("items");
            List<Item> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
            all = out;
            hasCache = true;
        } catch (IOException | JSONException e) {
            Log.w(TAG, "stremio: cache unreadable: " + e);
        }
    }

    private void writeCache(List<Item> items) throws JSONException, IOException {
        JSONArray a = new JSONArray();
        for (Item it : items) a.put(it.toJson());
        File tmp = new File(cacheFile.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(new JSONObject().put("items", a).toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(cacheFile)) throw new IOException("could not write " + cacheFile);
    }

    private void notifyChanged() {
        if (listener != null) listener.onStremioChanged();
    }

    // ---- HTTP: HttpURLConnection on our background thread, with timeouts. ----

    private static JSONObject post(String method, JSONObject body) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(API + method).openConnection();
        try {
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code);
            return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String readFile(File f) throws IOException {
        return new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8);
    }

    /** JSON null and "" both mean absent (JSONObject.optString would turn null into "null"). */
    static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return null;
        String s = o.optString(key);
        return s.isEmpty() ? null : s;
    }

    private static final Pattern ISO = Pattern.compile(
            "(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d+))?(Z|[+-]\\d{2}:?\\d{2})?");

    /** chrono's DateTime<Utc> serialises as RFC 3339, e.g. 2026-11-08T21:46:12.345Z. java.time needs API 26. */
    static long parseIso(String s) {
        if (s == null) return 0;
        Matcher m = ISO.matcher(s);
        if (!m.lookingAt()) return 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
        long t = c.getTimeInMillis();
        if (m.group(7) != null) t += Integer.parseInt((m.group(7) + "00").substring(0, 3));
        String z = m.group(8);
        if (z != null && !z.equals("Z")) {
            String d = z.replace(":", "");
            int off = Integer.parseInt(d.substring(1, 3)) * 60 + Integer.parseInt(d.substring(3, 5));
            t -= (z.charAt(0) == '-' ? -off : off) * 60_000L;
        }
        return t;
    }
}
