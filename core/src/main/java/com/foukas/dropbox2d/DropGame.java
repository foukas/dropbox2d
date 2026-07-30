package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Input;
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
 * Screen/State Flow section). GameplayScreen and MainMenuScreen are each
 * constructed once and reused for the life of the app, not reconstructed
 * per transition -- Game.setScreen() does not auto-dispose the outgoing
 * screen, and a future gdx-vfx FBO pipeline attached to GameplayScreen
 * would otherwise leak GL resources across repeated retries.
 */
public class DropGame extends Game {

    private PlayerProgress playerProgress;
    private ScreenShake screenShake;
    private ParticleSystem particleSystem;
    private AdProvider adProvider;
    private GameplayScreen gameplayScreen;
    private MainMenuScreen mainMenuScreen;

    @Override
    public void create() {
        Box2D.init();
        // Must be set before any Screen's render() runs, so Android doesn't
        // intercept the back button (finishing the activity) before our
        // per-screen back-button handling sees it.
        Gdx.input.setCatchKey(Input.Keys.BACK, true);

        playerProgress = new PlayerProgress();
        playerProgress.recordSessionStart(System.currentTimeMillis());
        screenShake = new ScreenShake();
        particleSystem = new ParticleSystem();
        adProvider = new NoOpAdProvider();

        gameplayScreen = new GameplayScreen(playerProgress, screenShake, particleSystem, adProvider);
        mainMenuScreen = new MainMenuScreen(this, gameplayScreen, playerProgress);
        setScreen(mainMenuScreen);
    }

    @Override
    public void dispose() {
        gameplayScreen.dispose();
        mainMenuScreen.dispose();
    }
}
