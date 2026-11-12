package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Settings text placement, retaining right alignment while using Ui's cap and optical metrics. */
final class SettingsType {
    private SettingsType() {}
    static float cap(Paint p) { return Ui.capHeight(p); }
    static TextView text(Context c, String text, Typeface face, int size, int ink) {
        TextView v = Ui.text(c, face, size, ink);
        v.setText(text);
        v.setFontFeatureSettings("tnum");
        Ui.lineHeight(v, size == 60 ? 72 : size == 32 ? 40 : size == 26 ? 32 : 28);
        v.setGravity(Gravity.TOP | Gravity.LEFT);
        return v;
    }
    static TextView baseline(FrameLayout parent, TextView v, int x, float base, int width, boolean optical) {
        int gravity = v.getGravity();
        Ui.baseline(parent, v, x, base, width, optical);
        v.setGravity(gravity);
        return v;
    }
    static TextView centred(FrameLayout parent, TextView v, int x, int width, int rowHeight) {
        return baseline(parent, v, x, rowHeight / 2f + cap(v.getPaint()) / 2f, width, false);
    }
}

