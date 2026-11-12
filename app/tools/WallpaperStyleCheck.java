package io.github.rowesk.fresco;

import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Checks the wallpaper rotation on the Mac, without a device: prints which file each style shows at
 * some awkward moments, then asserts the rules (December art only in December, the night after midnight
 * keeps its date, Random holds for a slot and re-picks for the next). Run it with tools/check-wallpapers.sh.
 *
 * "shows" is what the app would load from this build: a key whose set isn't packed becomes the nature
 * photo for the same moment, as in Wallpapers.keyFor.
 */
public final class WallpaperStyleCheck {
    private static File assets;
    private static int failures;

    public static void main(String[] args) {
        assets = new File(args.length > 0 ? args[0] : "build/assets/wallpapers");
        int y = 2026;
        Calendar[] moments = {
                at(y, Calendar.NOVEMBER, 30, 23, 0),
                at(y, Calendar.DECEMBER, 1, 2, 0),
                at(y, Calendar.DECEMBER, 1, 7, 0),
                at(y, Calendar.DECEMBER, 25, 12, 0),
                at(y, Calendar.DECEMBER, 31, 22, 0),
                at(y + 1, Calendar.JANUARY, 1, 2, 0),
                at(y + 1, Calendar.JANUARY, 1, 7, 0),
                at(y, Calendar.NOVEMBER, 10, 18, 0),
        };
        System.out.printf("%-22s %-9s %-10s %-26s %s%n", "moment", "style", "random→", "key", "shows (this build)");
        java.text.SimpleDateFormat label = new java.text.SimpleDateFormat("EEE d MMM yyyy HH:mm", java.util.Locale.UK);
        for (Calendar c : moments) {
            for (int s = 0; s < WallpaperStyle.IDS.length; s++) {
                String key = WallpaperStyle.key(s, c);
                String r = s == WallpaperStyle.RANDOM ? WallpaperStyle.IDS[WallpaperStyle.resolve(s, c)] : "";
                System.out.printf("%-22s %-9s %-10s %-26s %s%n", s == 0 ? label.format(c.getTime()) : "", WallpaperStyle.IDS[s], r, key, shows(key, c));
            }
        }

        System.out.println();
        // December art only from 1 to 31 December, the night after 31 December included.
        expect(art(at(y, Calendar.NOVEMBER, 30, 23, 0)).startsWith("art/everyday/night-"), "30 Nov 23:00 is everyday art");
        expect(art(at(y, Calendar.DECEMBER, 1, 2, 0)).startsWith("art/everyday/night-"), "1 Dec 02:00 is still 30 Nov's night");
        expect(art(at(y, Calendar.DECEMBER, 1, 7, 0)).startsWith("art/december/morning-"), "1 Dec 07:00 is December art");
        expect(art(at(y + 1, Calendar.JANUARY, 1, 2, 0)).equals(art(at(y, Calendar.DECEMBER, 31, 22, 0))), "1 Jan 02:00 is 31 Dec's night");
        expect(art(at(y + 1, Calendar.JANUARY, 1, 2, 0)).startsWith("art/december/night-"), "1 Jan 02:00 is December art");
        expect(art(at(y + 1, Calendar.JANUARY, 1, 7, 0)).startsWith("art/everyday/morning-"), "1 Jan 07:00 is everyday art");
        // Rotation formulas.
        Calendar d = at(y, Calendar.DECEMBER, 1, 7, 0);
        expect(art(d).equals("art/december/morning-" + (1 + 1 % 2)), "December painting = 1 + (day of month mod 2)");
        Calendar e = at(y, Calendar.NOVEMBER, 10, 18, 0);
        expect(art(e).equals("art/everyday/evening-" + (1 + e.get(Calendar.DAY_OF_YEAR) % 5)), "everyday painting = 1 + (day of year mod 5)");
        // Every everyday painting comes round in 5 days; consecutive days differ.
        java.util.HashSet<String> five = new java.util.HashSet<>();
        for (int i = 0; i < 5; i++) five.add(art(at(y, Calendar.MARCH, 1 + i, 8, 0)));
        expect(five.size() == 5, "5 consecutive mornings show all 5 everyday paintings");
        // No December art outside December, whatever the style, all year round.
        boolean leak = false;
        for (Calendar c = at(y, Calendar.JANUARY, 1, 6, 30); c.get(Calendar.YEAR) == y; c.add(Calendar.HOUR_OF_DAY, 1)) {
            boolean dec = WallpaperStyle.slotDate(c).get(Calendar.MONTH) == Calendar.DECEMBER;
            for (int s = 0; s < WallpaperStyle.IDS.length; s++) {
                if (!dec && WallpaperStyle.key(s, c).startsWith("art/december/")) leak = true;
                if (dec && WallpaperStyle.key(s, c).startsWith("art/everyday/")) leak = true;
            }
        }
        expect(!leak, "December art exactly when the slot's date is in December (every hour of " + y + ", every style)");
        // Nature keeps the original naming: night belongs to the day it starts on.
        expect(WallpaperStyle.key(WallpaperStyle.NATURE, at(y, Calendar.NOVEMBER, 10, 2, 0)).equals("nature/night-mon"), "Tue 02:00 nature is Monday's night");
        // Random: one style for a whole slot, across "reboots" (fresh Calendars), and a new draw per slot.
        boolean stable = true;
        for (int day = 1; day <= 28; day++) {
            int[][] ranges = {{6, 10}, {11, 16}, {17, 20}, {21, 29}};
            for (int[] r : ranges) {
                int first = -1;
                for (int h = r[0]; h <= r[1]; h++) {
                    for (int min = 0; min < 60; min += 15) {
                        Calendar c = at(y, Calendar.FEBRUARY, day, 0, 0);
                        c.add(Calendar.MINUTE, h * 60 + min);
                        int s = WallpaperStyle.resolve(WallpaperStyle.RANDOM, c);
                        if (first < 0) first = s;
                        else if (s != first) stable = false;
                    }
                }
            }
        }
        expect(stable, "Random holds one style for every minute of a slot (night 21:00–05:59 included)");
        int[] count = new int[4];
        int changes = 0, prev = -1;
        for (Calendar c = at(y, Calendar.JANUARY, 1, 8, 0); c.get(Calendar.YEAR) == y; c.add(Calendar.HOUR_OF_DAY, 1)) {
            if (c.get(Calendar.HOUR_OF_DAY) != 8 && c.get(Calendar.HOUR_OF_DAY) != 13
                    && c.get(Calendar.HOUR_OF_DAY) != 19 && c.get(Calendar.HOUR_OF_DAY) != 23) continue;
            int s = WallpaperStyle.resolve(WallpaperStyle.RANDOM, c);
            count[s]++;
            if (prev >= 0 && s != prev) changes++;
            prev = s;
        }
        int slots = count[0] + count[1] + count[2] + count[3];
        System.out.printf("Random over %d slots of %d: nature %d, art %d, abstract %d, space %d; style changed at %d of %d slot turns%n",
                slots, y, count[0], count[1], count[2], count[3], changes, slots - 1);
        boolean even = true;
        for (int n : count) even &= n > slots / 4 * 0.85 && n < slots / 4 * 1.15;
        expect(even, "Random draws each style about a quarter of the time");
        System.out.println(failures == 0 ? "all checks passed" : failures + " check(s) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String art(Calendar c) {
        return WallpaperStyle.key(WallpaperStyle.ART, c);
    }

    private static String shows(String key, Calendar c) {
        return new File(assets, key + ".webp").isFile() ? key : WallpaperStyle.natureKey(c) + "  (" + key.substring(0, key.indexOf('/')) + " not packed)";
    }

    private static Calendar at(int y, int month, int day, int h, int m) {
        return new GregorianCalendar(y, month, day, h, m);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) failures++;
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
    }
}
