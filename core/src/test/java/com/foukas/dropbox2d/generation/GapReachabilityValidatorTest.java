package com.foukas.dropbox2d.generation;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapReachabilityValidatorTest {

    private static final float WORLD_WIDTH = 9f;

    @Test
    void fullWidthGapIsAlwaysReachable() {
        assertTrue(GapReachabilityValidator.isReachable(0f, WORLD_WIDTH, WORLD_WIDTH, 1f, 0.01f));
    }

    @Test
    void degenerateInputsAreRejected() {
        assertFalse(GapReachabilityValidator.isReachable(1f, 0f, WORLD_WIDTH, 6f, 1f), "zero-width gap");
        assertFalse(GapReachabilityValidator.isReachable(-1f, 2f, WORLD_WIDTH, 6f, 1f), "negative start");
        assertFalse(GapReachabilityValidator.isReachable(8f, 3f, WORLD_WIDTH, 6f, 1f), "extends past world edge");
        assertFalse(GapReachabilityValidator.isReachable(2f, 2f, WORLD_WIDTH, 6f, 0f), "zero time to fall");
        assertFalse(GapReachabilityValidator.isReachable(2f, 2f, WORLD_WIDTH, 0f, 1f), "zero max speed");
    }

    @Test
    void edgePlacedGapIsHarderThanCenteredGapOfSameWidth() {
        float gapWidth = 2.4f;
        float edgeStart = 0f;
        float centeredStart = (WORLD_WIDTH - gapWidth) / 2f;

        float edgeDistance = GapReachabilityValidator.worstCaseDistanceToGap(edgeStart, gapWidth, WORLD_WIDTH);
        float centeredDistance = GapReachabilityValidator.worstCaseDistanceToGap(centeredStart, gapWidth, WORLD_WIDTH);

        assertTrue(edgeDistance > centeredDistance,
            "an edge-placed gap should have a strictly larger worst-case distance than a centered gap of the same width");
    }

    @Test
    void centeredGapIsSymmetric() {
        float gapWidth = 2.4f;
        float centeredStart = (WORLD_WIDTH - gapWidth) / 2f;
        float distance = GapReachabilityValidator.worstCaseDistanceToGap(centeredStart, gapWidth, WORLD_WIDTH);
        // A perfectly centered gap is equidistant from both walls -- the
        // worst case should equal that shared distance exactly.
        assertEquals((WORLD_WIDTH - gapWidth) / 2f, distance, 0.001f);
    }

    @RepeatedTest(200)
    void wideningAGapNeverMakesItLessReachable() {
        Random random = new Random();
        float gapStart = random.nextFloat() * (WORLD_WIDTH - 0.5f);
        float narrowWidth = 0.1f + random.nextFloat() * 1f;
        float extraWidth = random.nextFloat() * 2f;

        // Keep both gaps within world bounds.
        float maxWidth = WORLD_WIDTH - gapStart;
        float wideWidth = Math.min(narrowWidth + extraWidth, maxWidth);
        narrowWidth = Math.min(narrowWidth, maxWidth);
        if (wideWidth < narrowWidth) return; // degenerate random draw, skip

        float narrowDistance = GapReachabilityValidator.worstCaseDistanceToGap(gapStart, narrowWidth, WORLD_WIDTH);
        float wideDistance = GapReachabilityValidator.worstCaseDistanceToGap(gapStart, wideWidth, WORLD_WIDTH);

        assertTrue(wideDistance <= narrowDistance,
            "widening a gap (same start) must never increase its worst-case distance: narrow="
                + narrowDistance + " wide=" + wideDistance);
    }

    @RepeatedTest(200)
    void reachabilityIsSymmetricUnderMirroring() {
        Random random = new Random();
        float gapWidth = 0.5f + random.nextFloat() * 3f;
        if (gapWidth > WORLD_WIDTH) return;
        float gapStart = random.nextFloat() * (WORLD_WIDTH - gapWidth);
        float mirroredStart = WORLD_WIDTH - gapStart - gapWidth;

        float distance = GapReachabilityValidator.worstCaseDistanceToGap(gapStart, gapWidth, WORLD_WIDTH);
        float mirroredDistance = GapReachabilityValidator.worstCaseDistanceToGap(mirroredStart, gapWidth, WORLD_WIDTH);

        assertEquals(distance, mirroredDistance, 0.001f,
            "a gap and its mirror image across the world's center must have the same worst-case distance");
    }

    @Test
    void matchesGameTuningConstantsWithSixRowLookahead() {
        // Mirrors DropGame's actual constants: WORLD_WIDTH=9, ROW_SPACING=2.6,
        // GRAVITY_Y=-25, MAX_HORIZONTAL_SPEED=6, 6-row generation lookahead.
        float rowSpacing = 2.6f;
        float gravity = 25f;
        float maxSpeed = 6f;
        float timeToFall = (float) Math.sqrt(2 * (rowSpacing * 6f) / gravity);

        // A gap at the extreme edge of the allowed generation range
        // (matching GAP_MIN_WIDTH/spawn bounds) should be reachable with
        // the 6-row lookahead budget.
        assertTrue(GapReachabilityValidator.isReachable(0.5f, 2.4f, WORLD_WIDTH, maxSpeed, timeToFall),
            "worst-case gap under actual game constants should be reachable given the 6-row lookahead");
    }
}
