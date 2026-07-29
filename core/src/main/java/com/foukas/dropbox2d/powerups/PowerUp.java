package com.foukas.dropbox2d.powerups;

/** Shared lifecycle for a power-up effect. Extracted from WreckingBallPowerUp
 * once it was clear more power-up types are coming -- these four methods
 * plus reset() are exactly what WreckingBallPowerUp already needed, so
 * this isn't a guess at future shape, it's what one real implementation
 * already proved out. */
public interface PowerUp {
    void activate();

    void update(float delta);

    boolean isActive();

    float getRemaining();

    void reset();
}
