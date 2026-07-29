package com.foukas.dropbox2d.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinTierTest {

    @Test
    void zeroDepthIsDefaultTier() {
        assertEquals(SkinTier.DEFAULT, SkinTier.forDepth(0f));
    }

    @Test
    void depthBelowFirstMilestoneStaysDefault() {
        assertEquals(SkinTier.DEFAULT, SkinTier.forDepth(49.9f));
    }

    @Test
    void depthExactlyAtAMilestoneUnlocksIt() {
        assertEquals(SkinTier.EMERALD, SkinTier.forDepth(50f));
    }

    @Test
    void depthBetweenMilestonesUnlocksTheLowerOne() {
        assertEquals(SkinTier.EMERALD, SkinTier.forDepth(149.9f));
    }

    @Test
    void veryHighDepthUnlocksTheTopTier() {
        assertEquals(SkinTier.DIAMOND, SkinTier.forDepth(10000f));
    }

    @Test
    void tiersAreDeclaredInAscendingThresholdOrder() {
        SkinTier[] tiers = SkinTier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].getUnlockDepth() > tiers[i - 1].getUnlockDepth(),
                "forDepth()'s single-pass scan relies on ascending declaration order");
        }
    }
}
