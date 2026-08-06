package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.physics.box2d.Body;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks MOVING platforms' kinematic bodies and reverses their patrol
 * direction when they reach a patrol bound -- Box2D kinematic bodies are
 * velocity-driven and don't bounce automatically (moving-platforms design
 * doc, plan-eng-review 2026-08-06). Mirrors DebrisManager's shape (a small
 * class owning a collection of bodies with per-frame-adjacent logic
 * outside the row/generation bookkeeping), chosen over inlining this into
 * GameplayScreen or PlatformRow (plan-eng-review Architecture finding).
 *
 * checkBounds() MUST be called once per Box2D substep, inside
 * GameplayScreen.stepPhysics()'s fixed-timestep loop -- NOT once per
 * render frame the way DebrisManager.update() is called. render() can
 * wrap 0-15 Box2D substeps depending on frame timing; a once-per-frame
 * bound check would let a kinematic body overshoot its patrol amplitude
 * on any stutter/GC pause before the next check runs, undermining the
 * amplitude-shrink reachability transform's safety premise that the
 * amplitude is a hard cap by construction (plan-eng-review outside-voice
 * finding -- the same reasoning PhysicsNaNGuard.checkAndFix() is already
 * called from inside that same loop for).
 */
public class MovingPlatformManager {
    private static final float PATROL_SPEED = 0.8f;

    private final List<TrackedPlatform> platforms = new ArrayList<>();

    /** Starts the body patrolling toward maxX. minX/maxX are world-space
     * x-coordinates for the body's center, not its edges -- see the
     * design doc's seam-invariant note for how a caller derives them from
     * a flanking span's geometry. */
    public void track(Body body, float minX, float maxX) {
        body.setLinearVelocity(PATROL_SPEED, 0f);
        platforms.add(new TrackedPlatform(body, minX, maxX));
    }

    /** No-op if body isn't tracked -- callers don't need to know whether a
     * given body was ever a MOVING platform before untracking it. */
    public void untrack(Body body) {
        platforms.removeIf(platform -> platform.body == body);
    }

    /** Reverses a tracked body's horizontal velocity when it reaches
     * either patrol bound. Checking position against velocity direction
     * (not just position against bound) avoids double-reversing a body
     * that's still sitting exactly at a bound from the previous check. */
    public void checkBounds() {
        for (TrackedPlatform platform : platforms) {
            float x = platform.body.getPosition().x;
            float vx = platform.body.getLinearVelocity().x;
            if (x <= platform.minX && vx < 0f) {
                platform.body.setLinearVelocity(PATROL_SPEED, 0f);
            } else if (x >= platform.maxX && vx > 0f) {
                platform.body.setLinearVelocity(-PATROL_SPEED, 0f);
            }
        }
    }

    public int count() {
        return platforms.size();
    }

    private static class TrackedPlatform {
        final Body body;
        final float minX;
        final float maxX;

        TrackedPlatform(Body body, float minX, float maxX) {
            this.body = body;
            this.minX = minX;
            this.maxX = maxX;
        }
    }
}
