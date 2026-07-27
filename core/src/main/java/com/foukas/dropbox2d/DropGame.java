package com.foukas.dropbox2d;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Approach A: bare-minimum core loop, built with no engineering rigor
 * beyond what's needed to test whether tap-steer + real Box2D physics
 * feels good. No InputProvider abstraction, no reachability validator,
 * no NaN guard, no event-dispatch system, no tests -- all of that is
 * deliberately deferred to a Post-Validation Hardening phase (see the
 * design doc) if this bet is confirmed. Direct tap input, direct
 * score/game-over logic, direct Box2D ContactListener usage.
 */
public class DropGame extends ApplicationAdapter {

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

    private final List<PlatformRow> rows = new ArrayList<>();
    private final ArrayDeque<PlatformRow> pendingScoreRows = new ArrayDeque<>();
    private float lowestGeneratedY;
    private float spawnY;

    private float depthScore;
    private int comboChain;
    private boolean ballTouchedSinceLastRow;
    private boolean gameOver;

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
        world = new World(new com.badlogic.gdx.math.Vector2(0, GRAVITY_Y), true);
        world.setContactListener(new BallContactListener());

        rows.clear();
        pendingScoreRows.clear();
        depthScore = 0f;
        comboChain = 0;
        ballTouchedSinceLastRow = false;
        gameOver = false;
        physicsAccumulator = 0f;

        spawnY = 0f;
        ballBody = createBall(WORLD_WIDTH / 2f, spawnY + 1.5f);

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

    /** Basic randomized single-gap platform generator. No formal
     * reachability validator -- an occasional unfair gap is an accepted
     * risk for this raw validation prototype (see design doc). */
    private void spawnNextRow() {
        float rowY = lowestGeneratedY;
        lowestGeneratedY -= ROW_SPACING;

        float gapWidth = MathUtils.random(GAP_MIN_WIDTH, GAP_MAX_WIDTH);
        float gapStart = MathUtils.random(0.5f, WORLD_WIDTH - gapWidth - 0.5f);
        float gapEnd = gapStart + gapWidth;

        Body left = null;
        if (gapStart > 0.1f) {
            left = createPlatformSegment(0f, gapStart, rowY);
        }
        Body right = null;
        if (WORLD_WIDTH - gapEnd > 0.1f) {
            right = createPlatformSegment(gapEnd, WORLD_WIDTH, rowY);
        }

        PlatformRow row = new PlatformRow(rowY, left, right);
        rows.add(row);
        pendingScoreRows.addLast(row);
    }

    private Body createPlatformSegment(float xStart, float xEnd, float y) {
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
        fixture.setUserData("platform");
        shape.dispose();

        return body;
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);

        if (!gameOver) {
            handleInput(delta);
            stepPhysics(delta);
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
        com.badlogic.gdx.math.Vector2 vel = ballBody.getLinearVelocity();
        if (Math.abs(vel.x) > MAX_HORIZONTAL_SPEED) {
            ballBody.setLinearVelocity(MathUtils.clamp(vel.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED), vel.y);
        }
    }

    private void stepPhysics(float delta) {
        physicsAccumulator += delta;
        while (physicsAccumulator >= FIXED_TIMESTEP) {
            world.step(FIXED_TIMESTEP, 6, 2);
            physicsAccumulator -= FIXED_TIMESTEP;
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
    }

    private void manageRows() {
        // Generate ahead of the ball.
        while (ballBody.getPosition().y - lowestGeneratedY < ROW_SPACING * 6) {
            spawnNextRow();
        }

        // Score: consecutive gap-passes without a platform contact build a combo.
        while (!pendingScoreRows.isEmpty() && ballBody.getPosition().y < pendingScoreRows.peekFirst().y - PLATFORM_THICKNESS) {
            pendingScoreRows.pollFirst();
            if (ballTouchedSinceLastRow) {
                comboChain = 0;
            } else {
                comboChain++;
            }
            ballTouchedSinceLastRow = false;
        }

        // Recycle rows well above the current camera view -- unbounded growth
        // is a basic hygiene bug, not the kind of "engineering rigor" that
        // was scoped out (that was about event systems/validators/tests).
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
            rows.remove(row);
        }
    }

    private void checkGameOver() {
        float ballY = ballBody.getPosition().y;
        if (ballY - camera.position.y > WORLD_HEIGHT / 2f + TOP_MARGIN) {
            gameOver = true;
        }
    }

    private void draw() {
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(0.55f, 0.55f, 0.65f, 1f);
        for (PlatformRow row : rows) {
            drawBody(row.left);
            drawBody(row.right);
        }

        shapeRenderer.setColor(gameOver ? Color.RED : Color.ORANGE);
        shapeRenderer.circle(ballBody.getPosition().x, ballBody.getPosition().y, BALL_RADIUS, 24);

        shapeRenderer.end();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        String label = "Depth: " + (int) depthScore + "m   Combo: " + comboChain;
        if (gameOver) {
            label += "\nGAME OVER -- tap to retry";
        }
        font.draw(batch, label, 20f, Gdx.graphics.getHeight() - 20f);
        batch.end();
    }

    private void drawBody(Body body) {
        if (body == null) return;
        PolygonShape shape = (PolygonShape) body.getFixtureList().get(0).getShape();
        float hx = 0f;
        com.badlogic.gdx.math.Vector2 v = new com.badlogic.gdx.math.Vector2();
        shape.getVertex(0, v);
        hx = Math.abs(v.x);
        float x = body.getPosition().x;
        float y = body.getPosition().y;
        shapeRenderer.rect(x - hx, y - PLATFORM_THICKNESS / 2f, hx * 2f, PLATFORM_THICKNESS);
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

    private class BallContactListener implements ContactListener {
        @Override
        public void beginContact(Contact contact) {
            Fixture a = contact.getFixtureA();
            Fixture b = contact.getFixtureB();
            if (isBallPlatformContact(a, b)) {
                ballTouchedSinceLastRow = true;
            }
        }

        @Override
        public void endContact(Contact contact) {
        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {
        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {
        }

        private boolean isBallPlatformContact(Fixture a, Fixture b) {
            Object ua = a.getUserData();
            Object ub = b.getUserData();
            return ("ball".equals(ua) && "platform".equals(ub)) || ("ball".equals(ub) && "platform".equals(ua));
        }
    }

    private static class PlatformRow {
        final float y;
        final Body left;
        final Body right;

        PlatformRow(float y, Body left, Body right) {
            this.y = y;
            this.left = left;
            this.right = right;
        }
    }
}
