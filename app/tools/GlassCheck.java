package io.github.rowesk.fresco;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Runs the app's own glass solver (Wallpapers.Tuning, DESIGN.md §7) on the Mac over 32x18 grids of
 * each blurred copy, and flags any value that hit its clamp, i.e. where the contrast target couldn't be
 * met. Input lines: `<name> r g b r g b …` (576 cells, row-major). Run it with tools/check-glass.sh.
 */
public final class GlassCheck {
    public static void main(String[] args) throws IOException {
        System.out.printf("%-34s %5s %5s %5s %5s %5s %5s %5s %5s %5s %5s  %s%n",
                "wallpaper", "meanL", "frost", "dock", "gear", "sheet", "milk", "top", "bottom", "row", "sett", "at a clamp");
        int flagged = 0, n = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(args[0]))) {
            for (String line; (line = in.readLine()) != null; ) {
                String[] p = line.trim().split("\\s+");
                double[][] g = new double[576][];
                for (int i = 0; i < 576; i++) {
                    g[i] = new double[]{Double.parseDouble(p[1 + 3 * i]), Double.parseDouble(p[2 + 3 * i]), Double.parseDouble(p[3 + 3 * i])};
                }
                Wallpapers.Tuning t = Wallpapers.Tuning.fromGrid(g);
                StringBuilder clamp = new StringBuilder();
                if (t.dock >= .78f) clamp.append(" dock");
                if (t.gear >= .78f) clamp.append(" gear");
                if (t.sheet >= .78f) clamp.append(" sheet");
                if (t.milk >= .92f || t.milkGear >= .92f || t.milkSheet >= .92f) clamp.append(" milk");
                if (t.scrimTop >= .86f) clamp.append(" top");
                if (t.scrimBottom >= .86f) clamp.append(" bottom");
                if (t.scrimRow >= .86f) clamp.append(" row");
                if (t.scrimSettings >= .86f) clamp.append(" settings");
                if (clamp.length() > 0) flagged++;
                n++;
                System.out.printf("%-34s %5.2f %5.3f %5.2f %5.2f %5.2f %5.2f %5.2f %5.2f %5.2f %5.2f %s%n", p[0], t.meanL, t.frost,
                        t.dock, t.gear, t.sheet, t.milk, t.scrimTop, t.scrimBottom, t.scrimRow, t.scrimSettings, clamp);
            }
        }
        System.out.println(flagged == 0 ? "all " + n + " within limits" : flagged + " of " + n + " hit a clamp (contrast target not reached there)");
    }
}
