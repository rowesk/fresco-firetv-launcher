package io.github.rowesk.fresco;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * tools/stremio-login.sh sends this after dropping a new token, so the row fills in without
 * anyone touching the TV. It carries no data; the token arrives through the app's private files.
 */
public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        StremioClient.get(context).refresh(true);
    }
}
