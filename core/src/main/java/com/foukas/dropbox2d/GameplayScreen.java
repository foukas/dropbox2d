package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
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
import com.foukas.dropbox2d.fx.ParticleSystem;
import com.foukas.dropbox2d.fx.ScreenShake;
import com.foukas.dropbox2d.generation.GapReachabilityValidator;
import com.foukas.dropbox2d.input.InputProvider;
import com.foukas.dropbox2d.input.TapInputProvider;
import com.foukas.dropbox2d.input.TiltInputProvider;
import com.foukas.dropbox2d.generation.PlatformType;
import com.foukas.dropbox2d.monetization.AdProvider;
import com.foukas.dropbox2d.physics.DebrisManager;
import com.foukas.dropbox2d.physics.PhysicsNaNGuard;
import com.foukas.dropbox2d.powerups.PowerUpManager;
import com.foukas.dropbox2d.powerups.WreckingBallPowerUp;
import com.foukas.dropbox2d.progression.PlayerProgress;
import com.foukas.dropbox2d.progression.SkinTier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracted from DropGame (plan-eng-review Next Step 1, 2026-07-29) as a
 * pure move -- zero behavior change. DropGame now owns the app-lifetime
 * objects (PlayerProgress, ScreenShake, ParticleSystem, AdProvider) and
 * constructor-injects them here, matching the pattern PlayerProgress itself
 * already uses (constructor-injected Preferences, not a singleton) -- see
 * the design doc's Screen/State Flow section for the full rationale.
 */
public class GameplayScreen implements Screen, GameEventListener {

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
    // Registered power-up types (must match PowerUpManager.register keys in
    // startNewRun). One entry today; adding a second type is adding it here
    // plus a register() call -- pickup placement/rendering stay type-agnostic.
    private static final String[] POWER_UP_TYPES = {"wreckingBall"};

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
    // Wide contrast on purpose -- the first attempt (0.14 vs 0.04) was
    // nearly indistinguishable from the old flat clear color at a glance.
    private static final Color BG_TOP_COLOR = new Color(0.32f, 0.36f, 0.52f, 1f);
    private static final Color BG_BOTTOM_COLOR = new Color(0.02f, 0.02f, 0.04f, 1f);
    private static final Color COMBO_TEXT_COLOR = new Color(1f, 0.85f, 0.2f, 1f);

    // B2/B4 juice tuning
    private static final float COMBO_PULSE_DURATION = 0.3f;
    private static final float COMBO_SHAKE_DURATION = 0.15f;
    private static final float COMBO_SHAKE_MAGNITUDE = 0.08f;
    private static final float BREAK_SHAKE_DURATION = 0.2f;
    private static final float BREAK_SHAKE_MAGNITUDE = 0.12f;
    private static final float TOAST_DURATION = 2.5f;

    // B1: game-over-only tap zone (screen pixels from the top) that toggles
    // control scheme instead of retrying. Kept off the main play area on
    // purpose -- tapping anywhere during active play already means "steer,"
    // so a toggle button there would either get hit accidentally or need
    // its own no-steer exclusion zone during gameplay for no real benefit.
    private static final float CONTROL_TOGGLE_ZONE_HEIGHT = 100f;

    // Monetization plumbing: a second game-over tap zone, directly below the
    // control-toggle band, offering a free wrecking-ball power-up on the next
    // run in exchange for a rewarded ad. Gated behind adProvider.isRewardedAdReady()
    // for both the tap handler and the hint text, so with NoOpAdProvider (the
    // only implementation that exists today) this zone is entirely inert and
    // invisible -- see AdProvider's class doc for what it takes to go live.
    private static final float AD_OFFER_ZONE_HEIGHT = 200f;

    // ----- App-lifetime objects, owned by DropGame, constructor-injected -----
    private final PlayerProgress playerProgress;
    private final ScreenShake screenShake;
    private final ParticleSystem particleSystem;
    private final AdProvider adProvider;

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
    private PowerUpManager powerUpManager;

    private SkinTier highestAnnouncedTier;
    private int lastComboChain;
    private String toastText;
    private float toastTimer;
    private float comboPulseTimer;

    // B1: persistent across runs, loaded from PlayerProgress at construction.
    private InputProvider inputProvider;
    private boolean useTilt;

    // Monetization plumbing: persistent across runs. pendingRewardedWreckingBall
    // is set when a rewarded ad completes and consumed at the start of the
    // next run.
    private boolean pendingRewardedWreckingBall;

    private final List<PlatformRow> rows = new ArrayList<>();
    private final ArrayDeque<PlatformRow> pendingScoreRows = new ArrayDeque<>();
    private final List<PlatformDestroyed> pendingPlatformDestructions = new ArrayList<>();
    private final List<PowerUpCollected> pendingPowerUpPickups = new ArrayList<>();
    private float lowestGeneratedY;
    private float spawnY;

    private float depthScore;

    private static final float FIXED_TIMESTEP = 1f / 60f;
    private float physicsAccumulator;

    public GameplayScreen(PlayerProgress playerProgress, ScreenShake screenShake,
                           ParticleSystem particleSystem, AdProvider adProvider) {
        this.playerProgress = playerProgress;
        this.screenShake = screenShake;
        this.particleSystem = particleSystem;
        this.adProvider = adProvider;

        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        updateFontScale();

        // Tiers already earned in prior sessions shouldn't re-announce on
        // every app open -- only tiers crossed from here on are new news.
        highestAnnouncedTier = SkinTier.forDepth(playerProgress.getBestDepth());

        useTilt = playerProgress.getPreferTilt();
        inputProvider = useTilt ? new TiltInputProvider() : new TapInputProvider();

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
        lastComboChain = 0;
        comboPulseTimer = 0f;
        toastTimer = 0f;
        if (particleSystem != null) {
            particleSystem.clear();
        }

        spawnY = 0f;
        ballBody = createBall(WORLD_WIDTH / 2f, spawnY + 1.5f);
        powerUpManager = new PowerUpManager();
        powerUpManager.register("wreckingBall", new WreckingBallPowerUp(ballBody));
        debrisManager = new DebrisManager(world);

        if (pendingRewardedWreckingBall) {
            pendingRewardedWreckingBall = false;
            powerUpManager.activate("wreckingBall");
        }

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
        // PlatformDestroyed/PowerUpCollected require Box2D world mutation,
        // which can't happen from inside the collision callback that
        // produced the event -- both are queued here and drained after
        // world.step() completes. GapPassed/BallTouchedPlatform are fully
        // handled by ScoreManager. BallOffTop's game-over flag is handled
        // by GameOverController; this class additionally reacts to it to
        // persist a new best, since that's a one-time side effect tied to
        // the exact moment of the transition, not something either of
        // those two focused subscribers should also own.
        if (event instanceof PlatformDestroyed destroyed) {
            pendingPlatformDestructions.add(destroyed);
            screenShake.trigger(BREAK_SHAKE_DURATION, BREAK_SHAKE_MAGNITUDE);
            int particleCount = MathUtils.clamp(Math.round(destroyed.width() * 2f), 4, 14);
            particleSystem.burst(destroyed.x(), destroyed.y(), DEBRIS_COLOR, particleCount);
        } else if (event instanceof PowerUpCollected collected) {
            pendingPowerUpPickups.add(collected);
        } else if (event instanceof BallOffTop) {
            if (playerProgress.reportDepth(depthScore)) {
                toastText = "NEW BEST!";
                toastTimer = TOAST_DURATION;
            }
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
     * A power-up pickup occasionally spawns ON one of the flanking
     * platform segments (not centered in the gap) -- reaching it costs a
     * deliberate detour off the fall-through-the-gap line, which also
     * means landing on a platform and eating the combo-chain reset from
     * ScoreManager. That's the intended tradeoff: free score via the gap,
     * or a power-up via a detour that costs it. */
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
            List<float[]> availableSpans = new ArrayList<>(2);
            if (left != null) availableSpans.add(new float[]{0f, gapStart});
            if (right != null) availableSpans.add(new float[]{gapEnd, WORLD_WIDTH});
            if (!availableSpans.isEmpty()) {
                float[] span = availableSpans.get(MathUtils.random(availableSpans.size() - 1));
                float spanWidth = span[1] - span[0];
                float inset = Math.min(0.4f, spanWidth / 3f);
                float lo = span[0] + inset;
                float hi = span[1] - inset;
                float pickupX = hi > lo ? MathUtils.random(lo, hi) : (span[0] + span[1]) / 2f;
                float pickupY = rowY + PLATFORM_THICKNESS / 2f + POWERUP_RADIUS + 0.15f;
                String type = POWER_UP_TYPES[MathUtils.random(POWER_UP_TYPES.length - 1)];
                powerUp = createPowerUpPickup(pickupX, pickupY, type);
            }
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

    private Body createPowerUpPickup(float x, float y, String type) {
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
        fixture.setUserData("powerUp:" + type);
        shape.dispose();

        return body;
    }

    @Override
    public void render(float rawDelta) {
        float delta = Math.min(rawDelta, 0.25f);

        if (!gameOverController.isGameOver()) {
            handleInput(delta);
            stepPhysics(delta);
            drainPendingWorldMutations();
            powerUpManager.update(delta);
            updateCameraAndScroll(delta);
            manageRows();
            checkGameOver();
            updateProgressionAndFx(delta);
        } else if (Gdx.input.justTouched()) {
            // Gdx.input.getY() is measured from the TOP of the screen
            // (touch-coordinate convention), unlike the world's Y-up
            // OpenGL convention used everywhere else in this class.
            if (Gdx.input.getY() < CONTROL_TOGGLE_ZONE_HEIGHT) {
                toggleControlScheme();
            } else if (Gdx.input.getY() < AD_OFFER_ZONE_HEIGHT && adProvider.isRewardedAdReady()) {
                offerRewardedAd();
            } else {
                startNewRun();
            }
        }

        draw();
    }

    private void toggleControlScheme() {
        useTilt = !useTilt;
        inputProvider = useTilt ? new TiltInputProvider() : new TapInputProvider();
        playerProgress.setPreferTilt(useTilt);
    }

    /** Only reachable when adProvider.isRewardedAdReady() -- with
     * NoOpAdProvider that's never, so this is dead code in practice until a
     * real AdProvider is swapped in. onRewardEarned just sets a flag;
     * startNewRun() consumes it, since applying the power-up here would
     * grant it to the run that's already over. */
    private void offerRewardedAd() {
        adProvider.showRewardedAd(new AdProvider.AdCallback() {
            @Override
            public void onRewardEarned() {
                pendingRewardedWreckingBall = true;
            }

            @Override
            public void onAdFailedOrCancelled() {
            }
        });
    }

    /** Combo-multiplier pulse/shake/particles on a combo increase, live
     * skin-tier unlock detection, and toast/FX timers. Runs every active
     * gameplay frame -- unlike the world-mutation queue, none of this
     * touches Box2D, so there's no callback-timing constraint here. */
    private void updateProgressionAndFx(float delta) {
        int currentCombo = scoreManager.getComboChain();
        if (currentCombo > lastComboChain) {
            comboPulseTimer = COMBO_PULSE_DURATION;
            if (currentCombo >= 2) {
                screenShake.trigger(COMBO_SHAKE_DURATION, COMBO_SHAKE_MAGNITUDE);
                int particleCount = Math.min(6 + currentCombo, 16);
                particleSystem.burst(ballBody.getPosition().x, ballBody.getPosition().y, currentSkinTier().getColor(), particleCount);
            }
        }
        lastComboChain = currentCombo;

        float effectiveDepth = Math.max(playerProgress.getBestDepth(), depthScore);
        SkinTier currentTier = SkinTier.forDepth(effectiveDepth);
        if (currentTier.ordinal() > highestAnnouncedTier.ordinal()) {
            highestAnnouncedTier = currentTier;
            toastText = "UNLOCKED: " + currentTier.getDisplayName().toUpperCase();
            toastTimer = TOAST_DURATION;
        }

        screenShake.update(delta);
        particleSystem.update(delta);
        if (comboPulseTimer > 0f) {
            comboPulseTimer = Math.max(0f, comboPulseTimer - delta);
        }
        if (toastTimer > 0f) {
            toastTimer = Math.max(0f, toastTimer - delta);
        }
    }

    private SkinTier currentSkinTier() {
        return SkinTier.forDepth(Math.max(playerProgress.getBestDepth(), depthScore));
    }

    private void handleInput(float delta) {
        // steer is in [-1, 1]. Tap produces only -1/0/1 (binary, same feel
        // as Approach A); tilt produces a continuous value based on how far
        // past its dead zone the device is tilted -- magnitude scales the
        // applied force, giving tilt genuine analog control tap never had.
        float steer = inputProvider.getSteerValue();
        if (Math.abs(steer) > 0.001f) {
            float direction = Math.signum(steer);
            float magnitude = Math.abs(steer);

            float vx = ballBody.getLinearVelocity().x;
            if (Math.signum(vx) != direction || Math.abs(vx) < MAX_HORIZONTAL_SPEED) {
                ballBody.applyForceToCenter(direction * STEER_FORCE * magnitude, 0f, true);
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
            debrisManager.spawnDebris(destroyed.x(), destroyed.y(), destroyed.width());
        }
        pendingPlatformDestructions.clear();

        for (PowerUpCollected collected : pendingPowerUpPickups) {
            removeBodyFromRows(collected.body());
            world.destroyBody(collected.body());
            powerUpManager.activate(collected.type());
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
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Needed for particle fade-out and the ball highlight's alpha --
        // solid shapes elsewhere use alpha=1 so this doesn't change how
        // they look.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        drawBackground();

        // Screen shake is applied only to this projection matrix, never to
        // camera.position itself -- game logic (scroll, wall-tracking,
        // catch-up) reads camera.position and must never see the jitter.
        Matrix4 shakenProjection = camera.combined.cpy().translate(screenShake.getOffset().x, screenShake.getOffset().y, 0f);
        shapeRenderer.setProjectionMatrix(shakenProjection);
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

        drawParticles();

        boolean gameOver = gameOverController.isGameOver();
        Color ballColor = gameOver ? Color.RED : (powerUpManager.isActive() ? WRECKING_BALL_COLOR : currentSkinTier().getColor());
        shapeRenderer.setColor(ballColor);
        shapeRenderer.circle(ballBody.getPosition().x, ballBody.getPosition().y, BALL_RADIUS, 24);
        drawBallHighlight(ballColor);

        shapeRenderer.end();

        batch.setProjectionMatrix(hudCamera.combined);
        batch.begin();
        font.setColor(Color.WHITE);
        StringBuilder label = new StringBuilder("Depth: ").append((int) depthScore).append("m");
        if (powerUpManager.isActive()) {
            label.append(String.format("\n%s: %.1fs", displayName(powerUpManager.getActiveType()), powerUpManager.getRemaining()));
        }
        label.append(String.format("\nBest: %dm   Streak: %dd   Controls: %s", (int) playerProgress.getBestDepth(), playerProgress.getStreak(), useTilt ? "TILT" : "TAP"));
        if (gameOver) {
            label.append("\nGAME OVER -- tap to retry");
        }
        font.draw(batch, label.toString(), 20f, Gdx.graphics.getHeight() - 20f);

        drawComboMultiplier();
        drawToast();
        if (gameOver) {
            drawControlToggleHint();
            if (adProvider.isRewardedAdReady()) {
                drawRewardedAdHint();
            }
        }

        batch.end();
    }

    private void drawControlToggleHint() {
        String hint = "Tap here to switch to " + (useTilt ? "TAP" : "TILT");
        font.draw(batch, hint, 0f, Gdx.graphics.getHeight() - 10f, Gdx.graphics.getWidth(), com.badlogic.gdx.utils.Align.center, false);
    }

    private void drawRewardedAdHint() {
        String hint = "Tap here for a free Wrecking Ball (watch ad)";
        float y = Gdx.graphics.getHeight() - (CONTROL_TOGGLE_ZONE_HEIGHT + AD_OFFER_ZONE_HEIGHT) / 2f;
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
    private void drawBallHighlight(Color baseColor) {
        Color highlight = baseColor.cpy().lerp(Color.WHITE, 0.75f);
        highlight.a = 0.85f;
        shapeRenderer.setColor(highlight);

        float distance = BALL_RADIUS * 0.5f;
        float angle = ballBody.getAngle();
        // Rotate the local point (0, distance) -- the ball's "north pole"
        // in its own unrotated frame -- by its current angle, so the dot
        // sweeps around the ball's surface as it spins.
        float worldOffsetX = -distance * MathUtils.sin(angle);
        float worldOffsetY = distance * MathUtils.cos(angle);
        float hx = ballBody.getPosition().x + worldOffsetX;
        float hy = ballBody.getPosition().y + worldOffsetY;
        shapeRenderer.circle(hx, hy, BALL_RADIUS * 0.16f, 10);
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
    private void drawComboMultiplier() {
        int combo = scoreManager.getComboChain();
        if (combo < 2) return;

        float baseScale = font.getData().scaleX;
        float restingBoost = 1.7f;
        float pulseEnvelope = comboPulseTimer / COMBO_PULSE_DURATION; // 1 right after an increment -> 0
        font.getData().setScale(baseScale * (restingBoost + 0.7f * pulseEnvelope));
        font.setColor(COMBO_TEXT_COLOR);

        float rightMargin = 20f;
        float boxWidth = Gdx.graphics.getWidth() / 2f - rightMargin;
        font.draw(batch, "x" + combo, Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() - 20f, boxWidth, com.badlogic.gdx.utils.Align.right, false);

        font.setColor(Color.WHITE);
        font.getData().setScale(baseScale);
    }

    private void drawToast() {
        if (toastTimer <= 0f || toastText == null) return;
        float alpha = Math.min(1f, toastTimer / 0.5f); // quick fade in the final half-second
        font.setColor(1f, 1f, 1f, alpha);
        font.draw(batch, toastText, 0f, Gdx.graphics.getHeight() * 0.7f, Gdx.graphics.getWidth(), com.badlogic.gdx.utils.Align.center, false);
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
        shapeRenderer.rect(x - hx, y - PLATFORM_THICKNESS / 2f, hx * 2f, PLATFORM_THICKNESS);

        // Thin lighter strip along the top edge -- cheap "lit from above" cue.
        Color highlight = baseColor.cpy().lerp(Color.WHITE, 0.35f);
        shapeRenderer.setColor(highlight);
        float highlightThickness = PLATFORM_THICKNESS * 0.2f;
        shapeRenderer.rect(x - hx, y + PLATFORM_THICKNESS / 2f - highlightThickness, hx * 2f, highlightThickness);
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
