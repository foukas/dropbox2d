package com.foukas.dropbox2d.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProgressTest {

    private static final long MILLIS_PER_DAY = 1000L * 60 * 60 * 24;
    private static final long DAY_100 = 100L * MILLIS_PER_DAY;

    @Test
    void freshProgressStartsAtZero() {
        PlayerProgress progress = new PlayerProgress(new FakePreferences());

        assertEquals(0f, progress.getBestDepth());
        assertEquals(0, progress.getStreak());
    }

    @Test
    void reportDepthUpdatesBestOnlyWhenHigher() {
        PlayerProgress progress = new PlayerProgress(new FakePreferences());

        assertTrue(progress.reportDepth(50f));
        assertEquals(50f, progress.getBestDepth());

        assertFalse(progress.reportDepth(30f), "a lower depth must not overwrite a higher best");
        assertEquals(50f, progress.getBestDepth());

        assertTrue(progress.reportDepth(75f));
        assertEquals(75f, progress.getBestDepth());
    }

    @Test
    void bestDepthSurvivesAcrossInstances() {
        FakePreferences prefs = new FakePreferences();
        new PlayerProgress(prefs).reportDepth(120f);

        PlayerProgress reopened = new PlayerProgress(prefs);

        assertEquals(120f, reopened.getBestDepth());
    }

    @Test
    void firstSessionEverSetsStreakToOne() {
        PlayerProgress progress = new PlayerProgress(new FakePreferences());
        progress.recordSessionStart(DAY_100);

        assertEquals(1, progress.getStreak());
    }

    @Test
    void reopeningTheSameDayDoesNotDoubleCountTheStreak() {
        FakePreferences prefs = new FakePreferences();
        PlayerProgress progress = new PlayerProgress(prefs);
        progress.recordSessionStart(DAY_100);
        progress.recordSessionStart(DAY_100 + 1000); // still the same day, later that day

        assertEquals(1, progress.getStreak());
    }

    @Test
    void consecutiveDaysExtendTheStreakAndPersist() {
        FakePreferences prefs = new FakePreferences();
        new PlayerProgress(prefs).recordSessionStart(DAY_100);
        new PlayerProgress(prefs).recordSessionStart(DAY_100 + MILLIS_PER_DAY);
        PlayerProgress thirdSession = new PlayerProgress(prefs);
        thirdSession.recordSessionStart(DAY_100 + 2 * MILLIS_PER_DAY);

        assertEquals(3, thirdSession.getStreak());
    }

    @Test
    void skippingADayResetsTheStreak() {
        FakePreferences prefs = new FakePreferences();
        new PlayerProgress(prefs).recordSessionStart(DAY_100);
        PlayerProgress laterSession = new PlayerProgress(prefs);
        laterSession.recordSessionStart(DAY_100 + 5 * MILLIS_PER_DAY);

        assertEquals(1, laterSession.getStreak());
    }
}
