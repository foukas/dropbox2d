package com.foukas.dropbox2d.powerups;

import com.badlogic.gdx.physics.box2d.Body;

/**
 * For its duration, every platform the ball touches breaks, not just weak
 * ones -- see the rampage design doc (plan-eng-review, 2026-08-06) for the
 * full derivation. Reuses AbstractDensityPowerUp's density-multiplier
 * lifecycle directly (same mechanism as WreckingBallPowerUp): the
 * ContactDispatcher/GameplayScreen wiring that actually extends break-
 * eligibility to NORMAL/MOVING platforms while this is active lives
 * elsewhere (this class owns only the density timer and its own local
 * smash-count/bonus bookkeeping, not the break-eligibility check itself).
 *
 * "Rampage-active" for the BooleanSupplier ContactDispatcher queries is
 * NOT a method on this class -- GameplayScreen wires it as
 * {@code () -> "rampage".equals(powerUpManager.getActiveType())},
 * reusing PowerUpManager's existing exclusivity guarantee (Next Step 3)
 * rather than adding a second query surface here.
 */
public class RampagePowerUp extends AbstractDensityPowerUp {
    private static final float NORMAL_DENSITY = 1f;
    // Placeholder, same value as WreckingBallPowerUp's -- the momentum
    // math doesn't change per platform tag (design doc Constraints: "no
    // new physics re-derivation needed"), so there's no reason to start
    // from a different density. Free to diverge later by feel.
    private static final float RAMPAGE_DENSITY = 6f;
    private static final float DURATION_SECONDS = 5f;

    // Live, per-activation count -- decays to 0 on expiry (via onExpire()),
    // not on touch, so it drives its own HUD readout distinct from the
    // gap-combo (design doc Constraints, combo/spectacle conflict fix).
    private int smashCount;

    // Run-scoped total -- unlike smashCount, this does NOT reset on
    // expiry, only via a fresh RampagePowerUp instance at the start of a
    // new run (GameplayScreen.resetForNewRun() constructs a new one each
    // time, same pattern as WreckingBallPowerUp). Feeds the end-of-run
    // score bonus (GameOverScreen), which is deliberately never folded
    // into playerProgress.reportDepth() or persisted (design doc Approach
    // C: avoids contaminating bestDepth-driven systems like SkinTier).
    private int runBonus;

    public RampagePowerUp(Body ballBody) {
        super(ballBody, NORMAL_DENSITY, RAMPAGE_DENSITY, DURATION_SECONDS);
    }

    /** Called by GameplayScreen when a rampage-triggered break happens
     * (including both halves of a MOVING split-body pair). */
    public void recordSmash() {
        smashCount++;
        runBonus++;
    }

    public int getSmashCount() {
        return smashCount;
    }

    public int getRunBonus() {
        return runBonus;
    }

    @Override
    protected void onExpire() {
        smashCount = 0;
    }
}
