package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings-only decoder; four 240×135 copies, never the 8 MB full-size wallpaper. */
final class SettingsThumbnails {
    interface Callback { void done(int index, Bitmap bitmap); }
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private SettingsThumbnails() {}
    static void load(Context c, String[] keys, Callback callback) {
        final android.content.res.AssetManager assets = c.getApplicationContext().getAssets();
        final Handler main = new Handler(Looper.getMainLooper());
        for (int i = 0; i < keys.length; i++) {
            final int index = i;
            WORKER.execute(() -> {
                Bitmap decoded = null;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inScaled = false; o.inSampleSize = 8; o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                for (String ext : new String[]{".webp", ".jpg", ".png"}) {
                    try (InputStream in = assets.open("wallpapers/" + keys[index] + ext)) {
                        decoded = BitmapFactory.decodeStream(in, null, o); if (decoded != null) break;
                    } catch (IOException ignored) { }
                }
                final Bitmap b = decoded;
                main.post(() -> callback.done(index, b));
            });
        }
    }
}
