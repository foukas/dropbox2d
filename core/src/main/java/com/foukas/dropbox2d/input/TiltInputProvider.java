package com.foukas.dropbox2d.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;

/** Accelerometer-based tilt steering -- analog, unlike tap's binary
 * left/right. A dead zone ignores small unintentional tilts from just
 * holding the phone normally; beyond MAX_TILT the steer value saturates
 * at +-1 so full steering doesn't require an uncomfortable tilt angle.
 *
 * Sign convention: confirmed on-device that raw Gdx.input.getAccelerometerX()
 * reads backwards from what the naive Android-axis assumption predicted --
 * tilting the device's right edge down produced a negative reading. Negated
 * in normalize() below (the one place this assumption lives) so tilting
 * right steers right.
 *
 * Desktop (LWJGL3) has no accelerometer -- Gdx.input.getAccelerometerX()
 * returns 0 there, so this degrades to "no steering" rather than
 * crashing. Desktop is dev tooling only; the real validation target for
 * tilt is on-device (per the design doc's Success Criteria). */
public class TiltInputProvider implements InputProvider {
    private static final float DEAD_ZONE = 1.0f; // m/s^2
    private static final float MAX_TILT = 6.0f;  // m/s^2

    @Override
    public float getSteerValue() {
        return normalize(-Gdx.input.getAccelerometerX());
    }

    /** Pure so it's testable without a real Gdx.input context. Takes the
     * already-sign-corrected value -- see getSteerValue(). */
    static float normalize(float rawAccelX) {
        if (Math.abs(rawAccelX) < DEAD_ZONE) {
            return 0f;
        }
        float sign = Math.signum(rawAccelX);
        float magnitude = (Math.abs(rawAccelX) - DEAD_ZONE) / (MAX_TILT - DEAD_ZONE);
        return sign * MathUtils.clamp(magnitude, 0f, 1f);
    }
}
