import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Draws Fresco's original marks (the brands Simple Icons doesn't carry) with Java2D geometry and writes
 * them to assets/marks/<id>.svg. Each is a simple idea in the brand's colour, never a trace of its logo.
 * Units are a 100 box; the app sizes every mark optically anyway (MarkArt). A path with no fill takes the
 * brand colour, a white one is cut out at rest, and any other fill is its own colour on focus.
 * Run it with tools/preview-marks.sh --draw.
 */
public final class DrawMarks {
    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args[0]);

        // Prime Video: a disc with a play triangle cut out.
        write(dir, "primevideo", layer(circle(50, 50, 40), null), layer(play(41, 32, 69, 50, 41, 68, 7), "#fff"));

        // Disney+: a plus under an orbiting arc.
        Area plus = new Area(new RoundRectangle2D.Double(43, 40, 14, 42, 5, 5));
        plus.add(new Area(new RoundRectangle2D.Double(29, 54, 42, 14, 5, 5)));
        Area orbit = stroke(new Arc2D.Double(6, 26, 88, 70, -16, 212, Arc2D.OPEN), 7, BasicStroke.CAP_ROUND);
        plus.add(orbit);
        write(dir, "disneyplus", layer(plus, null));

        // BBC iPlayer: a soft play triangle in pink to magenta.
        write(dir, "iplayer", layer(play(30, 18, 82, 50, 30, 82, 18), null));

        // My5: a bold 5 from a bar, a stem and an open bowl.
        Area five = new Area(new Rectangle2D.Double(30, 12, 44, 15));
        five.add(new Area(new Rectangle2D.Double(30, 12, 15, 44)));
        five.add(stroke(new Arc2D.Double(31.5, 41.5, 41, 41, 140, -285, Arc2D.OPEN), 15, BasicStroke.CAP_BUTT));
        write(dir, "my5", layer(five, null));

        // Amazon Music: a pair of beamed notes.
        Area notes = new Area(rotated(new Ellipse2D.Double(18, 64, 26, 19), -20, 31, 73.5));
        notes.add(new Area(rotated(new Ellipse2D.Double(58, 56, 26, 19), -20, 71, 65.5)));
        notes.add(new Area(new Rectangle2D.Double(36.5, 26, 7, 46)));
        notes.add(new Area(new Rectangle2D.Double(76.5, 18, 7, 46)));
        Path2D beam = new Path2D.Double();
        beam.moveTo(36.5, 24);
        beam.lineTo(83.5, 14);
        beam.lineTo(83.5, 27);
        beam.lineTo(36.5, 37);
        beam.closePath();
        notes.add(new Area(beam));
        write(dir, "amazonmusic", layer(notes, null));

        // U (UKTV Play): a round-ended U.
        Path2D u = new Path2D.Double();
        u.moveTo(29, 21);
        u.lineTo(29, 56);
        u.append(new Arc2D.Double(29, 35, 42, 42, 180, 180, Arc2D.OPEN), true);
        u.lineTo(71, 21);
        write(dir, "u", layer(stroke(u, 18, BasicStroke.CAP_ROUND), null));

        // Pluto TV: a planet with a tilted ring passing in front of it, in yellow and ink.
        Shape ringPath = rotated(new Ellipse2D.Double(4, 37, 92, 26), -18, 50, 50);
        Area ring = stroke(ringPath, 6.5f, BasicStroke.CAP_BUTT);
        Area front = new Area(rotated(new Rectangle2D.Double(-10, 50, 120, 60), -18, 50, 50));
        Area planet = new Area(circle(50, 50, 27));
        Area frontRing = new Area(ring);
        frontRing.intersect(front);
        Area gapFront = stroke(ringPath, 6.5f + 7, BasicStroke.CAP_BUTT);
        gapFront.intersect(front);
        planet.subtract(gapFront);
        Area backRing = new Area(ring);
        backRing.subtract(front);
        backRing.subtract(new Area(circle(50, 50, 27 + 3.5)));
        Area ringAll = new Area(frontRing);
        ringAll.add(backRing);
        write(dir, "plutotv", layer(planet, null), layer(ringAll, "#14182b"));

        // Peacock: a fan of six dots around an ink pivot.
        String[] hues = {"#fcb712", "#f37021", "#e2213d", "#7a4ccf", "#0a8fe0", "#13ac4a"};
        String[] dots = new String[7];
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(165 - 30 * i);
            dots[i] = layer(circle(50 + 40 * Math.cos(a), 74 - 40 * Math.sin(a), 9.5), hues[i]);
        }
        dots[6] = layer(circle(50, 74, 8), null);
        write(dir, "peacock", dots);

        // Hulu: a rounded lowercase h.
        Path2D h = new Path2D.Double();
        h.moveTo(64, 82);
        h.lineTo(64, 58);
        h.append(new Arc2D.Double(36, 44, 28, 28, 0, 180, Arc2D.OPEN), true);
        Area hh = stroke(h, 15, BasicStroke.CAP_ROUND);
        Path2D stem = new Path2D.Double();
        stem.moveTo(36, 18);
        stem.lineTo(36, 82);
        hh.add(stroke(stem, 15, BasicStroke.CAP_ROUND));
        write(dir, "hulu", layer(hh, null));

        // Xbox Cloud Gaming: a cloud with a gamepad's d-pad and buttons cut out.
        Area cloud = new Area(circle(34, 56, 18));
        cloud.add(new Area(circle(55, 44, 24)));
        cloud.add(new Area(circle(75, 58, 16)));
        cloud.add(new Area(new RoundRectangle2D.Double(16, 50, 75, 24, 24, 24)));
        Area pad = new Area(new RoundRectangle2D.Double(26, 57.5, 22, 7, 3, 3));
        pad.add(new Area(new RoundRectangle2D.Double(33.5, 50, 7, 22, 3, 3)));
        pad.add(new Area(circle(62, 61, 4.2)));
        pad.add(new Area(circle(72, 55, 4.2)));
        write(dir, "xboxcloud", layer(cloud, null), layer(pad, "#fff"));

        // Moonlight: a crescent.
        Area moon = new Area(circle(50, 50, 38));
        moon.subtract(new Area(circle(67, 37, 31)));
        write(dir, "moonlight", layer(moon, null));
    }

    private static Ellipse2D circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }

    private static Shape rotated(Shape s, double deg, double cx, double cy) {
        return AffineTransform.getRotateInstance(Math.toRadians(deg), cx, cy).createTransformedShape(s);
    }

    private static Area stroke(Shape s, float width, int cap) {
        return new Area(new BasicStroke(width, cap, BasicStroke.JOIN_ROUND).createStrokedShape(s));
    }

    /** A triangle with its corners rounded by a round-joined stroke, as Marks.triangle does. */
    private static Area play(double x0, double y0, double x1, double y1, double x2, double y2, float round) {
        Path2D t = new Path2D.Double();
        t.moveTo(x0, y0);
        t.lineTo(x1, y1);
        t.lineTo(x2, y2);
        t.closePath();
        Area a = new Area(t);
        a.add(stroke(t, round, BasicStroke.CAP_ROUND));
        return a;
    }

    /** One <path>: Area's outlines never overlap, so even-odd fills them exactly. */
    private static String layer(Shape s, String fill) {
        StringBuilder d = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO: d.append('M').append(n(c[0])).append(' ').append(n(c[1])); break;
                case PathIterator.SEG_LINETO: d.append('L').append(n(c[0])).append(' ').append(n(c[1])); break;
                case PathIterator.SEG_QUADTO: d.append('Q').append(n(c[0])).append(' ').append(n(c[1])).append(' ')
                        .append(n(c[2])).append(' ').append(n(c[3])); break;
                case PathIterator.SEG_CUBICTO: d.append('C').append(n(c[0])).append(' ').append(n(c[1])).append(' ')
                        .append(n(c[2])).append(' ').append(n(c[3])).append(' ').append(n(c[4])).append(' ').append(n(c[5])); break;
                default: d.append('Z');
            }
        }
        return "<path fill-rule=\"evenodd\"" + (fill == null ? "" : " fill=\"" + fill + "\"") + " d=\"" + d + "\"/>";
    }

    private static String n(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }

    private static void write(Path dir, String id, String... layers) throws IOException {
        StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">");
        svg.append("<title>").append(id).append(" (original Fresco mark, tools/DrawMarks.java)</title>");
        for (String l : layers) svg.append(l);
        svg.append("</svg>\n");
        Files.write(dir.resolve(id + ".svg"), svg.toString().getBytes(StandardCharsets.UTF_8));
    }
}
