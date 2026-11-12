package io.github.rowesk.fresco;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

/** Run the production glass AND sharp-picture solvers, without Android runtime calls. */
public final class GlassCheck {
    private static int at;
    private static double[][][] windows(String[] p, int n) {
        double[][][] out = new double[n][3][3];
        for (int i = 0; i < n; i++) for (int q = 0; q < 3; q++) for (int c = 0; c < 3; c++) out[i][q][c] = Double.parseDouble(p[at++]);
        return out;
    }
    public static void main(String[] args) throws IOException {
        int n = 0, peekNone = 0, rowNone = 0, clockDark = 0, tintedDark = 0, failed = 0;
        System.out.println("wallpaper                           dock sheet rowMilk settings  peek ink/alpha  row ink/alpha clock ink worst/median");
        Wallpapers.Tuning.dockWidth = 880; // accepted four-app fixture
        try (BufferedReader in = new BufferedReader(new FileReader(args[0]))) {
            for (String line; (line = in.readLine()) != null;) {
                String[] p = line.trim().split("\\s+");
                at = 1;
                double[][] grid = new double[576][3];
                for (int i = 0; i < 576; i++) for (int c = 0; c < 3; c++) grid[i][c] = Double.parseDouble(p[at++]);
                Wallpapers.Tuning t = Wallpapers.Tuning.fromSamples(grid, windows(p, 5), windows(p, 26), windows(p, 26), windows(p, 26));
                // Mock fixture endpoints rounded to px: actual app measures its own strings.
                t.solveBottom(531, 571, 347);
                t.solveClock(0);
                if (t.peekScrim == 0) peekNone++;
                if (t.rowScrim == 0) rowNone++;
                if (t.clockDark) clockDark++;
                boolean ok = t.peekContrast >= 4.5 && t.rowTitleContrast >= 3 && t.rowMetaContrast >= 4.5 && t.clockMedian >= 2;
                if (!ok) failed++;
                System.out.printf(Locale.ROOT, "%-35s %.3f %.3f %.3f %.3f     %s %.3f       %s %.3f    %s %.2f/%.2f%s%n",
                        p[0], t.dock, t.sheet, t.rowMilk, t.scrimSettings,
                        t.peekDark ? "dark " : "white", t.peekScrim, t.rowDark ? "dark " : "white", t.rowScrim,
                        t.clockDark ? "dark " : "white", t.clockWorst, t.clockMedian, ok ? "" : " FAIL");
                t.solveClock(.58f * .8f);
                if (t.clockDark) tintedDark++;
                if (t.clockMedian < 2) failed++;
                n++;
            }
        }
        System.out.printf("%d pictures: peek no gradient %d (gradient %d); row no gradient %d (gradient %d).%n", n, peekNone, n - peekNone, rowNone, n - rowNone);
        System.out.printf("Clock: dark %d / white %d; at 80%% bedtime tint: dark %d / white %d. Contrast failures: %d.%n", clockDark, n - clockDark, tintedDark, n - tintedDark, failed);
        if (n == 0 || failed != 0) throw new AssertionError("Picture solver check failed");
    }
}
