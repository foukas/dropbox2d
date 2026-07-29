package com.foukas.dropbox2d.progression;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Local persistence: best depth ever reached, and the daily play streak.
 * Backed by libGDX Preferences, which handles the platform-specific
 * storage (SharedPreferences on Android, a properties file on desktop)
 * transparently. The streak-day-diff RULE lives in StreakCalculator
 * (pure, tested); this class is just the persistence plumbing around it. */
public class PlayerProgress {
    private static final String PREFS_NAME = "dropbox2d-progress";
    private static final String KEY_BEST_DEPTH = "bestDepth";
    private static final String KEY_STREAK = "streak";
    private static final String KEY_LAST_PLAYED_DAY = "lastPlayedDay";

    private final Preferences prefs;
    private float bestDepth;
    private int streak;

    public PlayerProgress() {
        this(Gdx.app.getPreferences(PREFS_NAME));
    }

    /** Package-visible so tests can inject an in-memory fake instead of
     * requiring a real Application context (Gdx.app.getPreferences()
     * doesn't work outside a running libGDX app). */
    PlayerProgress(Preferences prefs) {
        this.prefs = prefs;
        bestDepth = prefs.getFloat(KEY_BEST_DEPTH, 0f);
        streak = prefs.getInteger(KEY_STREAK, 0);
    }

    /** Call once per app session (not per run) -- updates and persists the
     * daily streak based on when the player last opened the game. */
    public void recordSessionStart(long nowEpochMillis) {
        long today = StreakCalculator.dayNumber(nowEpochMillis);
        long lastPlayedDay = prefs.getLong(KEY_LAST_PLAYED_DAY, StreakCalculator.NEVER_PLAYED);
        streak = StreakCalculator.computeStreak(lastPlayedDay, today, streak);
        prefs.putLong(KEY_LAST_PLAYED_DAY, today);
        prefs.putInteger(KEY_STREAK, streak);
        prefs.flush();
    }

    public float getBestDepth() {
        return bestDepth;
    }

    public int getStreak() {
        return streak;
    }

    /** Updates and persists a new best if depth beats the current one.
     * Returns true if a new best was set (useful for triggering a
     * "new best!" celebration). */
    public boolean reportDepth(float depth) {
        if (depth > bestDepth) {
            bestDepth = depth;
            prefs.putFloat(KEY_BEST_DEPTH, bestDepth);
            prefs.flush();
            return true;
        }
        return false;
    }
}
