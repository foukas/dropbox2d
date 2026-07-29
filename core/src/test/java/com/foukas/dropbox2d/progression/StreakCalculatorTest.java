package com.foukas.dropbox2d.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakCalculatorTest {

    @Test
    void firstEverSessionStartsStreakAtOne() {
        long today = 100L;
        assertEquals(1, StreakCalculator.computeStreak(StreakCalculator.NEVER_PLAYED, today, 0));
    }

    @Test
    void reopeningTheSameDayDoesNotDoubleCount() {
        long today = 100L;
        assertEquals(5, StreakCalculator.computeStreak(today, today, 5));
    }

    @Test
    void playingTheNextDayExtendsTheStreak() {
        long today = 100L;
        assertEquals(6, StreakCalculator.computeStreak(today - 1, today, 5));
    }

    @Test
    void skippingADayResetsTheStreak() {
        long today = 100L;
        assertEquals(1, StreakCalculator.computeStreak(today - 2, today, 10));
    }

    @Test
    void skippingManyDaysResetsTheStreak() {
        long today = 100L;
        assertEquals(1, StreakCalculator.computeStreak(today - 30, today, 10));
    }

    @Test
    void dayNumberIsStableWithinTheSameDay() {
        long millisPerDay = 1000L * 60 * 60 * 24;
        long baseDay = 12345L;
        long startOfDay = baseDay * millisPerDay;
        long endOfDay = startOfDay + millisPerDay - 1;

        assertEquals(baseDay, StreakCalculator.dayNumber(startOfDay));
        assertEquals(baseDay, StreakCalculator.dayNumber(endOfDay));
    }

    @Test
    void dayNumberIncrementsAcrossADayBoundary() {
        long millisPerDay = 1000L * 60 * 60 * 24;
        long baseDay = 12345L;
        long startOfNextDay = (baseDay + 1) * millisPerDay;

        assertEquals(baseDay + 1, StreakCalculator.dayNumber(startOfNextDay));
    }
}
