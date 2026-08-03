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
import com.foukas.dropbox2d.monetization.AdProvider;
import com.foukas.dropbox2d.progression.PlayerProgress;

/**
 * Replaces the pre-Screen frozen-overlay game-over hack (plan-eng-review
 * Next Step 5). Constructed once in DropGame.create() and reused, same
 * pattern as MainMenuScreen and GameplayScreen.
 *
 * Preserves the two existing tap zones from the pre-Screen implementation
 * at identical screen-pixel regions (Open Questions acceptance check):
 * control-toggle (top strip) and rewarded-ad offer (next strip, inert via
 * NoOpAdProvider). Adds a new bottom strip for "back to menu" -- the design
 * doc's Next Step 5 calls for a back-to-menu button that didn't exist
 * before, so it gets its own non-overlapping zone rather than competing
 * with the existing ones.
 */
public class GameOverScreen implements Screen {

    // Mirrors GameplayScreen.CONTROL_TOGGLE_ZONE_HEIGHT's sizing rationale --
    // a bottom strip, symmetric with the top one, kept off the main "tap to
    // retry" area for the same reason (avoid accidental hits).
    private static final float BACK_TO_MENU_ZONE_HEIGHT = 100f;

    // Press Start 2P (Next Step 11) is a lot wider/taller per line than the
    // default BitmapFont this screen's fixed height-fraction/pixel-offset
    // layout was tuned for -- at full HudFontScale, several lines here
    // overflow the window horizontally or collide vertically. Shrinking
    // just for this screen keeps the existing layout math valid without
    // reworking it; this screen is flagged for a real redesign later
    // anyway, so a rescale (not a layout change) is the appropriate fix now.
    private static final float AUX_TEXT_SCALE = 0.6f;

    private final Game game;
    private final GameplayScreen gameplayScreen;
    private final MainMenuScreen mainMenuScreen;
    private final PlayerProgress playerProgress;
    private final AdProvider adProvider;

    private final OrthographicCamera hudCamera;
    private final SpriteBatch batch;
    private final BitmapFont font;

    GameOverScreen(Game game, GameplayScreen gameplayScreen, MainMenuScreen mainMenuScreen,
                   PlayerProgress playerProgress, AdProvider adProvider) {
        this.game = game;
        this.gameplayScreen = gameplayScreen;
        this.mainMenuScreen = mainMenuScreen;
        this.playerProgress = playerProgress;
        this.adProvider = adProvider;

        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch = new SpriteBatch();
        font = HudFont.generate();
        font.setColor(Color.WHITE);
        updateFontScale();
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            game.setScreen(mainMenuScreen);
            return;
        }

        // #1a0b2e -- matches GameplayRenderer's BG_TOP_COLOR (Next Step 7's
        // synthwave palette), flat rather than gradient for the same reason
        // as MainMenuScreen.
        Gdx.gl.glClearColor(0.1020f, 0.0431f, 0.1804f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        font.draw(batch, "GAME OVER", 0f, height * 0.65f, width, Align.center, false);
        font.draw(batch, "Depth: " + (int) gameplayScreen.getDepthScore() + "m   Best: " + (int) playerProgress.getBestDepth() + "m",
                0f, height * 0.5f, width, Align.center, false);
        font.draw(batch, "Tap to retry", 0f, height * 0.4f, width, Align.center, false);

        // Preserved from the pre-Screen implementation, same pixel regions.
        String controlHint = "Tap here to switch to " + (gameplayScreen.isUseTilt() ? "TAP" : "TILT");
        font.draw(batch, controlHint, 0f, height - 10f, width, Align.center, false);
        if (adProvider.isRewardedAdReady()) {
            String adHint = "Tap here for a free Wrecking Ball (watch ad)";
            float y = height - (GameplayScreen.CONTROL_TOGGLE_ZONE_HEIGHT + GameplayScreen.AD_OFFER_ZONE_HEIGHT) / 2f;
            font.draw(batch, adHint, 0f, y, width, Align.center, false);
        }

        // font.draw()'s y is the text's TOP edge (text extends downward from
        // it) -- unlike the top-anchored hints above, a fixed small offset
        // here would let descenders (e.g. the 'p' in "Tap") clip below the
        // screen bottom. Use the actual line height so this stays correct
        // at any HudFontScale.
        font.draw(batch, "Tap here to return to menu", 0f, font.getLineHeight() + 10f, width, Align.center, false);
        batch.end();

        if (Gdx.input.justTouched()) {
            // Gdx.input.getY() is measured from the TOP of the screen
            // (touch-coordinate convention), unlike the world's Y-up
            // OpenGL convention used elsewhere in this codebase.
            float touchY = Gdx.input.getY();
            if (touchY < GameplayScreen.CONTROL_TOGGLE_ZONE_HEIGHT) {
                gameplayScreen.toggleControlScheme();
            } else if (touchY < GameplayScreen.AD_OFFER_ZONE_HEIGHT && adProvider.isRewardedAdReady()) {
                gameplayScreen.offerRewardedAd();
            } else if (touchY > height - BACK_TO_MENU_ZONE_HEIGHT) {
                game.setScreen(mainMenuScreen);
            } else {
                gameplayScreen.resetForNewRun();
                game.setScreen(gameplayScreen);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        hudCamera.setToOrtho(false, width, height);
        updateFontScale();
    }

    private void updateFontScale() {
        font.getData().setScale(HudFontScale.forScreenHeight(Gdx.graphics.getHeight()) * AUX_TEXT_SCALE);
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
