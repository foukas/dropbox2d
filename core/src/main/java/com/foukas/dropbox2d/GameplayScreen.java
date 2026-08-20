package com.foukas.dropbox2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.foukas.dropbox2d.events.BallOffTop;
import com.foukas.dropbox2d.events.ContactDispatcher;
import com.foukas.dropbox2d.events.GameEvent;
import com.foukas.dropbox2d.events.GameEventBus;
import com.foukas.dropbox2d.events.GameEventListener;
import com.foukas.dropbox2d.events.GapPassed;
import com.foukas.dropbox2d.events.PlatformDestroyed;
import com.foukas.dropbox2d.events.PowerUpCollected;
import com.foukas.dropbox2d.fx.MotionTrail;
import com.foukas.dropbox2d.fx.ParticleSystem;
import com.foukas.dropbox2d.fx.ScreenShake;
import com.foukas.dropbox2d.generation.GapReachabilityValidator;
import com.foukas.dropbox2d.generation.MovingPlatformReachability;
import com.foukas.dropbox2d.input.InputProvider;
import com.foukas.dropbox2d.input.TapInputProvider;
import com.foukas.dropbox2d.input.TiltInputProvider;
import com.foukas.dropbox2d.generation.PlatformType;
import com.foukas.dropbox2d.monetization.AdProvider;
import com.foukas.dropbox2d.platform.SafeAreaInsets;
import com.foukas.dropbox2d.physics.DebrisManager;
import com.foukas.dropbox2d.physics.MovingPlatformManager;
import com.foukas.dropbox2d.physics.PhysicsNaNGuard;
import com.foukas.dropbox2d.physics.VelocityClamp;
import com.foukas.dropbox2d.powerups.PowerUpManager;
import com.foukas.dropbox2d.powerups.RampagePowerUp;
import com.foukas.dropbox2d.powerups.WreckingBallPowerUp;
import com.foukas.dropbox2d.progression.Biome;
import com.foukas.dropbox2d.progression.PlayerProgress;
import com.foukas.dropbox2d.progression.SkinTier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    // Package-private: also read by GameplayRenderer to size the scrolling
    // grid-horizon background (Next Step 8) to the visible world range.
    // With ExtendViewport (Next Step 12), these are MINIMUMS, not the exact
    // visible size -- on any screen taller than 9:16 (most current phones),
    // the actual visible height extends beyond WORLD_HEIGHT so the play area
    // fills the screen instead of letterboxing top/bottom. Code that needs
    // the real current visible height should read viewport.getWorldHeight()
    // (see manageRows()/checkGameOver()/updateCameraAndScroll()), not this
    // constant, for exactly that reason.
    static final float WORLD_WIDTH = 9f;
    static final float WORLD_HEIGHT = 16f;
    private static final float GRAVITY_Y = -25f;

    // Package-private: also read by GameplayRenderer, which sizes drawn
    // shapes to match the physics bodies created here.
    static final float BALL_RADIUS = 0.4f;
    private static final float BALL_DENSITY = 1f;
    private static final float BALL_FRICTION = 0.4f;
    private static final float BALL_RESTITUTION = 0.55f;
    private static final float BALL_LINEAR_DAMPING = 0.5f;

    private static final float STEER_FORCE = 55f;
    private static final float MAX_HORIZONTAL_SPEED = 6f;

    private static final float ROW_SPACING = 2.6f;
    // Package-private: also read by GameplayRenderer.
    static final float PLATFORM_THICKNESS = 0.35f;
    private static final float GAP_MIN_WIDTH = 2.4f;
    private static final float GAP_MAX_WIDTH = 3.4f;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    // Moving-platform tuning (moving-platforms design doc, plan-eng-review
    // 2026-08-06) -- placeholders, expected to be re-tuned by feel like
    // GAP_MIN_WIDTH/WEAK_PLATFORM_CHANCE's historical siblings. Comfortably
    // below GAP_MIN_WIDTH / 2 = 1.2f by construction, enforced by
    // MovingPlatformReachabilityTest.realPatrolAmplitudeStaysComfortablyUnderHalfGapMinWidth
    // rather than a runtime clamp. MIN_FILLER_WIDTH + MOVING_PLATFORM_WIDTH +
    // MOVING_PLATFORM_AMPLITUDE (1.6f) exceeds the flanking span's
    // guaranteed 0.5f-unit minimum -- spawnNextRow()'s retry loop rejects
    // and rerolls in that case via MovingPlatformReachability.fitsSplitBodyGeometry(),
    // it never clamps or degrades the geometry silently.
    private static final float MOVING_PLATFORM_AMPLITUDE = 0.5f;
    private static final float MOVING_PLATFORM_WIDTH = 0.8f;
    private static final float MIN_FILLER_WIDTH = 0.3f;

    private static final float POWERUP_SPAWN_CHANCE = 0.15f;
    // Package-private: also read by GameplayRenderer.
    static final float POWERUP_RADIUS = 0.25f;
    // Registered power-up types (must match PowerUpManager.register keys in
    // resetForNewRun). Adding a type is adding it here plus a register()
    // call -- pickup placement/rendering stay type-agnostic (rampage's own
    // edge-placement rule, added in a later step, is the one exception).
    private static final String[] POWER_UP_TYPES = {"wreckingBall", "rampage"};

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

    // Package-private: also read by GameplayRenderer -- used both for the
    // particle burst color when a platform breaks (here, in onEvent) and
    // for the debris body color when rendering (GameplayRenderer.draw()).
    // Burnt-orange (Next Step 7's synthwave palette), a darker relative of
    // WEAK_PLATFORM_COLOR so debris visually reads as pieces of it.
    static final Color DEBRIS_COLOR = new Color(0.7098f, 0.2706f, 0.1216f, 1f); // #b5451f

    // B2/B4 juice tuning
    // Package-private: also read by GameplayRenderer's combo-pulse envelope.
    static final float COMBO_PULSE_DURATION = 0.3f;
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
    // Package-private: the tap-zone check lives here (input-handling), but
    // GameplayRenderer also reads it to draw the matching hint text.
    static final float CONTROL_TOGGLE_ZONE_HEIGHT = 100f;

    // Monetization plumbing: a second game-over tap zone, directly below the
    // control-toggle band, offering a free wrecking-ball power-up on the next
    // run in exchange for a rewarded ad. Gated behind adProvider.isRewardedAdReady()
    // for both the tap handler and the hint text, so with NoOpAdProvider (the
    // only implementation that exists today) this zone is entirely inert and
    // invisible -- see AdProvider's class doc for what it takes to go live.
    // Package-private: see CONTROL_TOGGLE_ZONE_HEIGHT above.
    static final float AD_OFFER_ZONE_HEIGHT = 200f;

    // Pause overlay's quit-to-menu tap zone (bottom strip, screen pixels
    // from the bottom) -- mirrors GameOverScreen's back-to-menu zone sizing.
    private static final float PAUSE_QUIT_ZONE_HEIGHT = 100f;

    // ----- App-lifetime objects, owned by DropGame, constructor-injected -----
    private final Game game;
    private final PlayerProgress playerProgress;
    private final ScreenShake screenShake;
    private final ParticleSystem particleSystem;
    private final AdProvider adProvider;
    // Set once via setGameOverScreen(), after both this and GameOverScreen
    // are constructed -- circular reference (GameOverScreen also needs this
    // instance, for retry), so DropGame wires it in a second phase.
    private GameOverScreen gameOverScreen;
    // Set once via setMainMenuScreen(), same reason -- needed for the pause
    // overlay's quit-to-menu action.
    private MainMenuScreen mainMenuScreen;
    private boolean paused;

    // ----- Runtime state -----
    private World world;
    private Body ballBody;
    private Body leftWall;
    private Body rightWall;
    private OrthographicCamera camera;
    private Viewport viewport;
    private final GameplayRenderer renderer;

    private GameEventBus eventBus;
    private ScoreManager scoreManager;
    private GameOverController gameOverController;
    private DebrisManager debrisManager;
    private MovingPlatformManager movingPlatformManager;
    private PowerUpManager powerUpManager;
    // Kept as its own field (not just registered with powerUpManager) so
    // GameplayScreen can call recordSmash()/read the smash counter and
    // run bonus directly, without a lookup through the registry each time.
    private RampagePowerUp rampagePowerUp;

    private SkinTier highestAnnouncedTier;
    private int lastComboChain;
    private String toastText;
    private float toastTimer;
    private float comboPulseTimer;

    // Package-private: also read by GameplayRenderer for the trail draw.
    static final int TRAIL_MAX_LENGTH = 12;
    private final Deque<MotionTrail.Point> trailPositions = MotionTrail.newBuffer();

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

    public GameplayScreen(Game game, PlayerProgress playerProgress, ScreenShake screenShake,
                           ParticleSystem particleSystem, AdProvider adProvider, SafeAreaInsets safeAreaInsets) {
        this.game = game;
        this.playerProgress = playerProgress;
        this.screenShake = screenShake;
        this.particleSystem = particleSystem;
        this.adProvider = adProvider;

        camera = new OrthographicCamera();
        // ExtendViewport, not FitViewport -- device verification (Next Step
        // 12) found FitViewport letterboxing top/bottom on screens taller
        // than 9:16 (i.e. most current phones), leaving visible background
        // strips above/below actual gameplay. ExtendViewport keeps
        // WORLD_WIDTH fixed (already matches full screen width, since 9 is
        // the constraining dimension here) and extends world height to fill
        // the rest of the screen instead -- showing more of the level
        // vertically on taller screens rather than shrinking/bordering it,
        // which suits an endless vertical faller better anyway.
        viewport = new ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        renderer = new GameplayRenderer(screenShake, particleSystem, playerProgress, safeAreaInsets);

        // Tiers already earned in prior sessions shouldn't re-announce on
        // every app open -- only tiers crossed from here on are new news.
        highestAnnouncedTier = SkinTier.forDepth(playerProgress.getBestDepth());

        useTilt = playerProgress.getPreferTilt();
        inputProvider = useTilt ? new TiltInputProvider() : new TapInputProvider();

        // No resetForNewRun() call here (plan-eng-review Next Step 5): both
        // real entry points into gameplay -- MainMenuScreen's "tap to
        // start" and GameOverScreen's retry -- now call it explicitly
        // before switching to this screen, so an initial call here would
        // just build a Box2D world that gets discarded unseen.
    }

    // Called from DropGame.create(), after both screens exist.
    void setGameOverScreen(GameOverScreen gameOverScreen) {
        this.gameOverScreen = gameOverScreen;
    }

    void setMainMenuScreen(MainMenuScreen mainMenuScreen) {
        this.mainMenuScreen = mainMenuScreen;
    }

    /** Resets all per-run state -- Box2D world/rows/depthScore (as before),
     * plus ScreenShake/ParticleSystem (plan-eng-review outside-voice
     * finding: these are per-run state, same category as MotionTrail's
     * buffer, not app-lifetime state like PlayerProgress/AdProvider).
     * Without this, a death-moment shake or lingering particles could
     * carry into the next run since GameplayScreen -- and the objects it's
     * constructor-injected with -- are now reused across retries rather
     * than reconstructed (see DropGame's class doc). Package-private: also
     * called by MainMenuScreen and GameOverScreen before switching to this
     * screen, since each of those is a fresh-run entry point. */
    void resetForNewRun() {
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
        // Lambda captures the powerUpManager FIELD, not a local value --
        // safe even though powerUpManager isn't assigned until later in
        // this same method, since preSolve() (the only caller) never runs
        // until stepPhysics() executes on a later frame, well after this
        // method returns (rampage design doc, plan-eng-review 2026-08-06).
        world.setContactListener(new ContactDispatcher(eventBus, () -> "rampage".equals(powerUpManager.getActiveType())));

        rows.clear();
        pendingScoreRows.clear();
        pendingPlatformDestructions.clear();
        pendingPowerUpPickups.clear();
        depthScore = 0f;
        physicsAccumulator = 0f;
        lastComboChain = 0;
        comboPulseTimer = 0f;
        toastTimer = 0f;
        particleSystem.clear();
        screenShake.stop();
        paused = false;
        trailPositions.clear();

        spawnY = 0f;
        ballBody = createBall(WORLD_WIDTH / 2f, spawnY + 1.5f);
        powerUpManager = new PowerUpManager();
        powerUpManager.register("wreckingBall", new WreckingBallPowerUp(ballBody));
        rampagePowerUp = new RampagePowerUp(ballBody);
        powerUpManager.register("rampage", rampagePowerUp);
        debrisManager = new DebrisManager(world);
        movingPlatformManager = new MovingPlatformManager();

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
            // preSolve fires every physics substep the ball remains in
            // contact with a still-existing weak platform -- setEnabled(false)
            // only suppresses collision response for that one step, not
            // future preSolve calls, so the same body can dispatch
            // PlatformDestroyed more than once within a single render frame
            // when stepPhysics() runs multiple substeps (more likely the
            // more platforms are breaking at once). Without this guard,
            // drainPendingWorldMutations() would call world.destroyBody()
            // twice on the same native body -- undefined behavior, observed
            // as a freeze rather than a clean exception.
            if (!isAlreadyQueuedForDestruction(destroyed.body())) {
                pendingPlatformDestructions.add(destroyed);
                float escalation = rampageEscalationScale(destroyed.body());
                screenShake.trigger(BREAK_SHAKE_DURATION, BREAK_SHAKE_MAGNITUDE * escalation);
                int particleCount = MathUtils.clamp(Math.round(destroyed.width() * 2f * escalation), 4, 24);
                particleSystem.burst(destroyed.x(), destroyed.y(), DEBRIS_COLOR, particleCount);
            }
        } else if (event instanceof PowerUpCollected collected) {
            pendingPowerUpPickups.add(collected);
        } else if (event instanceof BallOffTop) {
            if (playerProgress.reportDepth(depthScore)) {
                toastText = "NEW BEST!";
                toastTimer = TOAST_DURATION;
            }
        }
    }

    /** Rampage-triggered breaks (platform/movingPlatform tags -- only ever
     * break-eligible while rampage is active) escalate FX intensity with
     * the current smash streak; weakPlatform breaks (always eligible,
     * unrelated to rampage) are unaffected (scale 1). Read here, in
     * onEvent(), BEFORE drainPendingWorldMutations() increments the
     * counter for this exact break -- "+1" approximates "this is about to
     * become smash N." Not exact for a MOVING pair's second half (counted
     * once per pair, not per body -- see recordSmashIfRampageTriggered()),
     * but this is a feel effect, not a scoring integrity concern, so that
     * approximation is fine. Capped at 3x so a long streak doesn't produce
     * an absurd shake/particle count. */
    private float rampageEscalationScale(Body body) {
        Object tag = body.getFixtureList().get(0).getUserData();
        if (!("platform".equals(tag) || "movingPlatform".equals(tag))) {
            return 1f;
        }
        int upcomingCount = rampagePowerUp.getSmashCount() + 1;
        return Math.min(1f + upcomingCount * 0.15f, 3f);
    }

    private boolean isAlreadyQueuedForDestruction(Body body) {
        for (PlatformDestroyed queued : pendingPlatformDestructions) {
            if (queued.body() == body) {
                return true;
            }
        }
        return false;
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

        // Resolved from this ROW's own eventual depth (spawnY - rowY), not
        // depthScore (the ball's current depth) -- rows are generated
        // ~15.6 world units ahead of the ball (see manageRows()'s lookahead
        // below), so a row generated just before a biome boundary must
        // still roll against the biome it will actually land in, not
        // whatever biome the ball happens to be in right now (plan-eng-review
        // outside voice finding, relocated here from the reachability check
        // that turned out not to need it).
        Biome biome = Biome.biomeFor(spawnY - rowY);

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

        // MAX_HORIZONTAL_SPEED and timeToFall below are NOT adjusted for
        // the row's biome, and don't need to be: MAX_HORIZONTAL_SPEED is a
        // hard per-frame clamp the ball can't exceed regardless of damping
        // (equilibrium speed under any biome's damping stays far above the
        // clamp for any plausible tuning value), and higher damping only
        // ever slows a real fall relative to this undamped timeToFall
        // formula -- giving the ball more real time than credited, never
        // less. Both stay safe, conservative inputs for every biome as
        // long as damping only increases from baseline, which BiomeTest
        // enforces globally (see Biome's class doc) -- no per-biome
        // recomputation needed here.
        // Type (and therefore amplitude) must be decided BEFORE the retry
        // loop below, not after as WEAK/NORMAL selection used to be
        // (plan-eng-review moving-platforms step 4) -- amplitude now feeds
        // into the reachability check as an input, so it can no longer be
        // decided once a gap is already accepted. MOVING/WEAK stay
        // mutually exclusive for this slice: rollPlatformType() rolls
        // movingPlatformChance first, else weakPlatformChance, else
        // NORMAL, keeping WEAK's own distribution unchanged whenever a
        // side isn't MOVING.
        PlatformType leftType = rollPlatformType(biome);
        PlatformType rightType = rollPlatformType(biome);
        float leftAmplitude = leftType == PlatformType.MOVING ? MOVING_PLATFORM_AMPLITUDE : 0f;
        float rightAmplitude = rightType == PlatformType.MOVING ? MOVING_PLATFORM_AMPLITUDE : 0f;

        float gapWidth = 0f;
        float gapStart = 0f;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            gapWidth = MathUtils.random(GAP_MIN_WIDTH, GAP_MAX_WIDTH);
            gapStart = MathUtils.random(0.5f, WORLD_WIDTH - gapWidth - 0.5f);
            float attemptGapEnd = gapStart + gapWidth;

            // Validate against the narrowest gap either side's patrol
            // amplitude could ever produce (0 for a non-MOVING side --
            // identical to today's un-shrunk check in that case), plus a
            // check that a MOVING side's eventual flanking span can
            // actually fit the split-body geometry it will need (moving-
            // platforms step 6) -- reject and reroll on either failure,
            // never clamp or build broken geometry.
            MovingPlatformReachability.AdjustedGap adjusted =
                    MovingPlatformReachability.shrinkForAmplitude(gapStart, gapWidth, leftAmplitude, rightAmplitude);
            boolean reachable = GapReachabilityValidator.isReachable(
                    adjusted.gapStart(), adjusted.gapWidth(), WORLD_WIDTH, MAX_HORIZONTAL_SPEED, timeToFall);
            boolean leftFits = leftType != PlatformType.MOVING
                    || MovingPlatformReachability.fitsSplitBodyGeometry(gapStart, MOVING_PLATFORM_WIDTH, MOVING_PLATFORM_AMPLITUDE, MIN_FILLER_WIDTH);
            boolean rightFits = rightType != PlatformType.MOVING
                    || MovingPlatformReachability.fitsSplitBodyGeometry(WORLD_WIDTH - attemptGapEnd, MOVING_PLATFORM_WIDTH, MOVING_PLATFORM_AMPLITUDE, MIN_FILLER_WIDTH);

            if (reachable && leftFits && rightFits) {
                break;
            }
        }
        float gapEnd = gapStart + gapWidth;

        // A MOVING side is split into a static filler + a separate small
        // kinematic piece (moving-platforms step 6) -- never the whole
        // flanking span made kinematic (see the design doc's Constraints
        // for why that geometry would open an unvalidated wall-side hole).
        // leftAvailableSpanEnd/rightAvailableSpanStart narrow the power-up
        // placement span to the filler's footprint only on a MOVING side
        // (Next Step 7) -- unchanged (gapStart/gapEnd) for NORMAL/WEAK.
        Body left = null;
        Body leftKinematic = null;
        float leftAvailableSpanEnd = gapStart;
        if (gapStart > 0.1f) {
            if (leftType == PlatformType.MOVING) {
                MovingSegment segment = createMovingPlatformSegment(0f, gapStart, rowY, MOVING_PLATFORM_AMPLITUDE);
                left = segment.filler();
                leftKinematic = segment.kinematic();
                leftAvailableSpanEnd = segment.fillerEdge();
            } else {
                left = createPlatformSegment(0f, gapStart, rowY, leftType);
            }
        }
        Body right = null;
        Body rightKinematic = null;
        float rightAvailableSpanStart = gapEnd;
        if (WORLD_WIDTH - gapEnd > 0.1f) {
            if (rightType == PlatformType.MOVING) {
                MovingSegment segment = createMovingPlatformSegment(WORLD_WIDTH, gapEnd, rowY, MOVING_PLATFORM_AMPLITUDE);
                right = segment.filler();
                rightKinematic = segment.kinematic();
                rightAvailableSpanStart = segment.fillerEdge();
            } else {
                right = createPlatformSegment(gapEnd, WORLD_WIDTH, rowY, rightType);
            }
        }

        Body powerUp = null;
        if (MathUtils.random() < POWERUP_SPAWN_CHANCE) {
            List<float[]> availableSpans = new ArrayList<>(2);
            if (left != null) availableSpans.add(new float[]{0f, leftAvailableSpanEnd});
            if (right != null) availableSpans.add(new float[]{rightAvailableSpanStart, WORLD_WIDTH});
            if (!availableSpans.isEmpty()) {
                float[] span = availableSpans.get(MathUtils.random(availableSpans.size() - 1));
                // Type rolled BEFORE position (rampage design doc, plan-eng-
                // review 2026-08-06): rampage's placement rule depends on
                // which type this pickup is, unlike before where position
                // was type-agnostic. (Rampage's own placement rule was
                // reverted after playtest -- see pickupXForSpan's doc -- so
                // placement is type-agnostic again today, but the ordering
                // is left as-is since a future type-specific rule is likely.)
                String type = POWER_UP_TYPES[MathUtils.random(POWER_UP_TYPES.length - 1)];
                float pickupX = pickupXForSpan(span);
                float pickupY = rowY + PLATFORM_THICKNESS / 2f + POWERUP_RADIUS + 0.15f;
                powerUp = createPowerUpPickup(pickupX, pickupY, type);
            }
        }

        PlatformRow row = new PlatformRow(rowY, left, right, powerUp);
        row.leftKinematic = leftKinematic;
        row.rightKinematic = rightKinematic;
        rows.add(row);
        pendingScoreRows.addLast(row);
    }

    /** Splits a MOVING side's flanking segment into a static filler and a
     * separate, small kinematic piece patrolling near the gap (moving-
     * platforms step 6) -- see the design doc's corrected seam-invariant
     * note for the geometry this implements. wallX is the wall-side
     * boundary of the whole flanking span (0f for the left side,
     * WORLD_WIDTH for the right); gapEdgeX is the nominal, non-patrolled
     * gap-facing edge (gapStart for the left side, gapEnd for the right) --
     * the same value spawnNextRow()'s reachability-retry loop already
     * validated via MovingPlatformReachability. The formula below is
     * direction-agnostic: sign flips it for whichever side is being built,
     * so the left and right call sites don't need separate math. */
    private MovingSegment createMovingPlatformSegment(float wallX, float gapEdgeX, float y, float amplitude) {
        boolean gapIsToTheRight = gapEdgeX > wallX;
        float sign = gapIsToTheRight ? 1f : -1f;

        // The kinematic piece's gap-facing edge patrols between gapEdgeX
        // (rest) and gapEdgeX + sign*amplitude (full extension into the
        // gap) -- exactly the narrowed edge MovingPlatformReachability
        // .shrinkForAmplitude() already validated above. Its rest
        // (wall-ward) position overlaps the filler by amplitude; the
        // filler's edge is fixed at the kinematic piece's full-extension
        // wall-side edge, so the two footprints only ever overlap, never
        // separate into a gap.
        float restWallSideEdge = gapEdgeX - sign * MOVING_PLATFORM_WIDTH;
        float fillerEdge = restWallSideEdge + sign * amplitude;

        Body filler = createPlatformSegment(
                gapIsToTheRight ? wallX : fillerEdge,
                gapIsToTheRight ? fillerEdge : wallX,
                y, PlatformType.NORMAL);

        float restCenterX = restWallSideEdge + sign * MOVING_PLATFORM_WIDTH / 2f;
        float fullExtensionCenterX = restCenterX + sign * amplitude;
        float minX = Math.min(restCenterX, fullExtensionCenterX);
        float maxX = Math.max(restCenterX, fullExtensionCenterX);

        Body kinematic = createKinematicPlatformSegment(restCenterX, y);
        movingPlatformManager.track(kinematic, minX, maxX);

        return new MovingSegment(filler, kinematic, fillerEdge);
    }

    private Body createKinematicPlatformSegment(float centerX, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.KinematicBody;
        bodyDef.position.set(centerX, y);
        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(MOVING_PLATFORM_WIDTH / 2f, PLATFORM_THICKNESS / 2f);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.friction = 0.6f;
        fixtureDef.restitution = 0f;
        Fixture fixture = body.createFixture(fixtureDef);
        fixture.setUserData("movingPlatform");
        shape.dispose();

        return body;
    }

    private record MovingSegment(Body filler, Body kinematic, float fillerEdge) {
    }

    /** Extracted (plan-eng-review Code Quality finding, moving-platforms
     * step 3) so the left/right blocks in spawnNextRow() don't duplicate
     * the roll logic. MOVING/WEAK are mutually exclusive for this slice
     * (Approach C, the combo, is deferred -- see TODOS.md): rolls
     * movingPlatformChance first, else weakPlatformChance as before, else
     * NORMAL -- keeps WEAK's own distribution unchanged whenever a side
     * isn't MOVING, since the two rolls consume independent draws from
     * the unseeded random stream (moving-platforms step 4). */
    private PlatformType rollPlatformType(Biome biome) {
        if (MathUtils.random() < biome.getMovingPlatformChance()) {
            return PlatformType.MOVING;
        }
        return MathUtils.random() < biome.getWeakPlatformChance() ? PlatformType.WEAK : PlatformType.NORMAL;
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

    /** Reverted rampage's edge-placement rule after device playtest (user
     * feedback, rampage follow-up 2026-08-06): near-the-gap is the SAFE
     * zone in this game (that's where you want to fall), not a risky one,
     * so edge placement never actually delivered the intended risk/reward
     * -- every power-up type uses this same inset-toward-center placement
     * now, unchanged from before rampage existed. */
    private float pickupXForSpan(float[] span) {
        float spanWidth = span[1] - span[0];
        float inset = Math.min(0.4f, spanWidth / 3f);
        float lo = span[0] + inset;
        float hi = span[1] - inset;
        return hi > lo ? MathUtils.random(lo, hi) : (span[0] + span[1]) / 2f;
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
        // Pause trigger is BACK (Android)/ESCAPE (desktop) only -- not an
        // on-screen tap zone, since the whole play area is already a
        // tap-to-steer surface (resolved in the design doc's Open
        // Questions). Toggling here, before the paused/unpaused branches
        // below, means the very frame pause is requested already reflects
        // the new state.
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            paused = !paused;
        }

        if (paused) {
            if (Gdx.input.justTouched()) {
                if (Gdx.input.getY() > Gdx.graphics.getHeight() - PAUSE_QUIT_ZONE_HEIGHT) {
                    paused = false;
                    game.setScreen(mainMenuScreen);
                    return;
                }
                paused = false;
            }
            renderer.draw(this);
            return;
        }

        float delta = Math.min(rawDelta, 0.25f);
        handleInput(delta);
        stepPhysics(delta);
        drainPendingWorldMutations();
        powerUpManager.update(delta);
        updateCameraAndScroll(delta);
        manageRows();
        checkGameOver();
        updateProgressionAndFx(delta);

        renderer.draw(this);

        // GameOverScreen takes over entirely from here (plan-eng-review
        // Next Step 5) -- this screen won't render() again until
        // resetForNewRun() + game.setScreen(this) brings it back for a new
        // run, so there's no need to freeze update logic on a repeated
        // "already game over" frame like the pre-Screen implementation did.
        if (gameOverController.isGameOver()) {
            game.setScreen(gameOverScreen);
        }
    }

    boolean isPaused() {
        return paused;
    }

    // Package-private: called by GameOverScreen's control-toggle tap zone.
    void toggleControlScheme() {
        useTilt = !useTilt;
        inputProvider = useTilt ? new TiltInputProvider() : new TapInputProvider();
        playerProgress.setPreferTilt(useTilt);
    }

    /** Only reachable when adProvider.isRewardedAdReady() -- with
     * NoOpAdProvider that's never, so this is dead code in practice until a
     * real AdProvider is swapped in. onRewardEarned just sets a flag;
     * resetForNewRun() consumes it, since applying the power-up here would
     * grant it to the run that's already over. Package-private: called by
     * GameOverScreen's ad-offer tap zone. */
    void offerRewardedAd() {
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

    // Package-private: also called by GameplayRenderer for ball color.
    SkinTier currentSkinTier() {
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
            // VelocityClamp runs after PhysicsNaNGuard (rampage design doc,
            // plan-eng-review 2026-08-06): a genuinely NaN/Infinite velocity
            // should be corrected before magnitude logic runs on it. Same
            // per-substep call site and reasoning as PhysicsNaNGuard --
            // caps an anomalously large bounce (e.g. rampage's timer
            // expiring mid-fall through a platform stack) without waiting
            // for a render frame boundary. Built and wired ahead of the
            // rampage mechanism itself (ContactDispatcher's break-
            // eligibility extension) precisely so that mechanism never
            // exists without its failsafe already in place.
            VelocityClamp.checkAndClamp(ballBody);
            // Per-substep, not once per render frame (moving-platforms
            // step 9, plan-eng-review outside-voice finding): render()
            // can wrap several substeps depending on frame timing, so a
            // once-per-frame check would let a kinematic body overshoot
            // its patrol amplitude on a stutter before the next check --
            // see MovingPlatformManager's class doc, same reasoning as
            // PhysicsNaNGuard.checkAndFix() being called from here too.
            movingPlatformManager.checkBounds();
        }
    }

    /** Box2D forbids creating/destroying bodies from inside a collision
     * callback -- ContactDispatcher only ever dispatches events during
     * those callbacks, so the actual world mutation happens here, right
     * after world.step() returns and the world is unlocked. */
    private void drainPendingWorldMutations() {
        // Index-based, not for-each: dispatching a synthetic PlatformDestroyed
        // for a MOVING side's sibling body (below) re-enters onEvent(), which
        // appends to this same list -- a for-each would throw
        // ConcurrentModificationException. originalCount marks the boundary
        // between genuinely ball-triggered entries (queued before this method
        // started) and synthetic sibling entries this method itself appends,
        // so recordSmashIfRampageTriggered() only counts each split-body pair
        // once (rampage design doc, plan-eng-review 2026-08-06 -- "a single
        // PlatformDestroyed-equivalent outcome for the pair, not two
        // independent break events").
        int originalCount = pendingPlatformDestructions.size();
        for (int i = 0; i < pendingPlatformDestructions.size(); i++) {
            PlatformDestroyed destroyed = pendingPlatformDestructions.get(i);
            if (i < originalCount) {
                recordSmashIfRampageTriggered(destroyed.body());
            }
            // Found BEFORE removeBodyFromRows(), which nulls out whichever
            // row field matched destroyed.body() -- looking it up after
            // would make the pairing unrecoverable.
            Body sibling = findMovingSibling(destroyed.body());
            removeBodyFromRows(destroyed.body());
            world.destroyBody(destroyed.body());
            debrisManager.spawnDebris(destroyed.x(), destroyed.y(), destroyed.width());
            if (sibling != null && !isAlreadyQueuedForDestruction(sibling)) {
                // Dispatched through the normal event path (not destroyed
                // directly here) so the sibling gets its own shake/particle
                // burst via onEvent() -- destroying it inline would silently
                // skip its FX, reading as a bug (plan-eng-review outside-
                // voice finding).
                eventBus.dispatch(new PlatformDestroyed(sibling, sibling.getPosition().x, sibling.getPosition().y, platformWidth(sibling)));
            }
        }
        pendingPlatformDestructions.clear();

        for (PowerUpCollected collected : pendingPowerUpPickups) {
            removeBodyFromRows(collected.body());
            world.destroyBody(collected.body());
            if ("wreckingBall".equals(collected.type()) && "rampage".equals(powerUpManager.getActiveType())) {
                // Picking up wrecking ball while rampage is active would be
                // a strict downgrade -- rampage already breaks everything
                // wrecking ball does, plus NORMAL/MOVING platforms too --
                // so this repurposes the pickup as a rampage timer
                // extension instead of switching power-ups (user feedback,
                // rampage follow-up, 2026-08-06).
                rampagePowerUp.extendByWreckingBallPickup();
            } else {
                powerUpManager.activate(collected.type());
            }
        }
        pendingPowerUpPickups.clear();
    }

    /** A MOVING side's filler ("platform" tag) and kinematic sliver
     * ("movingPlatform" tag) overlap to read as one continuous platform --
     * breaking either half during rampage must break both, or the
     * surviving half floats/patrols alone with a visible gap where its
     * partner used to be (rampage design doc Constraints). Returns null
     * for NORMAL/WEAK platforms (no pairing) or if body doesn't match any
     * tracked row (shouldn't happen, defensive). */
    private Body findMovingSibling(Body body) {
        for (PlatformRow row : rows) {
            if (row.left == body && row.leftKinematic != null) return row.leftKinematic;
            if (row.leftKinematic == body && row.left != null) return row.left;
            if (row.right == body && row.rightKinematic != null) return row.rightKinematic;
            if (row.rightKinematic == body && row.right != null) return row.right;
        }
        return null;
    }

    /** "platform"/"movingPlatform" tags are only ever break-eligible while
     * rampage is active (ContactDispatcher.preSolve()'s gate) -- "weakPlatform"
     * breaks regardless of rampage state, so checking the destroyed body's
     * own tag is a sufficient, self-contained signal for "this specific
     * break was rampage-triggered" without PlatformDestroyed needing an
     * explicit flag. */
    private void recordSmashIfRampageTriggered(Body body) {
        Object tag = body.getFixtureList().get(0).getUserData();
        if ("platform".equals(tag) || "movingPlatform".equals(tag)) {
            rampagePowerUp.recordSmash();
        }
    }

    private float platformWidth(Body platformBody) {
        PolygonShape shape = (PolygonShape) platformBody.getFixtureList().get(0).getShape();
        Vector2 v = new Vector2();
        shape.getVertex(0, v);
        return Math.abs(v.x) * 2f;
    }

    private void removeBodyFromRows(Body body) {
        for (PlatformRow row : rows) {
            if (row.left == body) row.left = null;
            if (row.right == body) row.right = null;
            if (row.leftKinematic == body) row.leftKinematic = null;
            if (row.rightKinematic == body) row.rightKinematic = null;
            if (row.powerUp == body) row.powerUp = null;
        }
    }

    private void updateCameraAndScroll(float delta) {
        MotionTrail.add(trailPositions, new MotionTrail.Point(ballBody.getPosition().x, ballBody.getPosition().y), TRAIL_MAX_LENGTH);

        float depth = spawnY - ballBody.getPosition().y;
        depthScore = Math.max(depthScore, depth);

        // Stateless recompute every check, same style as currentSkinTier() --
        // but depthScore only, deliberately NOT Math.max(playerProgress
        // .getBestDepth(), depthScore) like currentSkinTier() uses. Biomes
        // are a cyclic per-run experience, not a one-way reward ratchet --
        // using lifetime-best here would let a high-best-depth player
        // permanently skip the early roster on every future run (plan-eng-
        // review outside voice finding). No "previous biome" state to
        // track; setLinearDamping() is a cheap field write, safe to call
        // unconditionally every frame even when the value hasn't changed.
        ballBody.setLinearDamping(Biome.biomeFor(depthScore).getLinearDamping());

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

        float recycleAboveY = camera.position.y + viewport.getWorldHeight();
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
        float recycleAboveY = camera.position.y + viewport.getWorldHeight();
        List<PlatformRow> toRemove = new ArrayList<>();
        for (PlatformRow row : rows) {
            if (row.y > recycleAboveY) {
                toRemove.add(row);
            }
        }
        for (PlatformRow row : toRemove) {
            if (row.left != null) world.destroyBody(row.left);
            if (row.right != null) world.destroyBody(row.right);
            // MOVING is mutually exclusive with WEAK and isn't a power-up,
            // so this row-recycling loop is the ONLY place a MOVING side's
            // kinematic body is ever destroyed in this slice -- untrack
            // BEFORE destroyBody() so MovingPlatformManager never holds a
            // dangling reference to a destroyed native body (plan-eng-
            // review Failure Modes finding).
            if (row.leftKinematic != null) {
                movingPlatformManager.untrack(row.leftKinematic);
                world.destroyBody(row.leftKinematic);
            }
            if (row.rightKinematic != null) {
                movingPlatformManager.untrack(row.rightKinematic);
                world.destroyBody(row.rightKinematic);
            }
            if (row.powerUp != null) world.destroyBody(row.powerUp);
            rows.remove(row);
        }
    }

    private void checkGameOver() {
        float ballY = ballBody.getPosition().y;
        if (ballY - camera.position.y > viewport.getWorldHeight() / 2f + TOP_MARGIN) {
            eventBus.dispatch(new BallOffTop());
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        renderer.resize(width, height);
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
        renderer.dispose();
    }

    // ----- Package-private getters for GameplayRenderer (plan-eng-review
    // Next Step 1a) -- per-frame/per-run state that only GameplayScreen
    // owns; the renderer reads it fresh each frame rather than caching it. -----

    OrthographicCamera getCamera() {
        return camera;
    }

    Viewport getViewport() {
        return viewport;
    }

    List<PlatformRow> getRows() {
        return rows;
    }

    DebrisManager getDebrisManager() {
        return debrisManager;
    }

    Body getBallBody() {
        return ballBody;
    }

    Deque<MotionTrail.Point> getTrailPositions() {
        return trailPositions;
    }

    PowerUpManager getPowerUpManager() {
        return powerUpManager;
    }

    GameOverController getGameOverController() {
        return gameOverController;
    }

    ScoreManager getScoreManager() {
        return scoreManager;
    }

    float getDepthScore() {
        return depthScore;
    }

    // Package-private: read by GameOverScreen for the conditional bonus
    // line. Run-scoped total, never persisted (see RampagePowerUp's class
    // doc).
    int getRampageBonus() {
        return rampagePowerUp.getRunBonus();
    }

    // Package-private: read by GameplayRenderer for the rampage HUD
    // counter. Live, per-activation count -- decays to 0 when rampage
    // expires (see RampagePowerUp's class doc), distinct from getRampageBonus().
    int getRampageSmashCount() {
        return rampagePowerUp.getSmashCount();
    }

    boolean isUseTilt() {
        return useTilt;
    }

    float getComboPulseTimer() {
        return comboPulseTimer;
    }

    String getToastText() {
        return toastText;
    }

    float getToastTimer() {
        return toastTimer;
    }

    static class PlatformRow {
        final float y;
        Body left;
        Body right;
        // Non-null only when the matching side is PlatformType.MOVING --
        // left/right stay the static filler body in that case (narrower
        // than the full flanking span, see moving-platforms step 6), and
        // this holds the separate small kinematic piece patrolling near
        // the gap. Null for NORMAL/WEAK sides, same as left/right are null
        // when that side has no flanking segment at all (moving-platforms
        // step 5).
        Body leftKinematic;
        Body rightKinematic;
        Body powerUp;

        PlatformRow(float y, Body left, Body right, Body powerUp) {
            this.y = y;
            this.left = left;
            this.right = right;
            this.powerUp = powerUp;
        }
    }
}
