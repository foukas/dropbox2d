package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;

/**
 * Post-step safety net against Box2D physics blowups (NaN/Infinity
 * position or velocity, or an absurd magnitude that's for-practical-
 * purposes just as broken). Cheap insurance, especially once Approach C's
 * power-ups start applying large one-shot forces to the ball.
 */
public final class PhysicsNaNGuard {

    private PhysicsNaNGuard() {
    }

    private static final float MAX_REASONABLE_SPEED = 100f; // world units/sec -- generous upper bound
    private static final float MAX_REASONABLE_POSITION = 100000f; // world units

    /** Checks the body's position/velocity and clamps/resets it in place
     * (at safeX/safeY, zero velocity) if either is NaN, Infinite, or past
     * the sanity bounds above. Returns true if a correction was applied. */
    public static boolean checkAndFix(Body body, float safeX, float safeY) {
        Vector2 pos = body.getPosition();
        Vector2 vel = body.getLinearVelocity();

        boolean posBad = isBad(pos.x) || isBad(pos.y)
            || Math.abs(pos.x) > MAX_REASONABLE_POSITION || Math.abs(pos.y) > MAX_REASONABLE_POSITION;
        boolean velBad = isBad(vel.x) || isBad(vel.y)
            || Math.abs(vel.x) > MAX_REASONABLE_SPEED || Math.abs(vel.y) > MAX_REASONABLE_SPEED;

        if (posBad) {
            body.setTransform(safeX, safeY, 0f);
        }
        if (posBad || velBad) {
            body.setLinearVelocity(0f, 0f);
            body.setAngularVelocity(0f);
        }
        return posBad || velBad;
    }

    private static boolean isBad(float value) {
        return Float.isNaN(value) || Float.isInfinite(value);
    }
}
