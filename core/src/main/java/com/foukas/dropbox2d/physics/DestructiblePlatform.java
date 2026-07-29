package com.foukas.dropbox2d.physics;

/**
 * Pure break-threshold logic for weak platforms. The Box2D wiring lives in
 * ContactDispatcher (reads the contact's resolved impulse in postSolve and
 * calls shouldBreak here); this class only decides yes/no from a number.
 *
 * Implementation choice (fixture removal, not pre-fractured composite
 * bodies): fixture removal is documented to have a Box2D pitfall where a
 * sensor touching the destroyed body never receives OnSeparation. This
 * codebase never relies on persistent "is currently touching" sensor
 * state anywhere -- every contact-driven event (BallTouchedPlatform,
 * PlatformDestroyed, PowerUpCollected) is a one-shot beginContact/
 * postSolve trigger, dispatched once and forgotten. There is no ongoing
 * touching-state to desync, so the "explicit sensor re-sync" the design
 * doc called for isn't a separate mechanism to build -- it falls out of
 * the fire-once event architecture already in place from the hardening
 * pass.
 */
public final class DestructiblePlatform {

    private DestructiblePlatform() {
    }

    /** Tuned against DropGame's actual constants (ball mass ~0.5 at
     * density 1, ~3.0 at wrecking-ball density 6, GRAVITY_Y=-25,
     * ROW_SPACING=2.6). A normal ball's contact impulse after falling
     * even 2-3 rows lands in the ~9-15 range (m*v scaled up by the
     * ball's ~0.55 restitution) -- 8 was inside that range, so weak
     * platforms broke under ordinary play regardless of the power-up,
     * observed live on both desktop and device. 25 sits comfortably
     * above normal-ball impacts (rare/never triggers) and comfortably
     * below wrecking-ball impacts (~35-55 at the same fall distances,
     * reliably triggers). Still expected to be re-tuned further by feel. */
    public static final float DEFAULT_BREAK_THRESHOLD = 25f;

    public static boolean shouldBreak(float impulseMagnitude) {
        return shouldBreak(impulseMagnitude, DEFAULT_BREAK_THRESHOLD);
    }

    public static boolean shouldBreak(float impulseMagnitude, float breakThreshold) {
        return impulseMagnitude >= breakThreshold;
    }
}
