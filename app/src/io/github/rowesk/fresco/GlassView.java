package io.github.rowesk.fresco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Property;
import android.view.View;

import java.util.ArrayList;

/**
 * A liquid-glass panel (DESIGN.md §6.2). Android 9 has no backdrop blur, and none is needed: the
 * wallpaper is static, so each panel draws the wallpaper's pre-blurred copy through a BitmapShader
 * whose matrix puts the right patch of it under the panel, magnified about the panel's centre.
 *
 * Moving a view doesn't re-run onDraw, so the shader would show the wrong patch while a panel moves.
 * HomeActivity calls {@link #syncAll} before every frame; only panels whose place on the stage
 * changed redraw. When nothing moves, nothing draws.
 */
final class GlassView extends View {
    static final int SHEEN_NORMAL = 0, SHEEN_SOFT = 1;

    // ---- shared state: the current blurred wallpaper, the incoming one during a change, the light ----
    private static final ArrayList<GlassView> attached = new ArrayList<>();
    private static View stage;
    private static Bitmap blur, next;
    private static float nextMix;
    private static int blurVersion;
    /** CSS gradient angle of the rim highlight, and its strength at each end (DESIGN.md §6.2 table). */
    private static float lightAngle = 180, rimHi = .70f, rimLo = .16f;
    private static int lightVersion;

    static void setStage(View v) { stage = v; }

    static void setBlur(Bitmap b) {
        blur = b;
        next = null;
        nextMix = 0;
        blurVersion++;
        invalidateAll();
    }

    /** Starts the wallpaper-change crossfade: the incoming blur fades in over the current one. */
    static void setNext(Bitmap b) {
        next = b;
        nextMix = 0;
        blurVersion++;
        invalidateAll();
    }

    static void setNextMix(float f) {
        nextMix = f;
        invalidateAll();
    }

    static void setLight(int period) {
        switch (period) {
            case Wallpapers.MORNING: lightAngle = 135; rimHi = .78f; rimLo = .20f; break;
            case Wallpapers.DAY: lightAngle = 170; rimHi = .72f; rimLo = .18f; break;
            case Wallpapers.EVENING: lightAngle = 225; rimHi = .66f; rimLo = .16f; break;
            default: lightAngle = 180; rimHi = .44f; rimLo = .10f; break;
        }
        lightVersion++;
        invalidateAll();
    }

    static void invalidateAll() {
        for (GlassView g : attached) g.invalidate();
    }

    /** Called before each frame is drawn. Cheap: a handful of rect maps. */
    static void syncAll() {
        if (stage == null) return;
        for (int i = 0; i < attached.size(); i++) attached.get(i).syncIfMoved();
    }

    // ---- per panel ----
    float radius;
    float mag = 1.05f;
    /** Magnification of the 12 px refractive rim; 0 for none (only the dock has one). */
    float lensMag;
    float tint;
    float frost;
    float milk;
    int sheen = SHEEN_NORMAL;
    boolean glint;
    private float milkLevel;
    private float[] shadowLayers;
    private Shadow.Baked shadow;

    private final RectF onStage = new RectF();
    private final RectF drawnAt = new RectF();
    private final Matrix matrix = new Matrix();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private BitmapShader shaderBlur, shaderNext, lensBlur, lensNext;
    private int shaderVersion = -1, rimVersion = -1;

    static final Property<View, Float> MILK = new Property<View, Float>(Float.class, "milk") {
        @Override
        public Float get(View v) { return ((GlassView) v).milkLevel; }

        @Override
        public void set(View v, Float f) {
            GlassView g = (GlassView) v;
            if (g.milkLevel != f) {
                g.milkLevel = f;
                g.invalidate();
            }
        }
    };

    GlassView(Context context, float radius) {
        super(context);
        this.radius = radius;
        lens.setStyle(Paint.Style.STROKE);
        lens.setStrokeWidth(12);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(2);
        shadowPaint.setColor(0xFF000000);
        setWillNotDraw(false);
    }

    /** Milk glass: the focus material. `level` fades it (gear focus); the droplets stay at 1. */
    void setMilk(float milk, float level) {
        this.milk = milk;
        this.milkLevel = level;
        invalidate();
    }

    void setTint(float tint, float frost) {
        this.tint = tint;
        this.frost = frost;
        invalidate();
    }

    /** Box-shadow layers as {offsetY, blur, alpha} triples; drawn outside the bounds, so parents must not clip. */
    void setShadow(float... layers) {
        shadowLayers = layers;
        shadow = null;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached.add(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached.remove(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        shadow = null;
        rimVersion = -1;
        float[] stops;
        int[] colors;
        if (sheen == SHEEN_SOFT) {
            colors = new int[]{Ui.white(.08f), Ui.white(.02f), Ui.white(0), Ui.white(.04f)};
            stops = new float[]{0, .30f, .60f, 1};
        } else {
            colors = new int[]{Ui.white(.15f), Ui.white(.04f), Ui.white(0), Ui.white(.05f)};
            stops = new float[]{0, .34f, .60f, 1};
        }
        sheenPaint.setShader(new LinearGradient(0, 0, 0, h, colors, stops, Shader.TileMode.CLAMP));
        // Glint: an ellipse 70 % × 70 % of the panel, centred at (43 %, 5 %), white 55 % fading out.
        RadialGradient rg = new RadialGradient(0, 0, 1, Ui.white(.55f), Ui.white(0), Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(.35f * w, .35f * h);
        m.postTranslate(.43f * w, .05f * h);
        rg.setLocalMatrix(m);
        glintPaint.setShader(rg);
        glintPaint.setAlpha(Math.round(.55f * 255));
    }

    private void syncIfMoved() {
        if (!isShown() || getWidth() == 0) return;
        Ui.mapToStage(this, stage, onStage);
        if (Math.abs(onStage.left - drawnAt.left) > .25f || Math.abs(onStage.top - drawnAt.top) > .25f
                || Math.abs(onStage.width() - drawnAt.width()) > .25f || Math.abs(onStage.height() - drawnAt.height()) > .25f) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float r = Math.min(radius, Math.min(w, h) / 2f);

        // 1. Shadow, behind and outside the panel.
        if (shadowLayers != null) {
            if (shadow == null) shadow = Shadow.get((int) w, (int) h, r, shadowLayers);
            shadow.draw(c, w, h, shadowPaint);
        }

        if (stage != null) Ui.mapToStage(this, stage, onStage);
        else onStage.set(0, 0, w, h);
        drawnAt.set(onStage);

        // 2. Wallpaper through the lens, then 3. the refractive rim (dock only).
        if (shaderVersion != blurVersion) {
            shaderBlur = blur == null ? null : new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shaderNext = next == null ? null : new BitmapShader(next, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            lensBlur = blur == null || lensMag == 0 ? null : new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            lensNext = next == null || lensMag == 0 ? null : new BitmapShader(next, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shaderVersion = blurVersion;
        }
        if (shaderBlur != null) {
            drawWallpaper(c, fill, shaderBlur, blur, mag, 255, 0, w, h, r);
            if (shaderNext != null && nextMix > 0) drawWallpaper(c, fill, shaderNext, next, mag, Math.round(nextMix * 255), 0, w, h, r);
            if (lensBlur != null) {
                drawWallpaper(c, lens, lensBlur, blur, lensMag, Math.round(.70f * 255), 6, w, h, r);
                if (lensNext != null && nextMix > 0) drawWallpaper(c, lens, lensNext, next, lensMag, Math.round(.70f * nextMix * 255), 6, w, h, r);
            }
        } else {
            plain.setColor(0xFF1A1F26);
            c.drawRoundRect(0, 0, w, h, r, r, plain);
        }

        // 4. Tint, then frost.
        if (tint > 0) {
            plain.setColor((Math.round(tint * 255) << 24) | 0x090D12);
            c.drawRoundRect(0, 0, w, h, r, r, plain);
        }
        if (frost > 0) {
            plain.setColor(Ui.white(frost));
            c.drawRoundRect(0, 0, w, h, r, r, plain);
        }
        // 5. Sheen.
        c.drawRoundRect(0, 0, w, h, r, r, sheenPaint);
        // 6. Milk, the focus glass.
        if (milk > 0 && milkLevel > 0) {
            plain.setColor(Ui.white(milk * milkLevel));
            c.drawRoundRect(0, 0, w, h, r, r, plain);
        }
        // 7. Glint.
        if (glint) c.drawRoundRect(0, 0, w, h, r, r, glintPaint);
        // 8. Specular rim along the light of the time of day.
        if (rimVersion != lightVersion) {
            rimPaint.setShader(rimGradient(w, h));
            rimVersion = lightVersion;
        }
        c.drawRoundRect(1, 1, w - 1, h - 1, r - 1, r - 1, rimPaint);
    }

    private void drawWallpaper(Canvas c, Paint p, BitmapShader s, Bitmap b, float m, int alpha, float inset,
                               float w, float h, float r) {
        float scale = 1920f / b.getWidth();
        float sx = onStage.width() / w, sy = onStage.height() / h;
        float cx = onStage.centerX(), cy = onStage.centerY();
        matrix.setScale(scale * m / sx, scale * m / sy);
        matrix.postTranslate((cx * (1 - m) - onStage.left) / sx, (cy * (1 - m) - onStage.top) / sy);
        s.setLocalMatrix(matrix);
        p.setShader(s);
        p.setAlpha(alpha);
        c.drawRoundRect(inset, inset, w - inset, h - inset, r - inset, r - inset, p);
    }

    /** A CSS linear-gradient at `lightAngle`: the line runs through the centre, long enough to span the box. */
    private Shader rimGradient(float w, float h) {
        double a = Math.toRadians(lightAngle);
        float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
        float len = (float) (Math.abs(w * Math.sin(a)) + Math.abs(h * Math.cos(a)));
        float cx = w / 2, cy = h / 2;
        return new LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2,
                new int[]{Ui.white(rimHi), Ui.white(.10f), Ui.white(.03f), Ui.white(rimLo)},
                new float[]{0, .26f, .55f, 1}, Shader.TileMode.CLAMP);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return true;
    }
}
