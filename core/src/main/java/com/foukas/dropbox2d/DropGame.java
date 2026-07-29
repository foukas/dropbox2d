package com.foukas.dropbox2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.foukas.dropbox2d.events.BallOffTop;
import com.foukas.dropbox2d.events.ContactDispatcher;
import com.foukas.dropbox2d.events.GameEvent;
import com.foukas.dropbox2d.events.GameEventBus;
import com.foukas.dropbox2d.events.GameEventListener;
import com.foukas.dropbox2d.events.GapPassed;
import com.foukas.dropbox2d.events.PlatformDestroyed;
import com.foukas.dropbox2d.events.PowerUpCollected;
import com.foukas.dropbox2d.generation.GapReachabilityValidator;
import com.foukas.dropbox2d.generation.PlatformType;
import com.foukas.dropbox2d.physics.DebrisManager;
import com.foukas.dropbox2d.physics.PhysicsNaNGuard;
import com.foukas.dropbox2d.powerups.WreckingBallManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Approach C built on the Post-Validation Hardening foundation: destructible
 * weak platforms (fixture removal, break-threshold driven by real Box2D
 * contact impulse) and the wrecking-ball power-up (a real density change,
 * not a scripted override -- see WreckingBallManager). PlatformDestroyed
 * and PowerUpCollected are new event types added to the existing bus;
 * nothing about the collision/scoring/game-over code from Hardening had to
 * change to accommodate them.
 */
public class DropGame extends ApplicationAdapter implements GameEventListener {

    // ----- World tuning (placeholder values -- expected to be re-tuned by feel) -----
    private static final float WORLD_WIDTH = 9f;
    private static final float WORLD_HEIGHT = 16f;
    private static final float GRAVITY_Y = -25f;

    private static final float BALL_RADIUS = 0.4f;
    private static final float BALL_DENSITY = 1f;
    private static final float BALL_FRICTION = 0.4f;
    private static final float BALL_RESTITUTION = 0.55f;
    private static final float BALL_LINEAR_DAMPING = 0.5f;

    private static final float STEER_FORCE = 55f;
    private static final float MAX_HORIZONTAL_SPEED = 6f;

    private static final float ROW_SPACING = 2.6f;
    private static final float PLATFORM_THICKNESS = 0.35f;
    private static final float GAP_MIN_WIDTH = 2.4f;
    private static final float GAP_MAX_WIDTH = 3.4f;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private static final float WEAK_PLATFORM_CHANCE = 0.35f;
    private static final float POWERUP_SPAWN_CHANCE = 0.15f;
    private static final float POWERUP_RADIUS = 0.25f;

    private static final float SCROLL_BASE_SPEED = 1.6f;
    private static final float SCROLL_RAMP_PER_METER = 0.02f;
    private static final float SCROLL_MAX_SPEED = 6.5f;

    private static final float TOP_MARGIN = 1.0f;
    // How far below the camera's vertical center the ball may fall before
    // the camera snaps down to match its velocity, instead of the fixed
    // scroll ramp -- mirrors the original game's "platforms catch up to a
    // fast drop" behavior. ~7 units is roughly a 3-5 platform freefall at
    // ROW_SPACING=2.6, matching the design doc's flavor text for a big drop.
    private static final float BOTTOM_FOLLOW_MARGIN = 7.0f;

    // Side walls: thin, tall static bodies re-centered on the camera every
    // frame so the ball can never be steered past the left/right edges of
    // the visible world, no matter how far the session has scrolled.
    private static final float WALL_THICKNESS = 0.3f;
    private static final float WALL_HEIGHT = 60f;

    private static final Color NORMAL_PLATFORM_COLOR = new Color(0.55f, 0.55f, 0.65f, 1f);
    private static final Color WEAK_PLATFORM_COLOR = new Color(0.65f, 0.5f, 0.3f, 1f);
    private static final Color DEBRIS_COLOR = new Color(0.5f, 0.4f, 0.35f, 1f);
    private static final Color POWERUP_COLOR = new Color(1f, 0.84f, 0f, 1f);
    private static final Color WRECKING_BALL_COLOR = new Color(0.55f, 0.1f, 0.1f, 1f);

    // ----- Runtime state -----
    private World world;
    private Body ballBody;
    private Body leftWall;
    private Body rightWall;
    private OrthographicCamera camera;
    private Viewport viewport;
    private OrthographicCamera hudCamera;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;

    private GameEventBus eventBus;
    private ScoreManager scoreManager;
    private GameOverController gameOverController;
    private DebrisManager debrisManager;
    private WreckingBallManager wreckingBallManager;

    private final List<PlatformRow> rows = new ArrayList<>();
    private final ArrayDeque<PlatformRow> pendingScoreRows = new ArrayDeque<>();
    private final List<PlatformDestroyed> pendingPlatformDestructions = new ArrayList<>();
    private final List<PowerUpCollected> pendingPowerUpPickups = new ArrayList<>();
    private float lowestGeneratedY;
    private float spawnY;

    private float depthScore;

    private static final float FIXED_TIMESTEP = 1f / 60f;
    private float physicsAccumulator;

    @Override
    public void create() {
        Box2D.init();

        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        updateFontScale();

        startNewRun();
    }

    private void startNewRun() {
        if (world != null) {
            world.dispose();
        }
        world = new World(new Vector2(0, GRAVITY_Y), true);

        eventBus = new GameEventBus();
        scoreManager = new ScoreManager();
        gameOverController = new GameOverController();
        eventBus.subscribe(scoreManager);
        eventBus.subscribe(gameOverController);
        eventBus.subscribe(this);
        world.setContactListener(new ContactDispatcher(eventBus));

        rows.clear();
        pendingScoreRows.clear();
        pendingPlatformDestructions.clear();
        pendingPowerUpPickups.clear();
        depthScore = 0f;
        physicsAccumulator = 0f;

        spawnY = 0f;
        ballBody = createBall(WORLD_WIDTH / 2f, spawnY + 1.5f);
        wreckingBallManager = new WreckingBallManager(ballBody);
        debrisManager = new DebrisManager(world);

        camera.position.set(WORLD_WIDTH / 2f, spawnY, 0f);
        camera.update();

        leftWall = createWall(-WALL_THICKNESS / 2f, camera.position.y);
        rightWall = createWall(WORLD_WIDTH + WALL_THICKNESS / 2f, camera.position.y);

        lowestGeneratedY = spawnY + 2f;
        // Pre-generate a handful of rows below the start so there's always
        // ground ahead of the ball.
        for (int i = 0; i < 8; i++) {
            spawnNextRow();
        }
    }

    @Override
    public void onEvent(GameEvent event) {
        // DropGame only reacts to the two event types that require Box2D
        // world mutation, which can't happen from inside the collision
        // callback that produced the event -- both are queued here and
        // drained after world.step() completes. GapPassed/BallOffTop/
        // BallTouchedPlatform are handled by ScoreManager/GameOverController.
        if (event instanceof PlatformDestroyed destroyed) {
            pendingPlatformDestructions.add(destroyed);
        } else if (event instanceof PowerUpCollected collected) {
            pendingPowerUpPickups.add(collected);
        }
    }

    private Body createBall(float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        bodyDef.fixedRotation = false;
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(BALL_RADIUS);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = BALL_DENSITY;
        fixtureDef.friction = BALL_FRICTION;
        fixtureDef.restitution = BALL_RESTITUTION;
        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData("ball");
        shape.dispose();

        body.setLinearDamping(BALL_LINEAR_DAMPING);
        return body;
    }

    private Body createWall(float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x, y);
        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(WALL_THICKNESS / 2f, WALL_HEIGHT / 2f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = 0.3f;
        fixtureDef.restitution = 0.2f;
        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData("wall");
        shape.dispose();

        return body;
    }

    /** Procedural single-gap platform generator, gated by
     * GapReachabilityValidator. Each flanking segment independently rolls
     * NORMAL vs WEAK -- weak segments never replace the gap itself, only
     * add an optional shortcut alongside it (see PlatformType's class
     * comment for why that resolves the reachability-risk open question).
     * A power-up pickup occasionally spawns centered in the gap. */
    private void spawnNextRow() {
        float rowY = lowestGeneratedY;
        lowestGeneratedY -= ROW_SPACING;

        // Rows are generated ~6 rows ahead of the ball (see manageRows), so
        // the ball has that whole lookahead distance -- not just one row's
        // worth -- to reposition before this specific row matters. Using
        // only a single row's fall time here would make the validator
        // reject the large majority of gaps against these tuning constants
        // (worst-case horizontal distances routinely exceed what one row's
        // fall time allows), which defeats the point of the check. This is
        // still a coarse fairness gate, not a full difficulty simulator --
        // see the design doc's Open Questions on feel-tuning needing
        // playtesting on top of this.
        float lookaheadRows = 6f;
        float timeToFall = (float) Math.sqrt(2 * (ROW_SPACING * lookaheadRows) / Math.abs(GRAVITY_Y));

        float gapWidth = 0f;
        float gapStart = 0f;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            gapWidth = MathUtils.random(GAP_MIN_WIDTH, GAP_MAX_WIDTH);
            gapStart = MathUtils.random(0.5f, WORLD_WIDTH - gapWidth - 0.5f);
            if (GapReachabilityValidator.isReachable(gapStart, gapWidth, WORLD_WIDTH, MAX_HORIZONTAL_SPEED, timeToFall)) {
                break;
            }
        }
        float gapEnd = gapStart + gapWidth;

        Body left = null;
        if (gapStart > 0.1f) {
            PlatformType type = MathUtils.random() < WEAK_PLATFORM_CHANCE ? PlatformType.WEAK : PlatformType.NORMAL;
            left = createPlatformSegment(0f, gapStart, rowY, type);
        }
        Body right = null;
        if (WORLD_WIDTH - gapEnd > 0.1f) {
            PlatformType type = MathUtils.random() < WEAK_PLATFORM_CHANCE ? PlatformType.WEAK : PlatformType.NORMAL;
            right = createPlatformSegment(gapEnd, WORLD_WIDTH, rowY, type);
        }

        Body powerUp = null;
        if (MathUtils.random() < POWERUP_SPAWN_CHANCE) {
            float pickupX = gapStart + gapWidth / 2f;
            powerUp = createPowerUpPickup(pickupX, rowY);
        }

        PlatformRow row = new PlatformRow(rowY, left, right, powerUp);
        rows.add(row);
        pendingScoreRows.addLast(row);
    }

    private Body createPlatformSegment(float xStart, float xEnd, float y, PlatformType type) {
        float width = xEnd - xStart;
        float centerX = xStart + width / 2f;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(centerX, y);
        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width / 2f, PLATFORM_THICKNESS / 2f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = 0.6f;
        fixtureDef.restitution = 0f;
        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData(type == PlatformType.WEAK ? "weakPlatform" : "platform");
        shape.dispose();

        return body;
    }

    private Body createPowerUpPickup(float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x, y);
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(POWERUP_RADIUS);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;
        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData("powerUp");
        shape.dispose();

        return body;
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);

        if (!gameOverController.isGameOver()) {
            handleInput(delta);
            stepPhysics(delta);
            drainPendingWorldMutations();
            wreckingBallManager.update(delta);
            updateCameraAndScroll(delta);
            manageRows();
            checkGameOver();
        } else if (Gdx.input.justTouched()) {
            startNewRun();
        }

        draw();
    }

    private void handleInput(float delta) {
        boolean touched = Gdx.input.isTouched();
        if (touched) {
            float touchX = Gdx.input.getX();
            float screenHalf = Gdx.graphics.getWidth() / 2f;
            float direction = touchX < screenHalf ? -1f : 1f;

            float vx = ballBody.getLinearVelocity().x;
            if (Math.signum(vx) != direction || Math.abs(vx) < MAX_HORIZONTAL_SPEED) {
                ballBody.applyForceToCenter(direction * STEER_FORCE, 0f, true);
            }
        }

        // Clamp horizontal speed directly -- no separate guard abstraction,
        // just inline clamping, consistent with the "direct" scope decision.
        Vector2 vel = ballBody.getLinearVelocity();
        if (Math.abs(vel.x) > MAX_HORIZONTAL_SPEED) {
            ballBody.setLinearVelocity(MathUtils.clamp(vel.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED), vel.y);
        }
    }

    private void stepPhysics(float delta) {
        physicsAccumulator += delta;
        while (physicsAccumulator >= FIXED_TIMESTEP) {
            world.step(FIXED_TIMESTEP, 6, 2);
            physicsAccumulator -= FIXED_TIMESTEP;
            PhysicsNaNGuard.checkAndFix(ballBody, WORLD_WIDTH / 2f, camera.position.y);
        }
    }

    /** Box2D forbids creating/destroying bodies from inside a collision
     * callback -- ContactDispatcher only ever dispatches events during
     * those callbacks, so the actual world mutation happens here, right
     * after world.step() returns and the world is unlocked. */
    private void drainPendingWorldMutations() {
        for (PlatformDestroyed destroyed : pendingPlatformDestructions) {
            removeBodyFromRows(destroyed.body());
            world.destroyBody(destroyed.body());
            debrisManager.spawnDebris(destroyed.x(), destroyed.y());
        }
        pendingPlatformDestructions.clear();

        for (PowerUpCollected collected : pendingPowerUpPickups) {
            removeBodyFromRows(collected.body());
            world.destroyBody(collected.body());
            wreckingBallManager.activate();
        }
        pendingPowerUpPickups.clear();
    }

    private void removeBodyFromRows(Body body) {
        for (PlatformRow row : rows) {
            if (row.left == body) row.left = null;
            if (row.right == body) row.right = null;
            if (row.powerUp == body) row.powerUp = null;
        }
    }

    private void updateCameraAndScroll(float delta) {
        float depth = spawnY - ballBody.getPosition().y;
        depthScore = Math.max(depthScore, depth);

        float scrollSpeed = Math.min(SCROLL_BASE_SPEED + depthScore * SCROLL_RAMP_PER_METER, SCROLL_MAX_SPEED);
        camera.position.y -= scrollSpeed * delta;

        // Catch-up: if the ball is falling faster than the fixed scroll
        // (e.g. dropping through several gaps in one continuous fall), the
        // camera must not let it fall out of view -- snap down to keep the
        // ball within BOTTOM_FOLLOW_MARGIN of the viewport center, matching
        // the original game's "platforms move to match its velocity" rule.
        float ballY = ballBody.getPosition().y;
        camera.position.y = Math.min(camera.position.y, ballY + BOTTOM_FOLLOW_MARGIN);

        camera.update();

        // Re-center the side walls on the camera so they always cover the
        // visible range, regardless of how far the session has scrolled.
        leftWall.setTransform(leftWall.getPosition().x, camera.position.y, 0f);
        rightWall.setTransform(rightWall.getPosition().x, camera.position.y, 0f);

        float recycleAboveY = camera.position.y + WORLD_HEIGHT;
        debrisManager.update(delta, recycleAboveY);
    }

    private void manageRows() {
        // Generate ahead of the ball.
        while (ballBody.getPosition().y - lowestGeneratedY < ROW_SPACING * 6) {
            spawnNextRow();
        }

        // Score: dispatch GapPassed as the ball crosses each row's Y level.
        // ScoreManager (an independent subscriber) owns the combo logic.
        while (!pendingScoreRows.isEmpty() && ballBody.getPosition().y < pendingScoreRows.peekFirst().y - PLATFORM_THICKNESS) {
            PlatformRow row = pendingScoreRows.pollFirst();
            eventBus.dispatch(new GapPassed(row.y));
        }

        // Recycle rows well above the current camera view -- unbounded growth
        // is a basic hygiene bug, not the kind of "engineering rigor" that
        // was scoped out (that was about event systems/validators/tests,
        // and those are now built -- see the class-level note above).
        float recycleAboveY = camera.position.y + WORLD_HEIGHT;
        List<PlatformRow> toRemove = new ArrayList<>();
        for (PlatformRow row : rows) {
            if (row.y > recycleAboveY) {
                toRemove.add(row);
            }
        }
        for (PlatformRow row : toRemove) {
            if (row.left != null) world.destroyBody(row.left);
            if (row.right != null) world.destroyBody(row.right);
            if (row.powerUp != null) world.destroyBody(row.powerUp);
            rows.remove(row);
        }
    }

    private void checkGameOver() {
        float ballY = ballBody.getPosition().y;
        if (ballY - camera.position.y > WORLD_HEIGHT / 2f + TOP_MARGIN) {
            eventBus.dispatch(new BallOffTop());
        }
    }

    private void draw() {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (PlatformRow row : rows) {
            drawPlatform(row.left);
            drawPlatform(row.right);
            drawPowerUp(row.powerUp);
        }

        shapeRenderer.setColor(DEBRIS_COLOR);
        for (Body debris : debrisManager.getBodies()) {
            drawDebris(debris);
        }

        boolean gameOver = gameOverController.isGameOver();
        Color ballColor = gameOver ? Color.RED : (wreckingBallManager.isActive() ? WRECKING_BALL_COLOR : Color.ORANGE);
        shapeRenderer.setColor(ballColor);
        shapeRenderer.circle(ballBody.getPosition().x, ballBody.getPosition().y, BALL_RADIUS, 24);

        shapeRenderer.end();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        StringBuilder label = new StringBuilder("Depth: ").append((int) depthScore)
            .append("m   Combo: ").append(scoreManager.getComboChain());
        if (wreckingBallManager.isActive()) {
            label.append(String.format("\nWRECKING BALL: %.1fs", wreckingBallManager.getRemaining()));
        }
        if (gameOver) {
            label.append("\nGAME OVER -- tap to retry");
        }
        font.draw(batch, label.toString(), 20f, Gdx.graphics.getHeight() - 20f);
        batch.end();
    }

    private void drawPlatform(Body body) {
        if (body == null) return;
        Fixture fixture = body.getFixtureList().get(0);
        boolean weak = "weakPlatform".equals(fixture.getUserData());
        shapeRenderer.setColor(weak ? WEAK_PLATFORM_COLOR : NORMAL_PLATFORM_COLOR);

        PolygonShape shape = (PolygonShape) fixture.getShape();
        Vector2 v = new Vector2();
        shape.getVertex(0, v);
        float hx = Math.abs(v.x);
        float x = body.getPosition().x;
        float y = body.getPosition().y;
        shapeRenderer.rect(x - hx, y - PLATFORM_THICKNESS / 2f, hx * 2f, PLATFORM_THICKNESS);
    }

    private void drawPowerUp(Body body) {
        if (body == null) return;
        shapeRenderer.setColor(POWERUP_COLOR);
        shapeRenderer.circle(body.getPosition().x, body.getPosition().y, POWERUP_RADIUS, 16);
    }

    private void drawDebris(Body body) {
        float size = 0.15f;
        float x = body.getPosition().x;
        float y = body.getPosition().y;
        float rotationDegrees = body.getAngle() * MathUtils.radDeg;
        shapeRenderer.rect(x - size / 2f, y - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, rotationDegrees);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        hudCamera.setToOrtho(false, width, height);
        updateFontScale();
    }

    // The default BitmapFont is sized in raw pixels (~15px cap height),
    // which reads fine on the ~960px-tall desktop dev window but is tiny
    // on a much higher-resolution phone screen. Scale it relative to
    // screen height so it stays legible across both.
    private static final float FONT_REFERENCE_HEIGHT = 960f;

    private void updateFontScale() {
        if (font == null) return;
        float scale = Gdx.graphics.getHeight() / FONT_REFERENCE_HEIGHT;
        font.getData().setScale(Math.max(1f, scale));
    }

    @Override
    public void dispose() {
        world.dispose();
        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }

    private static class PlatformRow {
        final float y;
        Body left;
        Body right;
        Body powerUp;

        PlatformRow(float y, Body left, Body right, Body powerUp) {
            this.y = y;
            this.left = left;
            this.right = right;
            this.powerUp = powerUp;
        }
    }
}
