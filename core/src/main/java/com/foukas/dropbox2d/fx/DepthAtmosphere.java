package com.foukas.dropbox2d.fx;

import com.badlogic.gdx.math.MathUtils;
import com.foukas.dropbox2d.progression.SkinTier;

/**
 * Pure depth-to-saturation mapping (plan-eng-review Next Step 8) -- resolves
 * the design doc's Open Question of "linear ramp vs. stepped per SkinTier"
 * with a continuous ramp between each tier's unlock threshold, driven
 * entirely by SkinTier's own thresholds (no separate magic-number table to
 * keep in sync). No Gdx dependency, matching this codebase's established
 * pattern (GapReachabilityValidator, StreakCalculator) of extracting
 * Box2D/Gdx-coupled logic into pure, testable functions.
 */
public final class DepthAtmosphere {

    private DepthAtmosphere() {
    }

    /** Returns a 0..1 intensity: 0 at the surface, 1 once depth reaches
     * SkinTier.DIAMOND's unlock threshold and beyond. Ramps linearly within
     * each tier's depth bracket, using that tier's and the next tier's own
     * unlock depths as the ramp's endpoints -- continuous across tier
     * boundaries (no jump), since each tier's upper endpoint exactly equals
     * the next tier's lower endpoint. */
    public static float saturationFor(float depth, SkinTier tier) {
        float maxDepth = SkinTier.DIAMOND.getUnlockDepth();
        float lowerDepth = tier.getUnlockDepth();
        SkinTier nextTier = nextTier(tier);
        float upperDepth = nextTier == tier ? maxDepth : nextTier.getUnlockDepth();

        float lowerSaturation = MathUtils.clamp(lowerDepth / maxDepth, 0f, 1f);
        if (upperDepth <= lowerDepth) {
            // Already at (or somehow past) the top tier's bracket.
            return lowerSaturation;
        }

        float upperSaturation = MathUtils.clamp(upperDepth / maxDepth, 0f, 1f);
        float progress = MathUtils.clamp((depth - lowerDepth) / (upperDepth - lowerDepth), 0f, 1f);
        return MathUtils.lerp(lowerSaturation, upperSaturation, progress);
    }

    private static SkinTier nextTier(SkinTier tier) {
        SkinTier[] values = SkinTier.values();
        int next = tier.ordinal() + 1;
        return next < values.length ? values[next] : tier;
    }
}
