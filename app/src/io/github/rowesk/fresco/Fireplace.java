package io.github.rowesk.fresco;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The fireplace: long log-fire videos on YouTube, chosen from a page that opens from a Fireplace button
 * at the end of the dock. YouTube plays them (so Premium, 4K and the length all come from YouTube), and
 * FrescoService brings Back from the fire home to Fresco instead of to YouTube's own home page.
 *
 * The button shows at Christmas (1 December to 6 January) unless settings say always or never.
 */
final class Fireplace {
    private Fireplace() {}

    private static final String TAG = "Fresco";

    /** The dock's Fireplace button stands in the dock's app list under this name. It isn't a package. */
    static final String DOCK_ID = "io.github.rowesk.fresco.fireplace";

    /** Prefs.FIREPLACE_DOCK values. */
    static final int AT_CHRISTMAS = 0, ALWAYS = 1, NEVER = 2;
    static final String[] DOCK_NAMES = {"At Christmas", "Always", "Never"};

    static final class Fire {
        final String name, video, about;

        Fire(String name, String video, String about) {
            this.name = name;
            this.video = video;
            this.about = about;
        }
    }

    /** Eight-hour 4K fires from Relax with TV Backgrounds. */
    static final Fire[] FIRES = {
            new Fire("Crackling logs", "eXtaM4QM4hk", "A log fire with its own quiet crackle, and no music."),
            new Fire("Merry and bright", "iqoFSLXcVMQ", "A log fire with cheerful instrumental Christmas music."),
            new Fire("Traditional carols", "GEh98C8lSzs", "A log fire with traditional carols played by an orchestra."),
            new Fire("Christmas harp", "qOb0aoWH8DA", "A log fire with peaceful Christmas music on the harp."),
    };

    /** 1 December to 6 January: Advent to Twelfth Night. */
    static boolean isChristmas(Calendar now) {
        int month = now.get(Calendar.MONTH);
        return month == Calendar.DECEMBER || (month == Calendar.JANUARY && now.get(Calendar.DAY_OF_MONTH) <= 6);
    }

    /** Whether the dock shows the Fireplace button now. It needs YouTube, which plays the fires. */
    static boolean inDock(Context c) {
        int when = Prefs.get(c).fireplaceDock();
        if (when == NEVER || !Apps.installed(c, FrescoService.YOUTUBE.getPackageName())) return false;
        return when == ALWAYS || isChristmas(Calendar.getInstance());
    }

    static Uri link(Fire f) {
        return Uri.parse("https://www.youtube.com/watch?v=" + f.video);
    }

    /** Opens the fire in YouTube. YouTube for Fire TV ignores a start time, so every fire starts at the beginning. */
    static Intent intent(Fire f) {
        return new Intent(Intent.ACTION_VIEW, link(f)).setPackage(FrescoService.YOUTUBE.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    // ------------------------------------------------------------------ thumbnails

    interface ThumbCallback { void done(int index, Bitmap bitmap); }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    /**
     * A frame from the middle of each video (YouTube's mq2 still, 320×180, without the title the channel
     * prints on its thumbnails), downloaded once into the cache. Null for any that can't be had offline.
     */
    static void loadThumbs(Context c, ThumbCallback callback) {
        final File dir = new File(c.getApplicationContext().getCacheDir(), "fireplace");
        final Handler main = new Handler(Looper.getMainLooper());
        for (int i = 0; i < FIRES.length; i++) {
            final int index = i;
            WORKER.execute(() -> {
                File file = new File(dir, FIRES[index].video + ".jpg");
                if (!file.isFile()) download("https://i.ytimg.com/vi/" + FIRES[index].video + "/mq2.jpg", file);
                Bitmap b = file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
                main.post(() -> callback.done(index, b));
            });
        }
    }

    private static void download(String url, File to) {
        HttpURLConnection http = null;
        File part = new File(to.getPath() + ".part");
        try {
            //noinspection ResultOfMethodCallIgnored
            to.getParentFile().mkdirs();
            http = (HttpURLConnection) new URL(url).openConnection();
            http.setConnectTimeout(8000);
            http.setReadTimeout(8000);
            if (http.getResponseCode() != 200) throw new IOException("HTTP " + http.getResponseCode());
            try (InputStream in = http.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (!part.renameTo(to)) throw new IOException("could not save " + to);
        } catch (IOException e) {
            Log.w(TAG, "fireplace: no thumbnail from " + url + ": " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        } finally {
            if (http != null) http.disconnect();
        }
    }
}
