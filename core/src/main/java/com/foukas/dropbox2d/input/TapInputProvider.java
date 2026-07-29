package com.foukas.dropbox2d.input;

import com.badlogic.gdx.Gdx;

/** Approach A's original control scheme, unchanged in feel: tap/hold the
 * left or right half of the screen for a binary (not analog) steer. */
public class TapInputProvider implements InputProvider {
    @Override
    public float getSteerValue() {
        if (!Gdx.input.isTouched()) {
            return 0f;
        }
        return sideOf(Gdx.input.getX(), Gdx.graphics.getWidth());
    }

    /** Pure so it's testable without a real Gdx.input/Gdx.graphics context. */
    static float sideOf(float touchX, float screenWidth) {
        return touchX < screenWidth / 2f ? -1f : 1f;
    }
}
