package com.foukas.dropbox2d.powerups;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;

/**
 * The one power-up Approach C ships (per the design doc: "start with just
 * the wrecking-ball power-up and expand only if it plays well" -- no
 * generic multi-power-up framework here, that would be speculative
 * generality for a system that doesn't exist yet).
 *
 * Deliberately NOT a scripted "always break platforms" override: the only
 * thing this does is temporarily multiply the ball's own Box2D density.
 * A heavier ball naturally generates a larger contact impulse at the same
 * fall speed, which is what crosses DestructiblePlatform's break
 * threshold -- the same physics-driven mechanism handles both the rare
 * "normal ball breaks a weak platform by falling really far" case and the
 * "wrecking-ball reliably smashes through" case. One mechanism, not two.
 */
public class WreckingBallManager {
    private static final float NORMAL_DENSITY = 1f;
    private static final float WRECKING_DENSITY = 6f;
    private static final float DURATION_SECONDS = 5f;

    private final Body ballBody;
    private float remaining;

    public WreckingBallManager(Body ballBody) {
        this.ballBody = ballBody;
    }

    public void activate() {
        remaining = DURATION_SECONDS;
        setDensity(WRECKING_DENSITY);
    }

    public void update(float delta) {
        if (remaining <= 0f) {
            return;
        }
        remaining -= delta;
        if (remaining <= 0f) {
            remaining = 0f;
            setDensity(NORMAL_DENSITY);
        }
    }

    public boolean isActive() {
        return remaining > 0f;
    }

    public float getRemaining() {
        return remaining;
    }

    public void reset() {
        remaining = 0f;
        setDensity(NORMAL_DENSITY);
    }

    private void setDensity(float density) {
        for (Fixture fixture : ballBody.getFixtureList()) {
            fixture.setDensity(density);
        }
        ballBody.resetMassData();
    }
}
