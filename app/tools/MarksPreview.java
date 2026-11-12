package io.github.rowesk.fresco;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * A contact sheet of every dock mark, drawn on the Mac from the app's own data and code: assets/marks
 * through MarkArt (the same reader, sizing and colours), with Java2D standing in for Marks' Android
 * drawing. Each mark is shown at rest on the painting and focused on a milk tile, a little over life size.
 * Args: <assets dir> <background png> <out png>. Run it with tools/preview-marks.sh.
 */
public final class MarksPreview {
    private static final float K = 1.25f;
    private static final int TILE = 210, GAP = 10, COLS = 5, LABEL = 34, MARGIN = 28;
    private static final int CELL_W = 2 * TILE + GAP + 2 * MARGIN, CELL_H = TILE + LABEL + 22;

    private static Font display;

    public static void main(String[] args) throws Exception {
        String assets = args[0];
        BufferedImage bg = ImageIO.read(new File(args[1]));
        display = Font.createFont(Font.TRUETYPE_FONT, new File(assets, "fonts/RedHatDisplay-VF.ttf"));

        Map<String, String> names = new LinkedHashMap<>();
        String[][] n = {{"netflix", "Netflix"}, {"itvx", "ITVX"}, {"channel4", "Channel 4"}, {"spotify", "Spotify"},
                {"hbomax", "HBO Max"}, {"appletv", "Apple TV"}, {"paramountplus", "Paramount+"}, {"now", "NOW"},
                {"dazn", "DAZN"}, {"crunchyroll", "Crunchyroll"}, {"youtubekids", "YouTube Kids"},
                {"youtubemusic", "YouTube Music"}, {"twitch", "Twitch"}, {"plex", "Plex"},
                {"jellyfin", "Jellyfin"}, {"kodi", "Kodi"}, {"emby", "Emby"}, {"vlcmediaplayer", "VLC"},
                {"nvidia", "GeForce NOW"}, {"steam", "Steam Link"}, {"tubi", "Tubi"}, {"rakuten", "Rakuten TV"},
                {"viaplay", "Viaplay"}, {"zdf", "ZDF"}, {"fubo", "Fubo"}, {"deezer", "Deezer"}, {"tidal", "TIDAL"},
                {"primevideo", "Prime Video *"}, {"disneyplus", "Disney+ *"}, {"iplayer", "BBC iPlayer *"},
                {"my5", "My5 *"}, {"amazonmusic", "Amazon Music *"}, {"u", "U *"}, {"plutotv", "Pluto TV *"},
                {"peacock", "Peacock *"}, {"hulu", "Hulu *"}, {"xboxcloud", "Xbox Cloud *"}, {"moonlight", "Moonlight *"}};
        for (String[] e : n) names.put(e[0], e[1]);

        List<Object[]> entries = new ArrayList<>();
        entries.add(new Object[]{"Stremio (hand)", stremio(), new Object[]{0xFFA57BFF, 0xFF5A3FE3}});
        entries.add(new Object[]{"YouTube (hand)", youtube(), new Object[]{0xFFFF0033, 0xFFFF0033}});
        for (MarkArt.Brand b : MarkArt.BRANDS) {
            String svg = new String(Files.readAllBytes(Paths.get(assets, "marks", b.id + ".svg")), StandardCharsets.UTF_8);
            MarkArt.Art art = MarkArt.load(svg, b.fit);
            entries.add(new Object[]{names.getOrDefault(b.id, b.id), art, b});
        }
        // Monograms, coloured from stand-in icons: a background colour with a white glyph on it.
        Object[][] fakes = {{"Downloader", 0xFFF87828}, {"TiviMate", 0xFF183858}, {"STV Player", 0xFF1A1A1A},
                {"ARD Mediathek", 0xFF083888}, {"Sling", 0xFFFFC72C}, {"4K Player", 0xFF8BC34A}, {"(no icon)", 0}};
        for (Object[] f : fakes) {
            int col = (Integer) f[1];
            int color = col == 0 ? MarkArt.NEUTRAL : MarkArt.accent(fakeIcon(col));
            entries.add(new Object[]{"\"" + f[0] + "\" mono", monogram((String) f[0]), new Object[]{color, color}});
        }

        int rows = (entries.size() + COLS - 1) / COLS;
        int w = COLS * CELL_W + 40, h = rows * CELL_H + 90;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        hints(g);
        g.setColor(new Color(0x1B1E24));
        g.fillRect(0, 0, w, h);
        g.setFont(display.deriveFont(Font.PLAIN, 30f));
        g.setColor(Color.WHITE);
        g.drawString("Fresco dock marks: rest (on the painting) and focus (on milk glass), 1.25x. * = original design", 32, 52);
        g.setFont(display.deriveFont(Font.PLAIN, 22f));
        for (int i = 0; i < entries.size(); i++) {
            Object[] e = entries.get(i);
            int x = 20 + (i % COLS) * CELL_W + MARGIN, y = 80 + (i / COLS) * CELL_H;
            // Rest: the painting behind the dock's dark glass.
            g.drawImage(bg, x, y, x + TILE, y + TILE, (x * 7) % (bg.getWidth() - TILE), (y * 3) % (bg.getHeight() - TILE),
                    (x * 7) % (bg.getWidth() - TILE) + TILE, (y * 3) % (bg.getHeight() - TILE) + TILE, null);
            g.setColor(new Color(9, 13, 18, 70));
            g.fillRect(x, y, TILE, TILE);
            int fx = x + TILE + GAP;
            g.drawImage(bg, fx, y, fx + TILE, y + TILE, (fx * 7) % (bg.getWidth() - TILE), (y * 3) % (bg.getHeight() - TILE),
                    (fx * 7) % (bg.getWidth() - TILE) + TILE, (y * 3) % (bg.getHeight() - TILE) + TILE, null);
            g.setColor(new Color(9, 13, 18, 70));
            g.fillRect(fx, y, TILE, TILE);
            // Focus: the milk droplet.
            g.setColor(new Color(255, 255, 255, 214));
            g.fill(new RoundRectangle2D.Float(fx + 8, y + 8, TILE - 16, TILE - 16, 52, 52));
            BufferedImage rest = draw(e[1], e[2], false), focus = draw(e[1], e[2], true);
            int o = (TILE - rest.getWidth()) / 2;
            g.drawImage(rest, x + o, y + o, null);
            g.drawImage(focus, fx + o, y + o, null);
            g.setColor(new Color(255, 255, 255, 220));
            g.drawString((String) e[0], x, y + TILE + 28);
        }
        g.dispose();
        ImageIO.write(out, "png", new File(args[2]));
        System.out.println("Wrote " + args[2] + " (" + entries.size() + " marks)");
    }

    /** Hand-drawn and monogram marks as {shape, cut}; brand marks as MarkArt.Art. */
    private static BufferedImage draw(Object mark, Object brand, boolean focus) {
        int px = Math.round((Marks.SIZE + 2 * Marks.PAD) * K);
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        AffineTransform t = new AffineTransform();
        t.scale(K, K);
        t.translate(Marks.PAD, Marks.PAD);
        t.scale(Marks.SIZE / 100.0, Marks.SIZE / 100.0);
        List<Shape> shapes = new ArrayList<>();
        List<Object> fills = new ArrayList<>();
        float top, bottom;
        int c0, c1;
        if (mark instanceof MarkArt.Art) {
            MarkArt.Art art = (MarkArt.Art) mark;
            MarkArt.Brand b = (MarkArt.Brand) brand;
            for (MarkArt.Layer l : art.layers) {
                shapes.add(path(l));
                fills.add(l.cut ? (Object) "cut" : l.fill != 0 ? (Object) l.fill : null);
            }
            top = art.top;
            bottom = art.bottom;
            c0 = b.top;
            c1 = b.bottom;
        } else {
            Shape[] s = (Shape[]) mark;
            shapes.add(s[0]);
            fills.add(null);
            shapes.add(s[1]);
            fills.add("cut");
            Rectangle2D r = s[0].getBounds2D();
            top = (float) r.getMinY();
            bottom = (float) r.getMaxY();
            Object[] c = (Object[]) brand;
            c0 = (Integer) c[0];
            c1 = (Integer) c[1];
        }
        if (!focus) {
            Area ink = new Area(), cut = new Area();
            for (int i = 0; i < shapes.size(); i++) ("cut".equals(fills.get(i)) ? cut : ink).add(new Area(shapes.get(i)));
            ink.subtract(cut);
            Shape s = t.createTransformedShape(ink);
            // The soft shadow: Android's 3 px shadow layer is about a 2.2 px Gaussian, 2 px down, 18 % black.
            BufferedImage sh = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = sh.createGraphics();
            hints(sg);
            sg.setColor(Color.BLACK);
            sg.translate(0, 2 * K);
            sg.fill(s);
            sg.dispose();
            blur(sh, 2.2f * K, 0x2E / 255f);
            g.drawImage(sh, 0, 0, null);
            g.setColor(new Color(255, 255, 255, Math.round(.92f * 255)));
            g.fill(s);
        } else {
            g.transform(t);
            for (int i = 0; i < shapes.size(); i++) {
                Object f = fills.get(i);
                if ("cut".equals(f)) g.setPaint(Color.WHITE);
                else if (f != null) g.setPaint(new Color((Integer) f, true));
                else g.setPaint(new GradientPaint(0, top, new Color(c0, true), 0, bottom, new Color(c1, true)));
                g.fill(shapes.get(i));
            }
        }
        g.dispose();
        return img;
    }

    private static Path2D path(MarkArt.Layer l) {
        Path2D p = new Path2D.Float(l.evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        float[] q = l.pts;
        for (int i = 0, k = 0; i < l.nOps; i++) {
            switch (l.ops[i]) {
                case MarkArt.MOVE: p.moveTo(q[k], q[k + 1]); k += 2; break;
                case MarkArt.LINE: p.lineTo(q[k], q[k + 1]); k += 2; break;
                case MarkArt.CUBIC: p.curveTo(q[k], q[k + 1], q[k + 2], q[k + 3], q[k + 4], q[k + 5]); k += 6; break;
                default: p.closePath();
            }
        }
        return p;
    }

    // The hand-drawn marks and the monogram, as Marks draws them.

    private static Shape[] stremio() {
        Shape sq = AffineTransform.getRotateInstance(Math.toRadians(45), 50, 50)
                .createTransformedShape(new RoundRectangle2D.Float(21, 21, 58, 58, 26, 26));
        return new Shape[]{sq, triangle(41, 35.5f, 65, 50, 41, 64.5f, 5)};
    }

    private static Shape[] youtube() {
        return new Shape[]{new RoundRectangle2D.Float(12, 25, 76, 50, 30, 30), triangle(43, 38.5f, 62, 50, 43, 61.5f, 4)};
    }

    private static Shape[] monogram(String name) {
        String ch = "?";
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                ch = new String(Character.toChars(cp)).toUpperCase();
                break;
            }
            i += Character.charCount(cp);
        }
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        Shape glyph = display.deriveFont(Font.PLAIN, Marks.MONO_TEXT).createGlyphVector(g.getFontRenderContext(), ch).getOutline();
        g.dispose();
        Area bold = new Area(new BasicStroke(Marks.MONO_BOLD, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(glyph));
        bold.add(new Area(glyph));
        Rectangle2D r = bold.getBounds2D();
        Shape letter = AffineTransform.getTranslateInstance(50 - r.getCenterX(), 50 - r.getCenterY()).createTransformedShape(bold);
        return new Shape[]{new RoundRectangle2D.Float(20, 20, 60, 60, 32, 32), letter};
    }

    private static Shape triangle(float x0, float y0, float x1, float y1, float x2, float y2, float stroke) {
        Path2D t = new Path2D.Float();
        t.moveTo(x0, y0);
        t.lineTo(x1, y1);
        t.lineTo(x2, y2);
        t.closePath();
        Area a = new Area(t);
        a.add(new Area(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(t)));
        return a;
    }

    private static int[] fakeIcon(int color) {
        int[] px = new int[32 * 32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) px[y * 32 + x] = x > 10 && x < 22 && y > 8 && y < 24 ? 0xFFFFFFFF : color;
        return px;
    }

    /** A separable Gaussian blur of the alpha channel, then scaled to the shadow's opacity. */
    private static void blur(BufferedImage img, float sigma, float alpha) {
        int w = img.getWidth(), h = img.getHeight(), r = (int) Math.ceil(sigma * 3);
        float[] k = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) sum += k[i + r] = (float) Math.exp(-i * i / (2 * sigma * sigma));
        float[] a = new float[w * h], b = new float[w * h];
        for (int i = 0; i < w * h; i++) a[i] = (img.getRGB(i % w, i / w) >>> 24) / 255f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = 0;
            for (int i = -r; i <= r; i++) v += k[i + r] * a[y * w + Math.max(0, Math.min(w - 1, x + i))];
            b[y * w + x] = v / sum;
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float v = 0;
            for (int i = -r; i <= r; i++) v += k[i + r] * b[Math.max(0, Math.min(h - 1, y + i)) * w + x];
            img.setRGB(x, y, Math.round(Math.min(1, v / sum) * alpha * 255) << 24);
        }
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setComposite(AlphaComposite.SrcOver);
    }
}
