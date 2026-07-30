package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.foukas.dropbox2d.progression.PlayerProgress;

/**
 * First screen on launch (plan-eng-review Next Step 4). Constructed once in
 * DropGame.create() and reused for the life of the app -- same
 * construct-once-reuse pattern as GameplayScreen (see DropGame's class
 * doc), though here it matters less since MainMenuScreen holds no per-run
 * state to leak, only rendering resources that would otherwise leak GL
 * handles if reconstructed every time the player returns to the menu.
 *
 * Control-scheme indicator is read-only (resolved in the design doc's
 * Reviewer Concerns) -- the actual tilt/tap toggle stays in-gameplay only,
 * via the existing control-toggle tap zone on GameOverScreen.
 */
public class MainMenuScreen implements Screen {

    private final Game game;
    private final GameplayScreen gameplayScreen;
    private final PlayerProgress playerProgress;

    private final OrthographicCamera hudCamera;
    private final SpriteBatch batch;
    private final BitmapFont font;

    MainMenuScreen(Game game, GameplayScreen gameplayScreen, PlayerProgress playerProgress) {
        this.game = game;
        this.gameplayScreen = gameplayScreen;
        this.playerProgress = playerProgress;

        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        updateFontScale();
    }

    @Override
    public void render(float delta) {
        // Back-button behavior per the design doc's resolved table: Main
        // Menu -> back exits the app. Gdx.input.setCatchKey(BACK, true) is
        // set once in DropGame.create() so Android doesn't intercept this
        // before render() sees it.
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            Gdx.app.exit();
            return;
        }

        Gdx.gl.glClearColor(0.04f, 0.04f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        font.draw(batch, "DROPBOX2D", 0f, Gdx.graphics.getHeight() * 0.65f, Gdx.graphics.getWidth(), Align.center, false);
        font.draw(batch, "Tap to start", 0f, Gdx.graphics.getHeight() * 0.5f, Gdx.graphics.getWidth(), Align.center, false);
        String controls = playerProgress.getPreferTilt() ? "TILT" : "TAP";
        font.draw(batch, "Best: " + (int) playerProgress.getBestDepth() + "m   Controls: " + controls,
                0f, Gdx.graphics.getHeight() * 0.15f, Gdx.graphics.getWidth(), Align.center, false);
        batch.end();

        if (Gdx.input.justTouched()) {
            game.setScreen(gameplayScreen);
        }
    }

    @Override
    public void resize(int width, int height) {
        hudCamera.setToOrtho(false, width, height);
        updateFontScale();
    }

    private void updateFontScale() {
        font.getData().setScale(HudFontScale.forScreenHeight(Gdx.graphics.getHeight()));
    }

    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
