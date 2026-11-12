package io.github.rowesk.fresco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the dock's marks are made of, with no Android in it so tools/preview-marks.sh can draw the same
 * thing on a Mac: which app gets which mark (assets/marks/<id>.svg, credited in CREDITS.md there) in
 * which colour, the SVG path reader, the optical sizing, and the colour a monogram takes from an icon.
 */
final class MarkArt {
    private MarkArt() {}

    /** Marks are drawn in a 100-unit box, as the hand-drawn Stremio and YouTube ones are. */
    static final float BOX = 100;
    /** Sizing: the mark's bounding box gets this geometric mean, and no side longer than MAX_SIDE. */
    private static final float MEAN = 64, MAX_SIDE = 84;
    /** A monogram for an app whose icon has no clear colour. */
    static final int NEUTRAL = 0xFF46546A;

    /** A mark and its focus colour: a vertical gradient from top to bottom (the same twice for flat). */
    static final class Brand {
        final String id;
        final int top, bottom;
        final String[] packages;
        /** An optical nudge on top of the automatic sizing. */
        float fit = 1;

        Brand(String id, int top, int bottom, String... packages) {
            this.id = id;
            this.top = top;
            this.bottom = bottom;
            this.packages = packages;
        }

        Brand fit(float f) {
            fit = f;
            return this;
        }
    }

    private static Brand b(String id, int color, String... packages) {
        return new Brand(id, color, color, packages);
    }

    private static Brand b(String id, int top, int bottom, String... packages) {
        return new Brand(id, top, bottom, packages);
    }

    /**
     * Each app's mark, by its Fire TV and Android TV package names. Brands too pale or too dark to read
     * on the milk droplet take ink-dark or an accent from their own palette. Stremio and YouTube are
     * drawn by hand in Marks and aren't listed; YouTube TV shares YouTube's (Simple Icons' youtubetv is
     * a wordmark, unreadable at this size). Round marks get a little more size than the automatic
     * sizing gives them, as circles look smaller than squares of the same bounds.
     */
    static final Brand[] BRANDS = {
            // Simple Icons 16.37.0
            b("netflix", 0xFFE50914, "com.netflix.ninja").fit(.92f),
            b("itvx", Ui.INK_DARK, "air.ITVMobilePlayer"),
            b("channel4", Ui.INK_DARK, "com.channel4.ondemand"),
            b("spotify", 0xFF1ED760, 0xFF14B44E, "com.spotify.tv.android").fit(1.06f),
            b("hbomax", Ui.INK_DARK, "com.wbd.stream", "com.hbo.hbonow"),
            b("appletv", Ui.INK_DARK, "com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv"),
            b("paramountplus", 0xFF0064FF, "com.cbs.ott", "com.cbs.ca"),
            b("now", 0xFF00A99D, 0xFF006A63, "com.bskyb.nowtv.beta"),
            b("dazn", Ui.INK_DARK, "com.dazn"),
            b("crunchyroll", 0xFFFF5E00, "com.crunchyroll.crunchyroid").fit(1.06f),
            b("youtubekids", 0xFFFF0033, "com.amazon.firetv.youtube.kids", "com.google.android.youtube.tvkids"),
            b("youtubemusic", 0xFFFF0033, "com.google.android.youtube.tvmusic").fit(1.06f),
            b("twitch", 0xFF9146FF, "tv.twitch.android.viewer", "tv.twitch.android.app"),
            b("plex", 0xFFEBAF00, 0xFFD99500, "com.plexapp.android"),
            b("jellyfin", 0xFFAA5CC3, 0xFF00A4DC, "org.jellyfin.androidtv"),
            b("kodi", 0xFF17B2E7, "org.xbmc.kodi"),
            b("emby", 0xFF52B54B, "tv.emby.embyatv"),
            b("vlcmediaplayer", 0xFFFF8800, "org.videolan.vlc"),
            b("nvidia", 0xFF76B900, "com.nvidia.geforcenow", "com.nvidia.tegrazone3"),
            b("steam", 0xFF1A9FFF, 0xFF102D5A, "com.valvesoftware.steamlink").fit(1.06f),
            b("tubi", 0xFF7408FF, "com.tubitv"),
            b("rakuten", 0xFFBF0000, "tv.wuaki.apptv"),
            b("viaplay", 0xFFFE365F, "com.viaplay.android"),
            b("zdf", 0xFFFA7D19, "com.zdf.android.mediathek"),
            b("fubo", 0xFFC83D1E, "com.fubo.firetv.screen"),
            b("deezer", 0xFFA238FF, "deezer.android.tv", "deezer.android.app"),
            b("tidal", Ui.INK_DARK, "com.aspiro.tidal"),
            // Original marks (tools/DrawMarks.java): these brands aren't in Simple Icons, so each gets
            // a simple idea in its colour rather than a copy of its logo.
            b("primevideo", 0xFF2AA8FF, 0xFF0A6FE0, "com.amazon.firebat", "com.amazon.amazonvideo.livingroom").fit(1.06f),
            b("disneyplus", 0xFF2D6BFF, 0xFF0B2A9E, "com.disney.disneyplus"),
            b("iplayer", 0xFFFF4C98, 0xFFB5128C, "uk.co.bbc.iplayer", "bbc.iplayer.android").fit(.92f),
            b("my5", 0xFF3A6FD8, 0xFF1C3D86, "com.mobileiq.demand5").fit(.94f),
            b("amazonmusic", 0xFF25D1DA, 0xFF128FB0, "com.amazon.bueller.music", "com.amazon.music.tv"),
            b("u", 0xFFFF7A6B, 0xFFE24857, "uk.co.uktv.dave").fit(.94f),
            b("plutotv", 0xFFFFD21A, 0xFFF0A500, "tv.pluto.android"),
            b("peacock", Ui.INK_DARK, "com.peacocktv.peacockandroid").fit(1.08f),
            b("hulu", 0xFF1CE783, 0xFF0FAE5F, "com.hulu.plus", "com.hulu.livingroomplus").fit(.92f),
            b("xboxcloud", 0xFF1A9A1A, 0xFF0B5E0B, "gamepass.fire.tv", "com.microsoft.xboxone.smartglass"),
            b("moonlight", 0xFF7D8CFF, 0xFF3442C2, "com.limelight"),
    };

    private static HashMap<String, Brand> byPackage;

    /** The mark for this package, or null for a monogram. */
    static synchronized Brand forPackage(String pkg) {
        if (byPackage == null) {
            byPackage = new HashMap<>();
            for (Brand b : BRANDS) for (String p : b.packages) byPackage.put(p, b);
        }
        return byPackage.get(pkg);
    }

    // ------------------------------------------------------------------ paths

    static final byte MOVE = 0, LINE = 1, CUBIC = 2, CLOSE = 3;

    /** One filled SVG path in mark units: ops, and their points (one pair, or three for a cubic). */
    static final class Layer {
        byte[] ops = new byte[64];
        float[] pts = new float[256];
        int nOps, nPts;
        boolean evenOdd;
        /** White on focus and cut out at rest, like the play triangles. */
        boolean cut;
        /** Its own colour on focus, or 0 for the brand's. */
        int fill;

        private void op(byte op, float... p) {
            if (nOps == ops.length) ops = java.util.Arrays.copyOf(ops, nOps * 2);
            if (nPts + p.length > pts.length) pts = java.util.Arrays.copyOf(pts, (nPts + p.length) * 2);
            ops[nOps++] = op;
            for (float f : p) pts[nPts++] = f;
        }
    }

    /** A mark's layers, sized and centred in the box; top and bottom bound its gradient. */
    static final class Art {
        final List<Layer> layers;
        final float top, bottom;

        Art(List<Layer> layers, float top, float bottom) {
            this.layers = layers;
            this.top = top;
            this.bottom = bottom;
        }
    }

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"([^\"]+)\"");
    private static final Pattern PATH = Pattern.compile("<path\\b([^>]*)>");

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\s" + name + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Reads the paths of a plain SVG (Simple Icons' files, or DrawMarks' output). A path with no fill
     * takes the brand colour; a white one is a cut-out; any other fill is that layer's own colour.
     */
    static Art load(String svg, float fit) {
        List<Layer> layers = new ArrayList<>();
        Matcher m = PATH.matcher(svg);
        while (m.find()) {
            Layer l = new Layer();
            parse(attr(m.group(1), "d"), l);
            l.evenOdd = "evenodd".equals(attr(m.group(1), "fill-rule"));
            String fill = attr(m.group(1), "fill");
            if (fill != null) {
                fill = fill.toLowerCase();
                if (fill.equals("#fff") || fill.equals("#ffffff") || fill.equals("white")) l.cut = true;
                else l.fill = 0xFF000000 | Integer.parseInt(fill.substring(1), 16);
            }
            layers.add(l);
        }
        return fitted(layers, fit);
    }

    /** Centres the art in the box at its optical size: by area, with wide wordmarks capped by width. */
    private static Art fitted(List<Layer> layers, float fit) {
        float[] r = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Layer l : layers) bounds(l, r);
        float w = r[2] - r[0], h = r[3] - r[1];
        float s = fit * Math.min(MEAN / (float) Math.sqrt(w * h), MAX_SIDE / Math.max(w, h));
        float dx = BOX / 2 - s * (r[0] + r[2]) / 2, dy = BOX / 2 - s * (r[1] + r[3]) / 2;
        for (Layer l : layers) {
            for (int i = 0; i < l.nPts; i += 2) {
                l.pts[i] = l.pts[i] * s + dx;
                l.pts[i + 1] = l.pts[i + 1] * s + dy;
            }
        }
        return new Art(layers, r[1] * s + dy, r[3] * s + dy);
    }

    /** The ink's bounds, following each curve rather than its control points. */
    private static void bounds(Layer l, float[] r) {
        int p = 0;
        float x = 0, y = 0;
        for (int i = 0; i < l.nOps; i++) {
            if (l.ops[i] == CUBIC) {
                for (int k = 1; k <= 16; k++) {
                    float t = k / 16f, u = 1 - t;
                    float a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
                    grow(r, a * x + b * l.pts[p] + c * l.pts[p + 2] + d * l.pts[p + 4],
                            a * y + b * l.pts[p + 1] + c * l.pts[p + 3] + d * l.pts[p + 5]);
                }
                x = l.pts[p + 4];
                y = l.pts[p + 5];
                p += 6;
            } else if (l.ops[i] != CLOSE) {
                x = l.pts[p];
                y = l.pts[p + 1];
                grow(r, x, y);
                p += 2;
            }
        }
    }

    private static void grow(float[] r, float x, float y) {
        r[0] = Math.min(r[0], x);
        r[1] = Math.min(r[1], y);
        r[2] = Math.max(r[2], x);
        r[3] = Math.max(r[3], y);
    }

    /** SVG path data, every command, into moves, lines and cubics (quadratics and arcs become cubics). */
    static void parse(String d, Layer out) {
        Reader in = new Reader(d);
        float x = 0, y = 0, sx = 0, sy = 0, cx = 0, cy = 0;
        char cmd = 0, prev = 0;
        while (in.more()) {
            char c = in.command();
            if (c != 0) cmd = c;
            else if (cmd == 'M') cmd = 'L';
            else if (cmd == 'm') cmd = 'l';
            else if (cmd == 0) throw new IllegalArgumentException("path data starts without a command");
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M':
                    x = sx = ox + in.num();
                    y = sy = oy + in.num();
                    out.op(MOVE, x, y);
                    break;
                case 'L':
                    x = ox + in.num();
                    y = oy + in.num();
                    out.op(LINE, x, y);
                    break;
                case 'H':
                    x = ox + in.num();
                    out.op(LINE, x, y);
                    break;
                case 'V':
                    y = oy + in.num();
                    out.op(LINE, x, y);
                    break;
                case 'C': {
                    float x1 = ox + in.num(), y1 = oy + in.num();
                    cx = ox + in.num();
                    cy = oy + in.num();
                    x = ox + in.num();
                    y = oy + in.num();
                    out.op(CUBIC, x1, y1, cx, cy, x, y);
                    break;
                }
                case 'S': {
                    boolean smooth = "CcSs".indexOf(prev) >= 0;
                    float x1 = smooth ? 2 * x - cx : x, y1 = smooth ? 2 * y - cy : y;
                    cx = ox + in.num();
                    cy = oy + in.num();
                    x = ox + in.num();
                    y = oy + in.num();
                    out.op(CUBIC, x1, y1, cx, cy, x, y);
                    break;
                }
                case 'Q':
                case 'T': {
                    boolean t = Character.toUpperCase(cmd) == 'T';
                    boolean smooth = "QqTt".indexOf(prev) >= 0;
                    float qx, qy;
                    if (!t) {
                        qx = ox + in.num();
                        qy = oy + in.num();
                    } else {
                        qx = smooth ? 2 * x - cx : x;
                        qy = smooth ? 2 * y - cy : y;
                    }
                    float ex = ox + in.num(), ey = oy + in.num();
                    out.op(CUBIC, x + 2f / 3 * (qx - x), y + 2f / 3 * (qy - y), ex + 2f / 3 * (qx - ex), ey + 2f / 3 * (qy - ey), ex, ey);
                    cx = qx;
                    cy = qy;
                    x = ex;
                    y = ey;
                    break;
                }
                case 'A': {
                    float rx = in.num(), ry = in.num(), rot = in.num();
                    boolean large = in.flag(), sweep = in.flag();
                    float ex = ox + in.num(), ey = oy + in.num();
                    arc(out, x, y, rx, ry, rot, large, sweep, ex, ey);
                    x = ex;
                    y = ey;
                    break;
                }
                case 'Z':
                    out.op(CLOSE);
                    x = sx;
                    y = sy;
                    break;
                default:
                    throw new IllegalArgumentException("unknown path command " + cmd);
            }
            prev = cmd;
        }
    }

    /** An SVG elliptical arc as cubics of at most 90° each (SVG 1.1 implementation notes, F.6). */
    private static void arc(Layer out, float x0, float y0, float rx, float ry, float rotDeg, boolean large, boolean sweep, float x, float y) {
        if (x0 == x && y0 == y) return;
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0) {
            out.op(LINE, x, y);
            return;
        }
        double phi = Math.toRadians(rotDeg), cos = Math.cos(phi), sin = Math.sin(phi);
        double dx = (x0 - x) / 2.0, dy = (y0 - y) / 2.0;
        double x1 = cos * dx + sin * dy, y1 = -sin * dx + cos * dy;
        double lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry);
        if (lambda > 1) {
            rx *= Math.sqrt(lambda);
            ry *= Math.sqrt(lambda);
        }
        double num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        double den = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        double k = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = k * rx * y1 / ry, cyp = -k * ry * x1 / rx;
        double cx = cos * cxp - sin * cyp + (x0 + x) / 2.0, cy = sin * cxp + cos * cyp + (y0 + y) / 2.0;
        double t1 = Math.atan2((y1 - cyp) / ry, (x1 - cxp) / rx);
        double dt = Math.atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx) - t1;
        if (sweep && dt < 0) dt += 2 * Math.PI;
        else if (!sweep && dt > 0) dt -= 2 * Math.PI;
        int n = (int) Math.ceil(Math.abs(dt) / (Math.PI / 2) - 1e-6);
        double step = dt / n, a = 4 / 3.0 * Math.tan(step / 4);
        for (int i = 0; i < n; i++) {
            double s0 = t1 + i * step, s1 = s0 + step;
            double c0 = Math.cos(s0), n0 = Math.sin(s0), c1 = Math.cos(s1), n1 = Math.sin(s1);
            // The unit-circle cubic, scaled by the radii, rotated, then moved to the centre.
            double[] p = {c0 - a * n0, n0 + a * c0, c1 + a * n1, n1 - a * c1, c1, n1};
            float[] q = new float[6];
            for (int j = 0; j < 6; j += 2) {
                double ux = p[j] * rx, uy = p[j + 1] * ry;
                q[j] = (float) (cos * ux - sin * uy + cx);
                q[j + 1] = (float) (sin * ux + cos * uy + cy);
            }
            if (i == n - 1) {
                q[4] = x;
                q[5] = y;
            }
            out.op(CUBIC, q);
        }
    }

    /** Path data tokens, including the packed forms optimisers write: "1.5.5", "-1-2", arc flags "011". */
    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        private void skip() {
            while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) i++;
        }

        boolean more() {
            skip();
            return i < s.length();
        }

        char command() {
            skip();
            char c = s.charAt(i);
            if (Character.isLetter(c) && c != 'e' && c != 'E') {
                i++;
                return c;
            }
            return 0;
        }

        boolean flag() {
            skip();
            return s.charAt(i++) == '1';
        }

        float num() {
            skip();
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            boolean dot = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') i++;
                else if (c == '.' && !dot) {
                    dot = true;
                    i++;
                } else break;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (s.charAt(i) == '-' || s.charAt(i) == '+') i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            }
            if (i == start) throw new IllegalArgumentException("expected a number at " + start + " in path data");
            return Float.parseFloat(s.substring(start, i));
        }
    }

    // ------------------------------------------------------------------ monogram colour

    /**
     * A monogram's focus colour from its app's icon pixels: the most common saturated hue (weighted by
     * saturation), averaged, then darkened or lightened until it reads on the milk droplet and under a
     * white letter (relative luminance 0.06–0.20). Neutral when the icon has no real colour.
     */
    static int accent(int[] argb) {
        float[] weight = new float[24], r = new float[24], g = new float[24], b = new float[24];
        float opaque = 0;
        for (int px : argb) {
            int a = px >>> 24;
            if (a < 128) continue;
            opaque++;
            int pr = px >> 16 & 255, pg = px >> 8 & 255, pb = px & 255;
            int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
            if (max < 40) continue;
            float sat = (max - min) / (float) max;
            if (sat < .3f) continue;
            float[] hsl = hsl(px);
            int bin = Math.min(23, (int) (hsl[0] / 15));
            float w = sat * sat;
            weight[bin] += w;
            r[bin] += pr * w;
            g[bin] += pg * w;
            b[bin] += pb * w;
        }
        int best = 0;
        for (int i = 1; i < 24; i++) if (weight[i] > weight[best]) best = i;
        // Too little colour to call: a grey or black icon gets the neutral slate.
        if (opaque == 0 || weight[best] < opaque * .04f) return NEUTRAL;
        int c = 0xFF000000 | Math.round(r[best] / weight[best]) << 16 | Math.round(g[best] / weight[best]) << 8 | Math.round(b[best] / weight[best]);
        float[] hsl = hsl(c);
        hsl[1] = Math.max(hsl[1], .45f);
        float lo = 0, hi = 1, l = hsl[2];
        double lum = luminance(fromHsl(hsl[0], hsl[1], l));
        if (lum > .20 || lum < .06) {
            double target = lum > .20 ? .20 : .06;
            for (int i = 0; i < 20; i++) {
                float mid = (lo + hi) / 2;
                if (luminance(fromHsl(hsl[0], hsl[1], mid)) > target) hi = mid;
                else lo = mid;
            }
            l = lo;
        }
        return fromHsl(hsl[0], hsl[1], l);
    }

    static double luminance(int c) {
        return .2126 * lin(c >> 16 & 255) + .7152 * lin(c >> 8 & 255) + .0722 * lin(c & 255);
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }

    private static float[] hsl(int c) {
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            float d = max - min;
            s = l > .5f ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return new float[]{h, s, l};
    }

    private static int fromHsl(float h, float s, float l) {
        float q = l < .5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return 0xFF000000 | channel(p, q, h / 360 + 1f / 3) << 16 | channel(p, q, h / 360) << 8 | channel(p, q, h / 360 - 1f / 3);
    }

    private static int channel(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        float v = t < 1f / 6 ? p + (q - p) * 6 * t : t < .5f ? q : t < 2f / 3 ? p + (q - p) * (2f / 3 - t) * 6 : p;
        return Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
