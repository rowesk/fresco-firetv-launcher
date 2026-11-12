package io.github.rowesk.fresco;

import java.util.Calendar;

/**
 * Which picture a wallpaper style shows at a given moment. Plain Java with no Android types, so the
 * rotation can be checked on the Mac (tools/WallpaperStyleCheck.java).
 *
 * Keys are asset paths under `wallpapers/` without the extension:
 *   nature/<slot>-<day>, abstract/<slot>-<day>, space/<slot>-<day>   one per time of day per weekday
 *   art/everyday/<slot>-<n>   n = 1 + (day of year mod 5)
 *   art/december/<slot>-<n>   n = 1 + (day of month mod 2), 1–31 December only
 * A night belongs to the date it starts on, so 02:00 is still the previous evening's date: its picture
 * doesn't change at midnight, and the night after 31 December stays on the December set.
 */
final class WallpaperStyle {
    static final int NATURE = 0, ART = 1, ABSTRACT = 2, SPACE = 3, RANDOM = 4;
    /** Stored in the settings file; the order is the order of the settings sub-page. */
    static final String[] IDS = {"nature", "art", "abstract", "space", "random"};
    static final String[] NAMES = {"Nature", "Art", "Abstract", "Space", "Random"};

    static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;
    static final String[] SLOTS = {"morning", "day", "evening", "night"};
    static final String[] DAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    private WallpaperStyle() {}

    static int fromId(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return NATURE;
    }

    /** Morning 06–11, day 11–17, evening 17–21, night 21–06. */
    static int period(Calendar c) {
        int h = c.get(Calendar.HOUR_OF_DAY);
        if (h >= 6 && h < 11) return MORNING;
        if (h >= 11 && h < 17) return DAY;
        if (h >= 17 && h < 21) return EVENING;
        return NIGHT;
    }

    /** The date the current time of day belongs to: before 06:00 it is the night of the day before. */
    static Calendar slotDate(Calendar now) {
        Calendar d = (Calendar) now.clone();
        if (d.get(Calendar.HOUR_OF_DAY) < 6) d.add(Calendar.DAY_OF_YEAR, -1);
        return d;
    }

    /** Random settles on one concrete style per time of day; the others are themselves. */
    static int resolve(int style, Calendar now) {
        if (style != RANDOM) return style;
        Calendar d = slotDate(now);
        // Seeded from the date and the slot, so the pick holds across Home presses and restarts until
        // the next time of day. splitmix64, because java.util.Random's first draw from neighbouring
        // seeds is strongly correlated.
        long z = (d.get(Calendar.YEAR) * 400L + d.get(Calendar.DAY_OF_YEAR)) * 4 + period(now);
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (int) ((z >>> 33) % 4);
    }

    static String key(int style, Calendar now) {
        int s = resolve(style, now);
        String slot = SLOTS[period(now)];
        Calendar d = slotDate(now);
        if (s == ART) {
            if (d.get(Calendar.MONTH) == Calendar.DECEMBER) {
                return "art/december/" + slot + "-" + (1 + d.get(Calendar.DAY_OF_MONTH) % 2);
            }
            return "art/everyday/" + slot + "-" + (1 + d.get(Calendar.DAY_OF_YEAR) % 5);
        }
        int day = (d.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Calendar.MONDAY = 2 → 0
        return IDS[s] + "/" + slot + "-" + DAYS[day];
    }

    /** What a set that isn't packed falls back to: the nature photo for the same moment. */
    static String natureKey(Calendar now) {
        return key(NATURE, now);
    }

    static int periodOfKey(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        for (int i = 0; i < SLOTS.length; i++) if (name.startsWith(SLOTS[i] + "-")) return i;
        return NIGHT;
    }
}
