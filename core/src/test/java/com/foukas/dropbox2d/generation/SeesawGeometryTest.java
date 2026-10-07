package com.foukas.dropbox2d.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // --- Trapdoor geometry (docs/designs/seesaw-trapdoor.md) ---

    private static final float THICKNESS = 0.35f; // GameplayScreen.PLATFORM_THICKNESS
    private static final float BALL_DIAMETER = 0.8f; // 2 * GameplayScreen.BALL_RADIUS
    private static final float MARGIN = 0.1f;

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    @Test
    void levelPlankHasNoClearance() {
        // At 0 tilt the plank sits flush in the hole: clearance is just
        // minus the full thickness.
        assertEquals(-THICKNESS, SeesawGeometry.clearanceAt(2.5f, 0f, THICKNESS), 1e-5f);
    }

    @Test
    void verticalPlankClearanceIsHalfHoleMinusHalfThickness() {
        assertEquals(1.25f - 0.175f, SeesawGeometry.clearanceAt(2.5f, rad(90f), THICKNESS), 1e-5f);
    }

    // Regression for the design doc's core correction: a hole the shipped
    // flanking plank (half-length 0.9) could span never passes the ball,
    // even vertical.
    @Test
    void shippedSizeHoleNeverPassesTheBall() {
        assertFalse(SeesawGeometry.passesBall(1.6f, rad(90f), THICKNESS, BALL_DIAMETER, 0f));
        assertTrue(Float.isNaN(SeesawGeometry.minPassAngle(1.6f, THICKNESS, BALL_DIAMETER, MARGIN)));
    }

    // The design doc's expected-fail control (eng review T0, R3-1).
    @Test
    void controlHoleAtSeventyDegreesDoesNotPassTheBall() {
        assertEquals(0.705f, SeesawGeometry.clearanceAt(2.0f, rad(70f), THICKNESS), 0.005f);
        assertFalse(SeesawGeometry.passesBall(2.0f, rad(70f), THICKNESS, BALL_DIAMETER, 0f));
    }

    // Rows of the design doc's minimum-W table: each W is the smallest hole
    // that passes at that angle with the 0.1 margin.
    @Test
    void minPassAngleMatchesTheDesignDocTable() {
        assertEquals(60f, (float) Math.toDegrees(SeesawGeometry.minPassAngle(2.68f, THICKNESS, BALL_DIAMETER, MARGIN)), 0.5f);
        assertEquals(75f, (float) Math.toDegrees(SeesawGeometry.minPassAngle(2.32f, THICKNESS, BALL_DIAMETER, MARGIN)), 0.6f);
        assertEquals(85f, (float) Math.toDegrees(SeesawGeometry.minPassAngle(2.19f, THICKNESS, BALL_DIAMETER, MARGIN)), 1.0f);
    }

    @Test
    void minPassAngleIsTheExactBoundaryOfPassesBall() {
        float theta = SeesawGeometry.minPassAngle(2.5f, THICKNESS, BALL_DIAMETER, MARGIN);
        assertTrue(SeesawGeometry.passesBall(2.5f, theta, THICKNESS, BALL_DIAMETER, MARGIN));
        assertFalse(SeesawGeometry.passesBall(2.5f, theta - rad(0.05f), THICKNESS, BALL_DIAMETER, MARGIN));
    }

    @Test
    void trapdoorFitsWhenSpanCoversFillerHoleAndLip() {
        assertTrue(SeesawGeometry.fitsTrapdoorGeometry(4.0f, 2.5f, 0.25f, 0.25f));
    }

    @Test
    void trapdoorDoesNotFitWithoutRoomForTheLip() {
        // Fits filler + hole but not the lip: the old check had no lip
        // term and would have accepted this.
        assertFalse(SeesawGeometry.fitsTrapdoorGeometry(2.875f, 2.5f, 0.25f, 0.25f));
    }

    @Test
    void trapdoorExactBoundaryFits() {
        // 0.25 + 2.5 + 0.25 = 3.0 exactly in binary floating point.
        assertTrue(SeesawGeometry.fitsTrapdoorGeometry(3.0f, 2.5f, 0.25f, 0.25f));
    }

    @Test
    void trapdoorRejectsTheGuaranteedMinimumSpan() {
        assertFalse(SeesawGeometry.fitsTrapdoorGeometry(0.5f, 2.3f, 0.3f, 0.3f),
                "the guaranteed-minimum 0.5-unit span must be rejected, not silently accepted");
    }
}
