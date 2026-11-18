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
        // December art from 1 December to 6 January (Twelfth Night), the night after 6 January included.
        expect(art(at(y, Calendar.NOVEMBER, 30, 23, 0)).startsWith("art/everyday/night-"), "30 Nov 23:00 is everyday art");
        expect(art(at(y, Calendar.DECEMBER, 1, 2, 0)).startsWith("art/everyday/night-"), "1 Dec 02:00 is still 30 Nov's night");
        expect(art(at(y, Calendar.DECEMBER, 1, 7, 0)).startsWith("art/december/morning-"), "1 Dec 07:00 is December art");
        expect(art(at(y + 1, Calendar.JANUARY, 1, 2, 0)).equals(art(at(y, Calendar.DECEMBER, 31, 22, 0))), "1 Jan 02:00 is 31 Dec's night");
        expect(art(at(y + 1, Calendar.JANUARY, 1, 7, 0)).startsWith("art/december/morning-"), "1 Jan 07:00 is still December art");
        expect(art(at(y + 1, Calendar.JANUARY, 7, 2, 0)).equals(art(at(y + 1, Calendar.JANUARY, 6, 22, 0))), "7 Jan 02:00 is 6 Jan's night");
        expect(art(at(y + 1, Calendar.JANUARY, 7, 2, 0)).startsWith("art/december/night-"), "7 Jan 02:00 is December art");
        expect(art(at(y + 1, Calendar.JANUARY, 7, 7, 0)).startsWith("art/everyday/morning-"), "7 Jan 07:00 is everyday art");
        // Rotation formulas.
        expect(art(at(y, Calendar.DECEMBER, 1, 7, 0)).equals("art/december/morning-1"), "1 Dec morning is December painting 1");
        expect(art(at(y, Calendar.DECEMBER, 31, 13, 0)).equals("art/december/day-" + (1 + 30 % 3)), "December painting = 1 + (days since 1 Dec mod count)");
        expect(!art(at(y + 1, Calendar.JANUARY, 1, 13, 0)).equals(art(at(y, Calendar.DECEMBER, 31, 13, 0))), "the turn of the year changes the painting");
        // Each time of day comes round all its December paintings, never shows one two days running, and every one is packed.
        for (int p = 0; p < 4; p++) {
            int hour = new int[]{8, 13, 18, 23}[p], n = WallpaperStyle.DECEMBER_ART[p];
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            boolean packed = true, repeats = false;
            String prev = null;
            for (Calendar c = at(y, Calendar.DECEMBER, 1, hour, 0); WallpaperStyle.christmasDay(c) >= 0; c.add(Calendar.DAY_OF_YEAR, 1)) {
                String k = art(c);
                seen.add(k);
                if (!shows(k, c).equals(k)) packed = false;
                if (k.equals(prev)) repeats = true;
                prev = k;
            }
            expect(seen.size() == n, "the season shows all " + n + " December " + WallpaperStyle.SLOTS[p] + " paintings");
            expect(!repeats, "no December " + WallpaperStyle.SLOTS[p] + " painting two days running, 1 Dec to 6 Jan");
            expect(packed, "every December " + WallpaperStyle.SLOTS[p] + " painting is packed in this build");
        }
        Calendar e = at(y, Calendar.NOVEMBER, 10, 18, 0);
        expect(art(e).equals("art/everyday/evening-" + (1 + e.get(Calendar.DAY_OF_YEAR) % 14)), "everyday evening painting = 1 + (day of year mod 14)");
        // Each time of day comes round all its everyday paintings in that many days, and every one is packed.
        String[] slotHour = {"08", "13", "18", "23"};
        for (int p = 0; p < 4; p++) {
            int n = WallpaperStyle.EVERYDAY_ART[p];
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            boolean allPacked = true;
            for (int i = 0; i < n; i++) {
                String k = art(at(y, Calendar.MARCH, 1 + i, Integer.parseInt(slotHour[p]), 0));
                seen.add(k);
                if (!shows(k, at(y, Calendar.MARCH, 1 + i, Integer.parseInt(slotHour[p]), 0)).equals(k)) allPacked = false;
            }
            expect(seen.size() == n, n + " consecutive " + WallpaperStyle.SLOTS[p] + "s show all " + n + " everyday paintings");
            expect(allPacked, "every everyday " + WallpaperStyle.SLOTS[p] + " painting is packed in this build");
        }
        // December art exactly in its season, whatever the style, all year round.
        boolean leak = false;
        for (Calendar c = at(y, Calendar.JANUARY, 1, 6, 30); c.get(Calendar.YEAR) == y; c.add(Calendar.HOUR_OF_DAY, 1)) {
            boolean dec = WallpaperStyle.christmasDay(WallpaperStyle.slotDate(c)) >= 0;
            for (int s = 0; s < WallpaperStyle.IDS.length; s++) {
                if (!dec && WallpaperStyle.key(s, c).startsWith("art/december/")) leak = true;
                if (dec && WallpaperStyle.key(s, c).startsWith("art/everyday/")) leak = true;
            }
        }
        expect(!leak, "December art exactly when the slot's date is 1 Dec–6 Jan (every hour of " + y + ", every style)");
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
