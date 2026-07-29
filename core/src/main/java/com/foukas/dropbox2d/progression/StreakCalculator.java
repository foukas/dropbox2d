package com.foukas.dropbox2d.progression;

/**
 * Pure day-diff streak logic, no libGDX Preferences dependency -- keeps
 * the actual rule (what counts as "continued the streak" vs "broke it")
 * testable without a real Application context. Days are plain long day
 * numbers (epoch millis / millis-per-day), not java.time -- minSdk 24
 * predates java.time (added in API 26) and this project isn't carrying
 * desugaring just for date arithmetic this simple.
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    public static final long NEVER_PLAYED = -1L;

    public static long dayNumber(long epochMillis) {
        return epochMillis / (1000L * 60 * 60 * 24);
    }

    /** Given the day the player last played, today's day number, and their
     * streak going into this session, returns the streak coming out of it.
     * Same day as last play -> unchanged (don't double-count re-opening
     * the app). Exactly one day later -> extended. Any bigger gap (or
     * never played before) -> resets to 1. */
    public static int computeStreak(long lastPlayedDay, long today, int previousStreak) {
        if (lastPlayedDay == today) {
            return Math.max(previousStreak, 1);
        }
        if (lastPlayedDay == today - 1) {
            return previousStreak + 1;
        }
        return 1;
    }
}
