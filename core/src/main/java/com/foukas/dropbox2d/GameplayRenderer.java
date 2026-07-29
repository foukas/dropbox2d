package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.math.Vector2;
import com.foukas.dropbox2d.fx.ParticleSystem;
import com.foukas.dropbox2d.fx.ScreenShake;
import com.foukas.dropbox2d.monetization.AdProvider;
import com.foukas.dropbox2d.progression.PlayerProgress;
import com.foukas.dropbox2d.progression.SkinTier;

/**
 * Extracted from GameplayScreen (plan-eng-review Next Step 1a, 2026-07-29) --
 * the render()/draw*() cluster, split out from the physics-step/input-
 * handling cluster that stays in GameplayScreen. Pure move, zero behavior
 * change: owns its own rendering resources (hudCamera, shapeRenderer, batch,
 * font) and reads per-frame gameplay state from GameplayScreen via
 * package-private getters, rather than duplicating that state here.
 * ScreenShake/ParticleSystem/PlayerProgress/AdProvider are the same
 * app-lifetime objects GameplayScreen already holds -- passed straight
 * through at construction rather than re-fetched per frame.
 */
public class GameplayRenderer {

    private static final Color NORMAL_PLATFORM_COLOR = new Color(0.55f, 0.55f, 0.65f, 1f);
    private static final Color WEAK_PLATFORM_COLOR = new Color(0.65f, 0.5f, 0.3f, 1f);
    private static final Color POWERUP_COLOR = new Color(1f, 0.84f, 0f, 1f);
    private static final Color WRECKING_BALL_COLOR = new Color(0.55f, 0.1f, 0.1f, 1f);
    // Wide contrast on purpose -- the first attempt (0.14 vs 0.04) was
    // nearly indistinguishable from the old flat clear color at a glance.
    private static final Color BG_TOP_COLOR = new Color(0.32f, 0.36f, 0.52f, 1f);
    private static final Color BG_BOTTOM_COLOR = new Color(0.02f, 0.02f, 0.04f, 1f);
    private static final Color COMBO_TEXT_COLOR = new Color(1f, 0.85f, 0.2f, 1f);

    // The default BitmapFont is sized in raw pixels (~15px cap height),
    // which reads fine on the ~960px-tall desktop dev window but is tiny
    // on a much higher-resolution phone screen. Scale it relative to
    // screen height so it stays legible across both.
    private static final float FONT_REFERENCE_HEIGHT = 960f;

    private final ScreenShake screenShake;
    private final ParticleSystem particleSystem;
    private final PlayerProgress playerProgress;
    private final AdProvider adProvider;

    private final OrthographicCamera hudCamera;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch batch;
    private final BitmapFont font;

    GameplayRenderer(ScreenShake screenShake, ParticleSystem particleSystem,
                      PlayerProgress playerProgress, AdProvider adProvider) {
        this.screenShake = screenShake;
        this.particleSystem = particleSystem;
        this.playerProgress = playerProgress;
        this.adProvider = adProvider;

        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        updateFontScale();
    }

    void draw(GameplayScreen screen) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Needed for particle fade-out and the ball highlight's alpha --
        // solid shapes elsewhere use alpha=1 so this doesn't change how
        // they look.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        drawBackground();

        OrthographicCamera camera = screen.getCamera();
        // Screen shake is applied only to this projection matrix, never to
        // camera.position itself -- game logic (scroll, wall-tracking,
        // catch-up) reads camera.position and must never see the jitter.
        Matrix4 shakenProjection = camera.combined.cpy().translate(screenShake.getOffset().x, screenShake.getOffset().y, 0f);
        shapeRenderer.setProjectionMatrix(shakenProjection);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (GameplayScreen.PlatformRow row : screen.getRows()) {
            drawPlatform(row.left);
            drawPlatform(row.right);
            drawPowerUp(row.powerUp);
        }

        shapeRenderer.setColor(GameplayScreen.DEBRIS_COLOR);
        for (Body debris : screen.getDebrisManager().getBodies()) {
            drawDebris(debris);
        }

        drawParticles();

        Body ballBody = screen.getBallBody();
        boolean gameOver = screen.getGameOverController().isGameOver();
        Color ballColor = gameOver ? Color.RED : (screen.getPowerUpManager().isActive() ? WRECKING_BALL_COLOR : screen.currentSkinTier().getColor());
        shapeRenderer.setColor(ballColor);
        shapeRenderer.circle(ballBody.getPosition().x, ballBody.getPosition().y, GameplayScreen.BALL_RADIUS, 24);
        drawBallHighlight(ballBody, ballColor);

        shapeRenderer.end();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        StringBuilder label = new StringBuilder("Depth: ").append((int) screen.getDepthScore()).append("m");
        if (screen.getPowerUpManager().isActive()) {
            label.append(String.format("\n%s: %.1fs", displayName(screen.getPowerUpManager().getActiveType()), screen.getPowerUpManager().getRemaining()));
        }
        label.append(String.format("\nBest: %dm   Streak: %dd   Controls: %s", (int) playerProgress.getBestDepth(), playerProgress.getStreak(), screen.isUseTilt() ? "TILT" : "TAP"));
        if (gameOver) {
            label.append("\nGAME OVER -- tap to retry");
        }
        font.draw(batch, label.toString(), 20f, Gdx.graphics.getHeight() - 20f);

        drawComboMultiplier(screen);
        drawToast(screen);
        if (gameOver) {
            drawControlToggleHint(screen);
            if (adProvider.isRewardedAdReady()) {
                drawRewardedAdHint();
            }
        }

        batch.end();
    }

    private void drawControlToggleHint(GameplayScreen screen) {
        String hint = "Tap here to switch to " + (screen.isUseTilt() ? "TAP" : "TILT");
        font.draw(batch, hint, 0f, Gdx.graphics.getHeight() - 10f, Gdx.graphics.getWidth(), com.badlogic.gdx.utils.Align.center, false);
    }

    private void drawRewardedAdHint() {
        String hint = "Tap here for a free Wrecking Ball (watch ad)";
        float y = Gdx.graphics.getHeight() - (GameplayScreen.CONTROL_TOGGLE_ZONE_HEIGHT + GameplayScreen.AD_OFFER_ZONE_HEIGHT) / 2f;
        font.draw(batch, hint, 0f, y, Gdx.graphics.getWidth(), com.badlogic.gdx.utils.Align.center, false);
    }

    private void drawBackground() {
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        shapeRenderer.rect(0f, 0f, w, h, BG_BOTTOM_COLOR, BG_BOTTOM_COLOR, BG_TOP_COLOR, BG_TOP_COLOR);
        shapeRenderer.end();
    }

    /** A small catchlight fixed to the ball's own rotating frame, not the
     * world -- rotating it by the ball's actual Box2D angle (real physics
     * rotation from rolling friction, not a cosmetic override) is what
     * sells "this is a ball rolling" instead of "a circle sliding." */
    private void drawBallHighlight(Body ballBody, Color baseColor) {
        Color highlight = baseColor.cpy().lerp(Color.WHITE, 0.75f);
        highlight.a = 0.85f;
        shapeRenderer.setColor(highlight);

        float distance = GameplayScreen.BALL_RADIUS * 0.5f;
        float angle = ballBody.getAngle();
        // Rotate the local point (0, distance) -- the ball's "north pole"
        // in its own unrotated frame -- by its current angle, so the dot
        // sweeps around the ball's surface as it spins.
        float worldOffsetX = -distance * MathUtils.sin(angle);
        float worldOffsetY = distance * MathUtils.cos(angle);
        float hx = ballBody.getPosition().x + worldOffsetX;
        float hy = ballBody.getPosition().y + worldOffsetY;
        shapeRenderer.circle(hx, hy, GameplayScreen.BALL_RADIUS * 0.16f, 10);
    }

    private void drawParticles() {
        for (ParticleSystem.Particle particle : particleSystem.getParticles()) {
            Color c = particle.color;
            shapeRenderer.setColor(c.r, c.g, c.b, particle.lifeFraction());
            float size = 0.08f * (0.5f + particle.lifeFraction());
            shapeRenderer.circle(particle.x, particle.y, size, 8);
        }
    }

    /** Distinct in every way from the regular HUD text -- color, size, and
     * position -- so it reads as its own thing rather than "the same text
     * as everything else." Always noticeably larger than the HUD label
     * while a combo is active (restingBoost), with an extra pop layered on
     * top right after each increment (the pulse envelope). */
    private void drawComboMultiplier(GameplayScreen screen) {
        int combo = screen.getScoreManager().getComboChain();
        if (combo < 2) return;

        float baseScale = font.getData().scaleX;
        float restingBoost = 1.7f;
        float pulseEnvelope = screen.getComboPulseTimer() / GameplayScreen.COMBO_PULSE_DURATION; // 1 right after an increment -> 0
        font.getData().setScale(baseScale * (restingBoost + 0.7f * pulseEnvelope));
        font.setColor(COMBO_TEXT_COLOR);

        float rightMargin = 20f;
        float boxWidth = Gdx.graphics.getWidth() / 2f - rightMargin;
        font.draw(batch, "x" + combo, Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() - 20f, boxWidth, com.badlogic.gdx.utils.Align.right, false);

        font.setColor(Color.WHITE);
        font.getData().setScale(baseScale);
    }

    private void drawToast(GameplayScreen screen) {
        if (screen.getToastTimer() <= 0f || screen.getToastText() == null) return;
        float alpha = Math.min(1f, screen.getToastTimer() / 0.5f); // quick fade in the final half-second
        font.setColor(1f, 1f, 1f, alpha);
        font.draw(batch, screen.getToastText(), 0f, Gdx.graphics.getHeight() * 0.7f, Gdx.graphics.getWidth(), com.badlogic.gdx.utils.Align.center, false);
        font.setColor(Color.WHITE);
    }

    private void drawPlatform(Body body) {
        if (body == null) return;
        Fixture fixture = body.getFixtureList().get(0);
        boolean weak = "weakPlatform".equals(fixture.getUserData());
        Color baseColor = weak ? WEAK_PLATFORM_COLOR : NORMAL_PLATFORM_COLOR;
        shapeRenderer.setColor(baseColor);

        PolygonShape shape = (PolygonShape) fixture.getShape();
        Vector2 v = new Vector2();
        shape.getVertex(0, v);
        float hx = Math.abs(v.x);
        float x = body.getPosition().x;
        float y = body.getPosition().y;
        shapeRenderer.rect(x - hx, y - GameplayScreen.PLATFORM_THICKNESS / 2f, hx * 2f, GameplayScreen.PLATFORM_THICKNESS);

        // Thin lighter strip along the top edge -- cheap "lit from above" cue.
        Color highlight = baseColor.cpy().lerp(Color.WHITE, 0.35f);
        shapeRenderer.setColor(highlight);
        float highlightThickness = GameplayScreen.PLATFORM_THICKNESS * 0.2f;
        shapeRenderer.rect(x - hx, y + GameplayScreen.PLATFORM_THICKNESS / 2f - highlightThickness, hx * 2f, highlightThickness);
    }

    // Small type-tag -> display-name mapping so the HUD doesn't print raw
    // registry keys. Add a case here alongside each new register() call.
    private String displayName(String type) {
        if ("wreckingBall".equals(type)) {
            return "WRECKING BALL";
        }
        return type == null ? "" : type.toUpperCase();
    }

    private void drawPowerUp(Body body) {
        if (body == null) return;
        shapeRenderer.setColor(POWERUP_COLOR);
        shapeRenderer.circle(body.getPosition().x, body.getPosition().y, GameplayScreen.POWERUP_RADIUS, 16);
    }

    private void drawDebris(Body body) {
        float size = 0.15f;
        float x = body.getPosition().x;
        float y = body.getPosition().y;
        float rotationDegrees = body.getAngle() * MathUtils.radDeg;
        shapeRenderer.rect(x - size / 2f, y - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, rotationDegrees);
    }

    void resize(int width, int height) {
        hudCamera.setToOrtho(false, width, height);
        updateFontScale();
    }

    private void updateFontScale() {
        float scale = Gdx.graphics.getHeight() / FONT_REFERENCE_HEIGHT;
        font.getData().setScale(Math.max(1f, scale));
    }

    void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }
}
