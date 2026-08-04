package com.foukas.dropbox2d.progression;

import com.badlogic.gdx.graphics.Color;

/** Depth-cyclic content variety (plan-eng-review, 2026-08-04) -- background
 * palette, hazard mix, and ball physics all shift by depth-band, and the
 * roster cycles forever rather than being a one-shot progression like
 * SkinTier's. Enum + static resolver mirrors SkinTier's shape deliberately:
 * a Biome is fully immutable/stateless (no per-instance runtime state the
 * way a PowerUp has), so it doesn't need PowerUpManager's registry pattern.
 *
 * BAND_SIZE_METERS and every constant's tuning values are placeholders --
 * expected to be re-tuned by feel, same as WEAK_PLATFORM_CHANCE and
 * POWERUP_SPAWN_CHANCE in GameplayScreen (see design doc Open Questions).
 * linearDamping only ever increases across constants relative to the first
 * (baseline) entry -- required so GapReachabilityValidator's existing
 * MAX_HORIZONTAL_SPEED/timeToFall inputs stay conservative without any
 * per-biome physics recomputation (see design doc Constraints). */
public enum Biome {
    // Baseline -- matches the pre-biome look/feel exactly (today's
    // GameplayRenderer.BG_TOP_COLOR/BG_BOTTOM_COLOR, GameplayScreen's
    // WEAK_PLATFORM_CHANCE and BALL_LINEAR_DAMPING) so a player who never
    // leaves the first band sees no change at all.
    NEON_DEPTHS(
            new Color(0.1020f, 0.0431f, 0.1804f, 1f), // #1a0b2e dark purple
            new Color(0.4157f, 0.1725f, 0.5686f, 1f), // #6a2c91 mid purple-magenta
            0.35f,
            0.5f),
    // Floatier -- higher damping (still Box2D-legible, not slow-motion) and
    // a cooler electric-blue palette so it reads as a distinct "biome", not
    // just a recolor. 3.0f, not a smaller bump: with gravity constant,
    // terminal fall speed roughly scales as gravity/damping (GRAVITY_Y=-25),
    // so damping needs to bring that terminal speed down near what a single
    // short fall between platforms actually reaches (~11 m/s worst case) or
    // the extra drag never has time to matter before the ball lands --
    // confirmed by playtest at 1.0f (terminal ~25 m/s, unreachable) feeling
    // identical to baseline.
    FLOATING_VOID(
            new Color(0.0392f, 0.0549f, 0.1804f, 1f), // #0a0e2e deep space navy
            new Color(0.1020f, 0.2902f, 0.4784f, 1f), // #1a4a7a electric blue
            0.5f,
            3.0f);

    // World units of depth per band; the roster cycles every
    // BAND_SIZE_METERS * values().length. Placeholder -- no starting number
    // is locked in (design doc Open Questions). Package-private (not
    // private) so BiomeTest can reference it directly instead of
    // duplicating the value as a magic number.
    static final float BAND_SIZE_METERS = 100f;

    private final Color topColor;
    private final Color bottomColor;
    private final float weakPlatformChance;
    private final float linearDamping;

    Biome(Color topColor, Color bottomColor, float weakPlatformChance, float linearDamping) {
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        this.weakPlatformChance = weakPlatformChance;
        this.linearDamping = linearDamping;
    }

    public Color getTopColor() {
        return topColor;
    }

    public Color getBottomColor() {
        return bottomColor;
    }

    public float getWeakPlatformChance() {
        return weakPlatformChance;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    /** The active biome for a given depth -- depthScore only, never a
     * lifetime-best depth like SkinTier.forDepth() uses. Biomes are a
     * cyclic per-run experience, not a one-way reward ratchet; using
     * lifetime-best here would let a high-best-depth player permanently
     * skip the early roster on every future run (see design doc
     * Constraints, plan-eng-review outside voice finding). */
    public static Biome biomeFor(float depth) {
        return biomeFor(depth, BAND_SIZE_METERS);
    }

    /** Package-private overload taking bandSize explicitly -- same reason
     * GapReachabilityValidator.worstCaseDistanceToGap() is a separate
     * package-private static method: makes the resolver itself directly,
     * deterministically testable (BiomeTest) without depending on whatever
     * BAND_SIZE_METERS happens to be tuned to, and without needing
     * reflection to exercise the bandSize <= 0 guard. Math.floorMod, not
     * %, so negative depth wraps correctly instead of returning a negative
     * index. */
    static Biome biomeFor(float depth, float bandSize) {
        if (bandSize <= 0f) {
            throw new IllegalArgumentException("bandSize must be positive, was " + bandSize);
        }
        Biome[] roster = values();
        int rawIndex = (int) Math.floor(depth / bandSize);
        int index = Math.floorMod(rawIndex, roster.length);
        return roster[index];
    }
}
