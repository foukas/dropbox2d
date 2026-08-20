package com.foukas.dropbox2d.powerups;

import com.badlogic.gdx.physics.box2d.Body;

/**
 * The first power-up Approach C ships. Deliberately NOT a scripted
 * "always break platforms" override: the only thing this does is
 * temporarily multiply the ball's own Box2D density -- see
 * AbstractDensityPowerUp's class doc for the shared lifecycle this and
 * RampagePowerUp (rampage design doc) both extend.
 */
public class WreckingBallPowerUp extends AbstractDensityPowerUp {
    private static final float NORMAL_DENSITY = 1f;
    private static final float WRECKING_DENSITY = 6f;
    private static final float DURATION_SECONDS = 5f;

    public WreckingBallPowerUp(Body ballBody) {
        super(ballBody, NORMAL_DENSITY, WRECKING_DENSITY, DURATION_SECONDS);
    }
}
