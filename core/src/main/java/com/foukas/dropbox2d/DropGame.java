package com.foukas.dropbox2d;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.foukas.dropbox2d.fx.ParticleSystem;
import com.foukas.dropbox2d.fx.ScreenShake;
import com.foukas.dropbox2d.monetization.AdProvider;
import com.foukas.dropbox2d.monetization.NoOpAdProvider;
import com.foukas.dropbox2d.progression.PlayerProgress;

/**
 * Thin Game subclass (plan-eng-review Next Step 1, 2026-07-29). Owns the
 * app-lifetime objects -- PlayerProgress, ScreenShake, ParticleSystem,
 * AdProvider -- created once here and constructor-injected into each
 * Screen, rather than static/singleton access (see the design doc's
 * Screen/State Flow section). GameplayScreen is constructed once and
 * reused across retries, not reconstructed per run -- Game.setScreen()
 * does not auto-dispose the outgoing screen, and a future gdx-vfx FBO
 * pipeline attached to GameplayScreen would otherwise leak GL resources
 * across repeated retries.
 */
public class DropGame extends Game {

    private PlayerProgress playerProgress;
    private ScreenShake screenShake;
    private ParticleSystem particleSystem;
    private AdProvider adProvider;
    private GameplayScreen gameplayScreen;

    @Override
    public void create() {
        Box2D.init();

        playerProgress = new PlayerProgress();
        playerProgress.recordSessionStart(System.currentTimeMillis());
        screenShake = new ScreenShake();
        particleSystem = new ParticleSystem();
        adProvider = new NoOpAdProvider();

        gameplayScreen = new GameplayScreen(playerProgress, screenShake, particleSystem, adProvider);
        setScreen(gameplayScreen);
    }

    @Override
    public void dispose() {
        gameplayScreen.dispose();
    }
}
