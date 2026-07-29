package com.foukas.dropbox2d.physics;

/**
 * Pure break-threshold logic for weak platforms. The Box2D wiring lives in
 * ContactDispatcher, which computes the ball's momentum (mass * speed) in
 * preSolve -- BEFORE the collision solver runs -- and calls shouldBreak
 * here. If it should break, ContactDispatcher calls contact.setEnabled
 * (false), which skips collision response for that contact entirely: the
 * ball passes through with no bounce, "as if it were nothing," instead of
 * bouncing off it like a normal platform and only breaking a step late.
 *
 * This was originally impulse-based, checked in postSolve using the
 * resolved contact impulse -- but by the time postSolve fires, Box2D has
 * already applied the bounce response for that contact. Deciding to break
 * one step after the bounce already happened is exactly why a wrecking
 * ball still visibly bounced off platforms it was about to destroy
 * (observed live on desktop and device). Momentum is also more physically
 * apt than resolved impulse here anyway: under gravity, fall velocity is
 * roughly mass-independent for a given drop, so momentum scales almost
 * exactly with the wrecking-ball density multiplier (6x mass -> ~6x
 * momentum at the same fall speed) -- a cleaner signal than impulse, which
 * is muddied by restitution mixing between the two fixtures.
 *
 * Implementation choice (fixture removal, not pre-fractured composite
 * bodies): fixture removal is documented to have a Box2D pitfall where a
 * sensor touching the destroyed body never receives OnSeparation. This
 * codebase never relies on persistent "is currently touching" sensor
 * state anywhere -- every contact-driven event (BallTouchedPlatform,
 * PlatformDestroyed, PowerUpCollected) is a one-shot trigger, dispatched
 * once and forgotten. There is no ongoing touching-state to desync, so
 * the "explicit sensor re-sync" the design doc called for isn't a
 * separate mechanism to build -- it falls out of the fire-once event
 * architecture already in place from the hardening pass.
 */
public final class DestructiblePlatform {

    private DestructiblePlatform() {
    }

    /** Tuned against DropGame's actual constants (ball mass ~0.5 at
     * density 1, ~3.0 at wrecking-ball density 6, GRAVITY_Y=-25,
     * ROW_SPACING=2.6). Since fall velocity is ~mass-independent, a normal
     * ball's momentum after even a big 5-row combo fall tops out around
     * ~13 (0.5 * ~25.5 m/s); a wrecking-ball-mass ball crosses this
     * threshold after falling just one row (~34). 20 sits comfortably
     * between those two ranges. Still expected to be re-tuned further by
     * feel -- see the design doc's power-up/destructible-platform balance
     * open question. */
    public static final float DEFAULT_BREAK_THRESHOLD = 20f;

    public static boolean shouldBreak(float momentumMagnitude) {
        return shouldBreak(momentumMagnitude, DEFAULT_BREAK_THRESHOLD);
    }

    public static boolean shouldBreak(float momentumMagnitude, float breakThreshold) {
        return momentumMagnitude >= breakThreshold;
    }
}
