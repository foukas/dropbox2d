package com.foukas.dropbox2d;

/**
 * Shared font-scale math (plan-eng-review Next Step 4) -- the default
 * BitmapFont is sized in raw pixels (~15px cap height), which reads fine on
 * the ~960px-tall desktop dev window but is tiny on a much higher-resolution
 * phone screen. Extracted once GameplayRenderer and MainMenuScreen both
 * needed the identical calculation, rather than letting it triplicate when
 * GameOverScreen needs it too (Next Step 5).
 */
final class HudFontScale {
    private static final float REFERENCE_HEIGHT = 960f;

    static float forScreenHeight(float screenHeight) {
        return Math.max(1f, screenHeight / REFERENCE_HEIGHT);
    }

    private HudFontScale() {
    }
}
