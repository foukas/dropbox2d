package com.foukas.dropbox2d.powerups;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;

/**
 * The first power-up Approach C ships. Deliberately NOT a scripted
 * "always break platforms" override: the only thing this does is
 * temporarily multiply the ball's own Box2D density. A heavier ball
 * naturally generates more momentum at the same fall speed (mass*velocity
 * -- velocity itself is roughly unaffected by mass under gravity), which
 * is what crosses DestructiblePlatform's break threshold. Real physics
 * differentiator, not two separate code paths for "normal" and "empowered."
 */
public class WreckingBallPowerUp implements PowerUp {
    private static final float NORMAL_DENSITY = 1f;
    private static final float WRECKING_DENSITY = 6f;
    private static final float DURATION_SECONDS = 5f;

    private final Body ballBody;
    private float remaining;

    public WreckingBallPowerUp(Body ballBody) {
        this.ballBody = ballBody;
    }

    @Override
    public void activate() {
        remaining = DURATION_SECONDS;
        setDensity(WRECKING_DENSITY);
    }

    @Override
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

    @Override
    public boolean isActive() {
        return remaining > 0f;
    }

    @Override
    public float getRemaining() {
        return remaining;
    }

    @Override
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
