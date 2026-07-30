package com.foukas.dropbox2d.fx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** Decaying camera jitter. A stronger trigger while already shaking
 * replaces the current shake; a weaker one is ignored rather than
 * downgrading an in-progress shake. */
public class ScreenShake {
    private float timeRemaining;
    private float duration;
    private float magnitude;
    private final Vector2 offset = new Vector2();

    public void trigger(float duration, float magnitude) {
        if (magnitude >= this.magnitude || timeRemaining <= 0f) {
            this.duration = duration;
            this.magnitude = magnitude;
            this.timeRemaining = duration;
        }
    }

    public void update(float delta) {
        if (timeRemaining <= 0f) {
            offset.setZero();
            return;
        }
        timeRemaining -= delta;
        if (timeRemaining <= 0f) {
            timeRemaining = 0f;
            offset.setZero();
            return;
        }
        float envelope = timeRemaining / duration; // 1 -> 0 over the shake's life
        float currentMagnitude = magnitude * envelope;
        offset.set(MathUtils.random(-currentMagnitude, currentMagnitude), MathUtils.random(-currentMagnitude, currentMagnitude));
    }

    public Vector2 getOffset() {
        return offset;
    }

    public boolean isShaking() {
        return timeRemaining > 0f;
    }

    /** Clears any active shake immediately, without waiting for it to decay
     * via update() -- for resetting per-run state (e.g. a death-moment
     * shake shouldn't carry into the next run). */
    public void stop() {
        timeRemaining = 0f;
        magnitude = 0f;
        offset.setZero();
    }
}
