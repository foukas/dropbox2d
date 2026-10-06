package com.foukas.dropbox2d.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seesaw design doc Next Step 10 -- mirrors MovingPlatformReachabilityTest's
 * fitsSplitBodyGeometry() tests. */
class SeesawGeometryTest {

    @Test
    void aSpanComfortablyWiderThanThePlankFits() {
        assertTrue(SeesawGeometry.fitsSeesawGeometry(4.0f, 0.75f, 0.5f));
    }

    @Test
    void aSpanNarrowerThanThePlankDoesNotFit() {
        assertFalse(SeesawGeometry.fitsSeesawGeometry(1.0f, 0.75f, 0.5f));
    }

    @Test
    void exactBoundaryFits() {
        // 2 * 0.75 + 0.5 = 2.0 exactly -- all three values are exact in
        // binary floating point, so this is a true boundary, not a
        // rounding coincidence.
        assertTrue(SeesawGeometry.fitsSeesawGeometry(2.0f, 0.75f, 0.5f));
    }

    // Mirrors MovingPlatformReachabilityTest.realConstantsRejectTheGuaranteedMinimumSpanButFitATypicalOne:
    // the real chosen constants must reject spawnNextRow()'s guaranteed-
    // minimum 0.5-unit span (retry loop rerolls, never builds a broken
    // seesaw) while still fitting a typical mid-range span, or SEESAW
    // would never actually spawn.
    @Test
    void realConstantsRejectTheGuaranteedMinimumSpanButFitATypicalOne() {
        float plankHalfLength = 0.9f; // GameplayScreen.SEESAW_HALF_LENGTH
        float minFillerWidth = 0.3f; // GameplayScreen.MIN_FILLER_WIDTH

        assertFalse(SeesawGeometry.fitsSeesawGeometry(0.5f, plankHalfLength, minFillerWidth),
                "the guaranteed-minimum 0.5-unit span must be rejected, not silently accepted");
        assertTrue(SeesawGeometry.fitsSeesawGeometry(3.0f, plankHalfLength, minFillerWidth),
                "a typical mid-range flanking span must fit, or SEESAW would never successfully spawn");
    }
}
