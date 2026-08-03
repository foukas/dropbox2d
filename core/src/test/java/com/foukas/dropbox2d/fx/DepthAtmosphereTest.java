package com.foukas.dropbox2d.fx;

import com.foukas.dropbox2d.progression.SkinTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepthAtmosphereTest {

    private static final float DELTA = 0.001f;

    @Test
    void surfaceIsFullyDark() {
        assertEquals(0f, DepthAtmosphere.saturationFor(0f, SkinTier.DEFAULT), DELTA);
    }

    @Test
    void midwayThroughDefaultBracketIsHalfOfItsRange() {
        // DEFAULT [0, 50) ramps from 0 to 50/300 = 0.1667.
        assertEquals(50f / 300f / 2f, DepthAtmosphere.saturationFor(25f, SkinTier.DEFAULT), DELTA);
    }

    @Test
    void continuousAcrossTheDefaultEmeraldBoundary() {
        // Same depth (50), evaluated against the tier just below the
        // threshold and the tier just crossed into, must agree -- no jump.
        float atEndOfDefault = DepthAtmosphere.saturationFor(50f, SkinTier.DEFAULT);
        float atStartOfEmerald = DepthAtmosphere.saturationFor(50f, SkinTier.EMERALD);
        assertEquals(atEndOfDefault, atStartOfEmerald, DELTA);
        assertEquals(50f / 300f, atEndOfDefault, DELTA);
    }

    @Test
    void continuousAcrossTheEmeraldAmethystBoundary() {
        float atEndOfEmerald = DepthAtmosphere.saturationFor(150f, SkinTier.EMERALD);
        float atStartOfAmethyst = DepthAtmosphere.saturationFor(150f, SkinTier.AMETHYST);
        assertEquals(atEndOfEmerald, atStartOfAmethyst, DELTA);
        assertEquals(150f / 300f, atEndOfEmerald, DELTA);
    }

    @Test
    void continuousAcrossTheAmethystDiamondBoundary() {
        float atEndOfAmethyst = DepthAtmosphere.saturationFor(300f, SkinTier.AMETHYST);
        float atStartOfDiamond = DepthAtmosphere.saturationFor(300f, SkinTier.DIAMOND);
        assertEquals(atEndOfAmethyst, atStartOfDiamond, DELTA);
        assertEquals(1f, atEndOfAmethyst, DELTA);
    }

    @Test
    void diamondBracketStaysPinnedAtMaxRegardlessOfHowFarPast() {
        assertEquals(1f, DepthAtmosphere.saturationFor(300f, SkinTier.DIAMOND), DELTA);
        assertEquals(1f, DepthAtmosphere.saturationFor(10_000f, SkinTier.DIAMOND), DELTA);
    }

    @Test
    void neverExceedsOneOrDropsBelowZero() {
        for (SkinTier tier : SkinTier.values()) {
            assertEquals(true, DepthAtmosphere.saturationFor(-50f, tier) >= 0f);
            assertEquals(true, DepthAtmosphere.saturationFor(100_000f, tier) <= 1f);
        }
    }
}
