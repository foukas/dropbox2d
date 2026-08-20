package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Post-collision safety net against a single bounce imparting far more
 * velocity than any normal interaction should ever produce (rampage
 * design doc, plan-eng-review 2026-08-06). Extending platform-breaking to
 * NORMAL/MOVING platforms lets the ball free-fall unimpeded through a long
 * stack -- if the power-up granting that expires mid-fall, the next solid
 * contact absorbs the ball's full accumulated velocity in one bounce,
 * risking an off-screen launch. Mirrors PhysicsNaNGuard's shape (a small,
 * single-purpose guard checked every Box2D substep) but is a DIFFERENT
 * failure mode and response: PhysicsNaNGuard detects NaN/Infinite/absurd
 * values and hard-resets to zero; this detects a merely-too-fast (but
 * otherwise valid) velocity and scales it down, preserving direction, so
 * the bounce is capped rather than stopped dead.
 *
 * Deliberately NOT a per-axis clamp (like GameplayScreen.handleInput()'s
 * MAX_HORIZONTAL_SPEED clamp on vel.x alone) -- a component-wise clamp
 * would leave vel.y, the exact component driving the off-screen-launch
 * risk, completely unclamped.
 */
public final class VelocityClamp {

    private VelocityClamp() {
    }

    // Placeholder, expected to be re-tuned against real fall-speed data
    // (design doc Open Questions) -- comfortably above the ~25.5 m/s
    // normal-play worst case DestructiblePlatform's own doc cites (a big
    // multi-row combo fall under normal density), well below
    // PhysicsNaNGuard's MAX_REASONABLE_SPEED=100 ("this is definitely
    // broken, not just fast") threshold.
    private static final float MAX_SAFE_SPEED = 40f;

    /** Scales the body's velocity vector down to MAX_SAFE_SPEED if its
     * magnitude exceeds that, preserving direction. Returns true if a
     * correction was applied. */
    public static boolean checkAndClamp(Body body) {
        Vector2 vel = body.getLinearVelocity();
        if (vel.len() <= MAX_SAFE_SPEED) {
            return false;
        }
        Vector2 clamped = vel.cpy().setLength(MAX_SAFE_SPEED);
        body.setLinearVelocity(clamped);
        return true;
    }
}
