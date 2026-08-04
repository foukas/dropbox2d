package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.math.Vector2;
import com.crashinvaders.vfx.VfxManager;
import com.crashinvaders.vfx.effects.BloomEffect;
import com.crashinvaders.vfx.effects.CrtEffect;
import com.foukas.dropbox2d.fx.DepthAtmosphere;
import com.foukas.dropbox2d.fx.MotionTrail;
import com.foukas.dropbox2d.fx.ParticleSystem;
import com.foukas.dropbox2d.fx.ScreenShake;
import com.foukas.dropbox2d.platform.SafeAreaInsets;
import com.foukas.dropbox2d.progression.Biome;
import com.foukas.dropbox2d.progression.PlayerProgress;
import com.foukas.dropbox2d.progression.SkinTier;

import java.util.Deque;

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

    // Synthwave palette "A -- Classic Synthwave" (plan-eng-review Next Step 7,
    // user-picked from a 3-option visual comparison). Hex values kept in
    // comments since libGDX's Color constructor takes 0-1 floats, not hex.
    private static final Color NORMAL_PLATFORM_COLOR = new Color(0f, 0.898f, 1f, 1f); // #00e5ff cyan
    private static final Color WEAK_PLATFORM_COLOR = new Color(1f, 0.4196f, 0.2078f, 1f); // #ff6b35 orange
    private static final Color POWERUP_COLOR = new Color(1f, 0.8235f, 0.2471f, 1f); // #ffd23f gold
    private static final Color WRECKING_BALL_COLOR = new Color(1f, 0.1765f, 0.1765f, 1f); // #ff2d2d red
    // Background palette is now owned by the active Biome (plan-eng-review
    // biome progression step 4) -- DepthAtmosphere.saturationFor()'s
    // grid-line intensity and SkinTier's ball/platform colors stay on
    // their existing, separate (non-cycling, depth-capped-at-300) axis for
    // this slice, not folded into the biome system yet. The old hardcoded
    // BG_TOP_COLOR/BG_BOTTOM_COLOR constants are gone -- Biome.NEON_DEPTHS
    // carries the identical values, so nothing duplicates them anymore.
    private static final Color COMBO_TEXT_COLOR = new Color(1f, 0.8824f, 0.3020f, 1f); // #ffe14d gold
    private static final Color PAUSE_DIM_COLOR = new Color(0f, 0f, 0f, 0.6f);

    // Scrolling grid-horizon (Next Step 8) -- the vivid pink from the
    // original 3-option palette mockup (see Next Step 7), held back from
    // the base gradient and used here instead as the "reward" color that
    // only fully emerges at depth, via DepthAtmosphere.saturationFor().
    // Drawn as filled rects, not GL_LINES -- glLineWidth beyond 1px is
    // unreliable/ignored on most GL drivers, so hairline GL_LINES read as
    // flat clutter with no glow. A dim, wide "halo" rect behind a bright,
    // thin "core" rect fakes a glow, same trick already used for the ball
    // highlight and platform top-edge highlight in this file.
    private static final Color GRID_LINE_COLOR = new Color(1f, 0.1804f, 0.7686f, 1f); // #ff2ec4
    private static final float GRID_LINE_MAX_SPACING = 4f; // world units, near the surface (sparse)
    private static final float GRID_LINE_MIN_SPACING = 1.5f; // world units, at depth (dense)
    private static final float GRID_CORE_THICKNESS = 0.025f; // world units, constant
    private static final float GRID_GLOW_MIN_THICKNESS = 0.08f;
    private static final float GRID_GLOW_MAX_THICKNESS = 0.22f;

    // Ball motion trail (Next Step 9) -- additive blending so overlapping
    // trail circles brighten instead of just alpha-compositing, giving a
    // "glowing afterimage" look. Same color as the ball itself (including
    // wrecking-ball red), fading from newest (front of the deque) to
    // oldest (back).
    private static final float TRAIL_MAX_ALPHA = 0.5f;
    private static final float TRAIL_MIN_SIZE_FRACTION = 0.3f;
    private static final float TRAIL_MAX_SIZE_FRACTION = 0.7f;

    // HUD layout (Next Step 11) -- distinct positioned elements instead of a
    // single StringBuilder dump, so the HUD reads as a deliberate interface
    // rather than debug text (the complaint that started this whole
    // redesign). Scales are expressed as multipliers of the normal
    // HudFontScale-driven base scale, not absolute pixel sizes, so this
    // still tracks screen size the same way the old uniform text did.
    // Corner text needs more breathing room than an icon does -- device
    // verification (Next Step 12) found the depth/Best/Streak text still
    // clipped a little under the physical rounded screen corners even after
    // accounting for the real system-bar insets (those only cover the
    // status/nav bar height, not the corner radius). A fixed extra buffer is
    // a simpler, universal-enough fix than querying the per-device corner
    // radius (RoundedCorner API is 31+; minSdk here is 24).
    private static final float HUD_TEXT_MARGIN = 40f;
    private static final float HUD_DEPTH_SCALE = 1.8f;
    private static final float HUD_STAT_SCALE = 0.85f;
    private static final Color HUD_STAT_COLOR = new Color(1f, 1f, 1f, 0.75f);
    private static final float HUD_MARKER_RADIUS = 6f;
    private static final float HUD_MARKER_TEXT_GAP = 6f;

    private final ScreenShake screenShake;
    private final ParticleSystem particleSystem;
    private final PlayerProgress playerProgress;
    private final SafeAreaInsets safeAreaInsets;

    private final OrthographicCamera hudCamera;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch batch;
    private final BitmapFont font;

    // Post-process FBO pipeline (Next Step 10) -- wraps the entire draw()
    // output (background, gameplay, HUD, everything). Constructed once,
    // same construct-once-reuse lifetime as everything else in this class
    // (see GameplayScreen's/DropGame's class docs) -- accepted risk, no
    // try/catch fallback if FBO init fails on an unsupported GPU (decided
    // during /plan-eng-review Code Quality Issue 1; see the design doc's
    // Reviewer Concerns).
    private final VfxManager vfxManager;
    private final BloomEffect bloomEffect;
    private final CrtEffect crtEffect;

    GameplayRenderer(ScreenShake screenShake, ParticleSystem particleSystem,
                      PlayerProgress playerProgress, SafeAreaInsets safeAreaInsets) {
        this.screenShake = screenShake;
        this.particleSystem = particleSystem;
        this.playerProgress = playerProgress;
        this.safeAreaInsets = safeAreaInsets;

        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = HudFont.generate();

        vfxManager = new VfxManager(Pixmap.Format.RGBA8888, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        bloomEffect = new BloomEffect();
        // Low threshold since this scene has no real HDR data -- without
        // it, the flat 0-1 color range barely triggers bloom at all.
        bloomEffect.setThreshold(0.3f);
        bloomEffect.setBloomIntensity(1.2f);
        bloomEffect.setBlurPasses(2);
        crtEffect = new CrtEffect();
        vfxManager.addEffect(bloomEffect);
        vfxManager.addEffect(crtEffect);
        font.setColor(Color.WHITE);
        updateFontScale();
    }

    void draw(GameplayScreen screen) {
        vfxManager.cleanUpBuffers();
        vfxManager.beginInputCapture();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Needed for particle fade-out and the ball highlight's alpha --
        // solid shapes elsewhere use alpha=1 so this doesn't change how
        // they look.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        OrthographicCamera camera = screen.getCamera();
        drawSkyGradient(Biome.biomeFor(screen.getDepthScore()));

        // vfxManager.beginInputCapture() (above) rebinds its own FBO, which
        // resets the GL viewport to the FBO's full pixel size -- discarding
        // the aspect-correct viewport GameplayScreen's ExtendViewport
        // applied on resize(). Only world-space content (grid, platforms,
        // ball, trail) needs that viewport; without re-applying it here,
        // world geometry gets non-uniformly stretched to fill the full
        // framebuffer instead (e.g. the ball rendering as an oval) on any
        // device whose aspect ratio isn't exactly WORLD_WIDTH:WORLD_HEIGHT --
        // invisible on the 9:16 desktop dev window, visible on an actual
        // phone. Screen-space content (sky gradient above, HUD below) still
        // wants the full viewport, so it's restored again after the
        // world-space block ends.
        screen.getViewport().apply();

        float saturation = DepthAtmosphere.saturationFor(screen.getDepthScore(), screen.currentSkinTier());
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawGridLines(camera, saturation, screen.getViewport().getWorldHeight());
        shapeRenderer.end();

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
        drawTrail(screen, ballColor);
        shapeRenderer.setColor(ballColor);
        shapeRenderer.circle(ballBody.getPosition().x, ballBody.getPosition().y, GameplayScreen.BALL_RADIUS, 24);
        drawBallHighlight(ballBody, ballColor);

        shapeRenderer.end();

        // Restore the full-framebuffer viewport for the screen-space content
        // below (pause dim, HUD markers/text) -- see the comment above
        // screen.getViewport().apply().
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        boolean paused = screen.isPaused();
        if (paused) {
            drawPauseDim();
        }
        drawHudMarkers(screen);

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        drawHudText(screen);

        drawComboMultiplier(screen);
        drawToast(screen);
        if (paused) {
            drawPauseText();
        }

        batch.end();

        vfxManager.endInputCapture();
        vfxManager.applyEffects();
        vfxManager.renderToScreen();
    }

    /** Dim rect over the last-rendered (frozen, since physics isn't
     * stepping while paused) gameplay frame -- its own shapeRenderer
     * begin/end pair, called BEFORE batch.begin() (see draw()) since
     * ShapeRenderer and SpriteBatch must not have overlapping begin/end
     * blocks. See the design doc's Open Questions for why this is manual
     * ShapeRenderer/BitmapFont rather than a Scene2D Stage. */
    private void drawPauseDim() {
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(PAUSE_DIM_COLOR);
        shapeRenderer.rect(0f, 0f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.end();
    }

    private void drawPauseText() {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        font.draw(batch, "PAUSED", 0f, height * 0.6f, width, com.badlogic.gdx.utils.Align.center, false);
        font.draw(batch, "Tap to resume", 0f, height * 0.5f, width, com.badlogic.gdx.utils.Align.center, false);
        font.draw(batch, "Tap here to quit to menu", 0f, font.getLineHeight() + 10f, width, com.badlogic.gdx.utils.Align.center, false);
    }

    /** Power-up indicator dot for the HUD -- a separate ShapeRenderer pass,
     * called BEFORE batch.begin() (see draw()), same reason as
     * drawPauseDim(). Position is derived from the same font-metric math
     * drawHudText() uses so the two stay aligned without sharing mutable
     * state between them. The bottom-left TAP/TILT marker this used to also
     * draw is gone (device verification, Next Step 12) -- it left the
     * bottom-left corner mostly empty background on taller-than-9:16
     * screens, not worth the corner space once the "TAP"/"TILT" text next
     * to it (the thing that actually needed the marker for context) was
     * already dropped for the same reason. */
    private void drawHudMarkers(GameplayScreen screen) {
        if (!screen.getPowerUpManager().isActive()) {
            return;
        }

        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        float height = Gdx.graphics.getHeight();
        float textMarginLeft = HUD_TEXT_MARGIN + safeAreaInsets.left();
        float textMarginTop = HUD_TEXT_MARGIN + safeAreaInsets.top();
        float baseLineHeight = font.getLineHeight();
        float statLineHeight = baseLineHeight * HUD_STAT_SCALE;
        float depthLineHeight = baseLineHeight * HUD_DEPTH_SCALE;
        float markerY = height - textMarginTop - depthLineHeight - statLineHeight / 2f;
        shapeRenderer.setColor(WRECKING_BALL_COLOR);
        shapeRenderer.circle(textMarginLeft + HUD_MARKER_RADIUS, markerY, HUD_MARKER_RADIUS, 12);

        shapeRenderer.end();
    }

    /** Depth prominent and unlabeled (top-left, Next Step 11's main
     * requirement) -- Best/Streak (top-right) and the controls badge
     * (bottom-left) deliberately smaller so they read as secondary stats
     * rather than competing with the depth number. Their marker dots/shapes
     * are drawn separately by drawHudMarkers(); this method only calls
     * font.draw(). Replaces the pre-redesign single StringBuilder dump.
     * The GAME OVER text this HUD used to show for one transitional frame
     * before game.setScreen(gameOverScreen) fired is gone too --
     * GameOverScreen has fully owned that screen since Next Step 5. */
    private void drawHudText(GameplayScreen screen) {
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        float baseScale = font.getData().scaleX;
        // Android targets SDK 35+, where edge-to-edge is enforced and can't
        // be opted out of -- content draws under the status/gesture-nav
        // bars, so corner-positioned HUD text needs the real system-bar
        // inset added on top of HUD_TEXT_MARGIN or it renders (partly)
        // behind them. Zero on desktop (NoOpSafeAreaInsets).
        float marginLeft = HUD_TEXT_MARGIN + safeAreaInsets.left();
        float marginRight = HUD_TEXT_MARGIN + safeAreaInsets.right();
        float marginTop = HUD_TEXT_MARGIN + safeAreaInsets.top();

        font.setColor(Color.WHITE);
        font.getData().setScale(baseScale * HUD_DEPTH_SCALE);
        float depthLineHeight = font.getLineHeight();
        font.draw(batch, (int) screen.getDepthScore() + "m", marginLeft, height - marginTop);

        font.getData().setScale(baseScale * HUD_STAT_SCALE);
        float statLineHeight = font.getLineHeight();

        if (screen.getPowerUpManager().isActive()) {
            String countdown = String.format("%s %.1fs", displayName(screen.getPowerUpManager().getActiveType()), screen.getPowerUpManager().getRemaining());
            float textX = marginLeft + HUD_MARKER_RADIUS * 2f + HUD_MARKER_TEXT_GAP;
            float textY = height - marginTop - depthLineHeight;
            font.draw(batch, countdown, textX, textY);
        }

        font.setColor(HUD_STAT_COLOR);
        float statRight = width - marginRight;
        float bestY = height - marginTop;
        font.draw(batch, "BEST " + (int) playerProgress.getBestDepth() + "m", 0f, bestY, statRight, com.badlogic.gdx.utils.Align.right, false);
        float streakY = bestY - statLineHeight;
        font.draw(batch, "STREAK " + playerProgress.getStreak() + "d", 0f, streakY, statRight, com.badlogic.gdx.utils.Align.right, false);

        font.setColor(Color.WHITE);
        font.getData().setScale(baseScale);
    }

    /** Sky gradient (Next Step 7's flat gradient, now biome-driven as of
     * biome progression step 4), drawn screen-space at the full framebuffer
     * viewport so it covers the whole device screen -- including any
     * letterbox area outside the world camera's aspect-correct viewport
     * (see the comment in draw() around screen.getViewport().apply()) --
     * rather than being confined to it. */
    private void drawSkyGradient(Biome biome) {
        shapeRenderer.setProjectionMatrix(hudCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        Color bottom = biome.getBottomColor();
        Color top = biome.getTopColor();
        shapeRenderer.rect(0f, 0f, w, h, bottom, bottom, top, top);
        shapeRenderer.end();
    }

    /** Scrolling grid-horizon (Next Step 8): world-space horizontal lines
     * that scroll naturally with the camera -- no manual offset tracking
     * needed, since drawing them with the world camera's own projection
     * matrix makes that automatic. Sparse and nearly invisible near the
     * surface, denser and fully neon-bright at depth, via
     * DepthAtmosphere.saturationFor() -- "depth is the light source"
     * (Cross-Model Perspective). viewportWorldHeight is the live
     * ExtendViewport height (Next Step 12), not the WORLD_HEIGHT constant --
     * it's larger on screens taller than 9:16, and using the fixed constant
     * would leave the top/bottom of an extended viewport ungridded. */
    private void drawGridLines(OrthographicCamera camera, float saturation, float viewportWorldHeight) {
        float spacing = MathUtils.lerp(GRID_LINE_MAX_SPACING, GRID_LINE_MIN_SPACING, saturation);
        float viewBottom = camera.position.y - viewportWorldHeight / 2f;
        float viewTop = camera.position.y + viewportWorldHeight / 2f;
        float firstLineY = MathUtils.floor(viewBottom / spacing) * spacing;

        Color glowColor = GRID_LINE_COLOR.cpy();
        glowColor.a = MathUtils.lerp(0.02f, 0.35f, saturation);
        Color coreColor = GRID_LINE_COLOR.cpy().lerp(Color.WHITE, 0.3f);
        coreColor.a = MathUtils.lerp(0.1f, 0.9f, saturation);
        float glowThickness = MathUtils.lerp(GRID_GLOW_MIN_THICKNESS, GRID_GLOW_MAX_THICKNESS, saturation);

        for (float y = firstLineY; y <= viewTop; y += spacing) {
            shapeRenderer.setColor(glowColor);
            shapeRenderer.rect(0f, y - glowThickness / 2f, GameplayScreen.WORLD_WIDTH, glowThickness);
            shapeRenderer.setColor(coreColor);
            shapeRenderer.rect(0f, y - GRID_CORE_THICKNESS / 2f, GameplayScreen.WORLD_WIDTH, GRID_CORE_THICKNESS);
        }
    }

    /** Additive-blended fading circles at the ball's recent positions --
     * flush() before and after the blend-func switch, since ShapeRenderer
     * batches draw calls and GL state applies at flush time, not at
     * geometry-submission time; without the flushes, already-queued
     * platform/debris/particle geometry (or the ball drawn right after)
     * could render with the wrong blend mode. */
    private void drawTrail(GameplayScreen screen, Color ballColor) {
        Deque<MotionTrail.Point> positions = screen.getTrailPositions();
        if (positions.isEmpty()) {
            return;
        }

        shapeRenderer.flush();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        int total = positions.size();
        int i = 0;
        for (MotionTrail.Point p : positions) {
            float fraction = 1f - (float) i / total; // 1 at newest (front) -> toward 0 at oldest
            shapeRenderer.setColor(ballColor.r, ballColor.g, ballColor.b, fraction * TRAIL_MAX_ALPHA);
            float size = GameplayScreen.BALL_RADIUS * MathUtils.lerp(TRAIL_MIN_SIZE_FRACTION, TRAIL_MAX_SIZE_FRACTION, fraction);
            shapeRenderer.circle(p.x(), p.y(), size, 12);
            i++;
        }

        shapeRenderer.flush();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
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
        vfxManager.resize(width, height);
    }

    private void updateFontScale() {
        font.getData().setScale(HudFontScale.forScreenHeight(Gdx.graphics.getHeight()));
    }

    void dispose() {
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
        vfxManager.dispose();
        bloomEffect.dispose();
        crtEffect.dispose();
    }
}
