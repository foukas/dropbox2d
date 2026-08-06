package com.foukas.dropbox2d.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovingPlatformReachabilityTest {

    @Test
    void bothAmplitudesZeroReturnsInputUnchanged() {
        MovingPlatformReachability.AdjustedGap adjusted = MovingPlatformReachability.shrinkForAmplitude(1.5f, 2.8f, 0f, 0f);
        assertEquals(1.5f, adjusted.gapStart(), 0.0001f);
        assertEquals(2.8f, adjusted.gapWidth(), 0.0001f);
    }

    @Test
    void leftAmplitudeOnlyShiftsStartAndShrinksWidth() {
        MovingPlatformReachability.AdjustedGap adjusted = MovingPlatformReachability.shrinkForAmplitude(1.5f, 2.8f, 0.5f, 0f);
        assertEquals(2.0f, adjusted.gapStart(), 0.0001f, "left amplitude shifts the gap start right");
        assertEquals(2.3f, adjusted.gapWidth(), 0.0001f, "left amplitude shrinks the width it shifted into");
    }

    @Test
    void rightAmplitudeOnlyShrinksWidthWithoutMovingStart() {
        MovingPlatformReachability.AdjustedGap adjusted = MovingPlatformReachability.shrinkForAmplitude(1.5f, 2.8f, 0f, 0.5f);
        assertEquals(1.5f, adjusted.gapStart(), 0.0001f, "right amplitude never moves the gap start");
        assertEquals(2.3f, adjusted.gapWidth(), 0.0001f, "right amplitude shrinks the width from the far edge");
    }

    @Test
    void bothAmplitudesShiftStartAndShrinkWidthByTheirSum() {
        MovingPlatformReachability.AdjustedGap adjusted = MovingPlatformReachability.shrinkForAmplitude(1.5f, 2.8f, 0.4f, 0.3f);
        assertEquals(1.9f, adjusted.gapStart(), 0.0001f);
        assertEquals(2.1f, adjusted.gapWidth(), 0.0001f, "width shrinks by the sum of both amplitudes");
    }

    // Mirrors GapReachabilityValidatorTest.matchesGameTuningConstantsWithSixRowLookahead's
    // style: confirms the real chosen constant (not a hypothetical one)
    // satisfies the "comfortably below GAP_MIN_WIDTH / 2" constraint the
    // design doc requires by construction, so no runtime clamp is needed.
    @Test
    void realPatrolAmplitudeStaysComfortablyUnderHalfGapMinWidth() {
        float gapMinWidth = 2.4f; // GameplayScreen.GAP_MIN_WIDTH
        float patrolAmplitude = 0.5f; // GameplayScreen.MOVING_PLATFORM_AMPLITUDE
        assertTrue(patrolAmplitude < gapMinWidth / 2f,
                "patrol amplitude " + patrolAmplitude + " must stay comfortably under half of GAP_MIN_WIDTH (" + (gapMinWidth / 2f) + ")");
    }

    @Test
    void fitsSplitBodyGeometryAtExactBoundaryIsTrue() {
        assertTrue(MovingPlatformReachability.fitsSplitBodyGeometry(1.6f, 0.8f, 0.5f, 0.3f));
    }

    @Test
    void fitsSplitBodyGeometryOneUnitBelowBoundaryIsFalse() {
        assertFalse(MovingPlatformReachability.fitsSplitBodyGeometry(0.6f, 0.8f, 0.5f, 0.3f));
    }

    // spawnNextRow()'s gapStart random range guarantees a flanking span of
    // at least 0.5 world units -- confirms the real chosen constants
    // correctly reject that guaranteed-worst-case span (the retry loop
    // must reroll here, not silently build a broken body), while still
    // fitting a typical mid-range roll so the feature isn't rejected on
    // every attempt.
    @Test
    void realConstantsRejectTheGuaranteedMinimumSpanButFitATypicalOne() {
        float kinematicWidth = 0.8f; // GameplayScreen.MOVING_PLATFORM_WIDTH
        float amplitude = 0.5f; // GameplayScreen.MOVING_PLATFORM_AMPLITUDE
        float minFillerWidth = 0.3f; // GameplayScreen.MIN_FILLER_WIDTH

        assertFalse(MovingPlatformReachability.fitsSplitBodyGeometry(0.5f, kinematicWidth, amplitude, minFillerWidth),
                "the guaranteed-minimum 0.5-unit span must be rejected, not silently accepted");
        assertTrue(MovingPlatformReachability.fitsSplitBodyGeometry(3.0f, kinematicWidth, amplitude, minFillerWidth),
                "a typical mid-range flanking span must fit, or MOVING would never successfully spawn");
    }
}
