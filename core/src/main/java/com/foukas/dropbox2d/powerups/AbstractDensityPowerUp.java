package com.foukas.dropbox2d.powerups;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;

/**
 * Shared lifecycle for a power-up that works by temporarily multiplying
 * the ball's Box2D fixture density (rampage design doc, plan-eng-review
 * 2026-08-06 -- extracted from WreckingBallPowerUp once RampagePowerUp
 * needed the identical activate/update/isActive/getRemaining/reset/
 * setDensity lifecycle, just with different density/duration constants).
 * A heavier ball naturally generates more momentum at the same fall speed
 * (mass*velocity -- velocity itself is roughly unaffected by mass under
 * gravity), which is what crosses DestructiblePlatform's break threshold.
 * Real physics differentiator, not a scripted "always break" override.
 */
public abstract class AbstractDensityPowerUp implements PowerUp {
    private final Body ballBody;
    private final float normalDensity;
    private final float targetDensity;
    private final float durationSeconds;
    private float remaining;

    protected AbstractDensityPowerUp(Body ballBody, float normalDensity, float targetDensity, float durationSeconds) {
        this.ballBody = ballBody;
        this.normalDensity = normalDensity;
        this.targetDensity = targetDensity;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public void activate() {
        remaining = durationSeconds;
        setDensity(targetDensity);
    }

    @Override
    public void update(float delta) {
        if (remaining <= 0f) {
            return;
        }
        remaining -= delta;
        if (remaining <= 0f) {
            remaining = 0f;
            setDensity(normalDensity);
            onExpire();
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
        setDensity(normalDensity);
        onExpire();
    }

    /** Hook for subclass-specific behavior when this power-up's window
     * ends, whether by natural expiry (update()) or forced reset() (e.g.
     * PowerUpManager.activate() resetting the previously-active power-up
     * when a different one is picked up). No-op by default. */
    protected void onExpire() {
    }

    /** Adds extra time to the current remaining duration without resetting
     * density or calling onExpire() -- for a power-up that wants "picking
     * up a related item while this is already active extends the window"
     * instead of the usual activate()/reset() exclusivity dance (rampage
     * follow-up, 2026-08-06: RampagePowerUp exposes this so a wrecking-ball
     * pickup mid-rampage becomes a timer bonus, not a strict downgrade).
     * Only meaningful while isActive(); a subclass exposing this publicly
     * should guard the call site accordingly. */
    protected void extend(float extraSeconds) {
        remaining += extraSeconds;
    }

    private void setDensity(float density) {
        for (Fixture fixture : ballBody.getFixtureList()) {
            fixture.setDensity(density);
        }
        ballBody.resetMassData();
    }
}
