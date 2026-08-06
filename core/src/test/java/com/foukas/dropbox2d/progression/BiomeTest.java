package com.foukas.dropbox2d.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiomeTest {

    @Test
    void depthZeroResolvesToTheFirstBiomeInRoster() {
        assertEquals(Biome.values()[0], Biome.biomeFor(0f));
    }

    @Test
    void justBelowTheBandBoundaryStaysInTheFirstBiome() {
        assertEquals(Biome.values()[0], Biome.biomeFor(Biome.BAND_SIZE_METERS - 0.01f));
    }

    @Test
    void exactlyAtTheBandBoundaryEntersTheSecondBiome() {
        assertEquals(Biome.values()[1], Biome.biomeFor(Biome.BAND_SIZE_METERS));
    }

    @Test
    void wrapsBackToTheFirstBiomeAfterOneFullRosterSpan() {
        float fullSpan = Biome.BAND_SIZE_METERS * Biome.values().length;
        assertEquals(Biome.values()[0], Biome.biomeFor(fullSpan));
    }

    @Test
    void negativeDepthWrapsCorrectlyInsteadOfReturningANegativeIndex() {
        // floorMod, not %: floor(-1 / BAND_SIZE_METERS) = -1, floorMod(-1, 2) = 1,
        // not Java's raw -1 % 2 = -1 (which would be an invalid array index).
        assertEquals(Biome.values()[1], Biome.biomeFor(-1f));
        // A large negative depth several roster-spans back should land on
        // the same biome as -1f did, not throw or go out of bounds.
        float severalSpansBack = -(Biome.BAND_SIZE_METERS * Biome.values().length * 3) - 1f;
        assertEquals(Biome.values()[1], Biome.biomeFor(severalSpansBack));
    }

    @Test
    void zeroBandSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> Biome.biomeFor(0f, 0f));
    }

    @Test
    void negativeBandSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> Biome.biomeFor(0f, -5f));
    }

    // Regression guard (plan-eng-review Test Review REGRESSION RULE): the
    // first biome's weak-platform chance must keep matching the pre-biome
    // WEAK_PLATFORM_CHANCE constant it replaced in
    // GameplayScreen.spawnNextRow(), or existing difficulty silently
    // changes for players who never leave the first band. 0.35f is that
    // historical value, not an arbitrary choice.
    @Test
    void firstBiomeReproducesTheHistoricalPreBiomeWeakPlatformChance() {
        assertEquals(0.35f, Biome.values()[0].getWeakPlatformChance(), 0.0001f);
    }

    // Enforced constraint (plan-eng-review biome progression step 6, design
    // doc Constraints): damping must only ever INCREASE from the baseline
    // (first roster entry). GapReachabilityValidator's existing
    // MAX_HORIZONTAL_SPEED/timeToFall inputs stay conservative under that
    // constraint without any per-biome physics recomputation -- see step 7.
    // A future biome added with lower damping than baseline would silently
    // reopen that reachability bug; this test fails immediately instead.
    @Test
    void everyBiomesDampingIsAtLeastTheBaselines() {
        float baselineDamping = Biome.values()[0].getLinearDamping();
        for (Biome biome : Biome.values()) {
            assertTrue(biome.getLinearDamping() >= baselineDamping,
                    biome + " has damping " + biome.getLinearDamping()
                            + ", below baseline " + baselineDamping);
        }
    }

    // Moving-platforms design doc, Success Criteria: every biome exposes a
    // movingPlatformChance, and the baseline (first roster entry) is
    // nonzero and low enough that a player who never leaves the first band
    // still sees the "oh, that's new" moment within a reasonable number of
    // rows, not buried at rare odds.
    @Test
    void everyBiomeHasAMovingPlatformChance() {
        for (Biome biome : Biome.values()) {
            assertTrue(biome.getMovingPlatformChance() > 0f,
                    biome + " has a non-positive movingPlatformChance");
        }
    }

    @Test
    void baselineMovingPlatformChanceIsNotBuriedAtRareOdds() {
        float baseline = Biome.values()[0].getMovingPlatformChance();
        assertTrue(baseline >= 0.05f && baseline <= 0.5f,
                "baseline movingPlatformChance " + baseline
                        + " should be visible-but-not-dominant, same posture as weakPlatformChance");
    }
}
