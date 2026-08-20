package com.foukas.dropbox2d.events;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ContactDispatcher had zero test coverage before the moving-platforms
 * design doc (2026-08-06) -- these tests exist specifically to guard the
 * regression the design doc's Success Criteria named: extending the tag
 * check with a new value must not silently stop BallTouchedPlatform (and
 * the combo-chain reset it drives) from firing. Mirrors DebrisManagerTest's
 * headless Box2D setup.
 *
 * preSolve()'s break-threshold wiring (as opposed to beginContact()'s
 * BallTouchedPlatform dispatch) had ZERO coverage until the rampage design
 * doc (plan-eng-review 2026-08-06) -- extending it to break "platform"/
 * "movingPlatform" tags while rampage is active is a REGRESSION-RULE
 * mandatory test, not optional: it modifies existing, previously-untested
 * behavior with a real new failure mode (NORMAL platforms silently
 * breaking outside rampage, or never breaking during it). */
class ContactDispatcherTest {

    private static final float BALL_RADIUS = 0.4f;

    private World world;
    private GameEventBus bus;
    private List<GameEvent> dispatched;
    // Mutable so individual tests can flip it before stepping -- mirrors
    // how GameplayScreen wires this to powerUpManager.getActiveType().
    private boolean rampageActive;

    @BeforeAll
    static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @BeforeEach
    void setUp() {
        world = new World(new Vector2(0, -25f), true);
        bus = new GameEventBus();
        dispatched = new ArrayList<>();
        bus.subscribe(dispatched::add);
        rampageActive = false;
        world.setContactListener(new ContactDispatcher(bus, () -> rampageActive));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Body createBall(float x, float y) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        Body body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(BALL_RADIUS);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1f;
        body.createFixture(fixtureDef).setUserData("ball");
        shape.dispose();
        return body;
    }

    private Body createPlatform(float x, float y, String tag) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.StaticBody;
        bodyDef.position.set(x, y);
        Body body = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(1f, 0.2f);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        body.createFixture(fixtureDef).setUserData(tag);
        shape.dispose();
        return body;
    }

    private void stepUntilContactOrTimeout(int maxSteps) {
        for (int i = 0; i < maxSteps && dispatched.isEmpty(); i++) {
            world.step(1f / 60f, 6, 2);
        }
    }

    /** A ball already falling fast enough to cross DestructiblePlatform's
     * momentum threshold on first contact -- avoids needing a long,
     * gravity-only free-fall setup just to get the ball moving. Mass at
     * density 1 for a 0.4-radius circle is ~0.5, so -50 gives momentum
     * ~25, comfortably above the 20 threshold. */
    private Body createFastFallingBall(float x, float y) {
        Body ball = createBall(x, y);
        ball.setLinearVelocity(0f, -50f);
        return ball;
    }

    private boolean anyPlatformDestroyed() {
        return dispatched.stream().anyMatch(PlatformDestroyed.class::isInstance);
    }

    @Test
    void landingOnAMovingPlatformFiresBallTouchedPlatform() {
        createPlatform(0f, 0f, "movingPlatform");
        createBall(0f, BALL_RADIUS + 0.05f); // just above the platform, falls into contact

        stepUntilContactOrTimeout(120);

        assertTrue(dispatched.stream().anyMatch(BallTouchedPlatform.class::isInstance),
                "landing on a movingPlatform-tagged fixture must fire BallTouchedPlatform");
    }

    @Test
    void landingOnAnOrdinaryPlatformStillFiresBallTouchedPlatform() {
        // Regression guard: confirms the new tag branch didn't accidentally
        // change behavior for the existing "platform" tag.
        createPlatform(0f, 0f, "platform");
        createBall(0f, BALL_RADIUS + 0.05f);

        stepUntilContactOrTimeout(120);

        assertTrue(dispatched.stream().anyMatch(BallTouchedPlatform.class::isInstance),
                "landing on a platform-tagged fixture must still fire BallTouchedPlatform");
    }

    // Rampage design doc, plan-eng-review 2026-08-06 (Test Review Iron
    // Rule -- mandatory, not optional): preSolve()'s break-threshold
    // wiring had zero coverage before this. These four tests confirm the
    // new tag scope in both directions, plus that the pre-existing
    // "weakPlatform always breaks" behavior is genuinely unaffected.

    @Test
    void fastBallBreaksAPlatformTaggedFixtureWhileRampageIsActive() {
        rampageActive = true;
        createPlatform(0f, 0f, "platform");
        createFastFallingBall(0f, BALL_RADIUS + 0.05f);

        stepUntilContactOrTimeout(10);

        assertTrue(anyPlatformDestroyed(), "a platform-tagged fixture must break under sufficient momentum while rampage is active");
    }

    @Test
    void fastBallBreaksAMovingPlatformTaggedFixtureWhileRampageIsActive() {
        rampageActive = true;
        createPlatform(0f, 0f, "movingPlatform");
        createFastFallingBall(0f, BALL_RADIUS + 0.05f);

        stepUntilContactOrTimeout(10);

        assertTrue(anyPlatformDestroyed(), "a movingPlatform-tagged fixture must break under sufficient momentum while rampage is active");
    }

    @Test
    void fastBallDoesNotBreakAPlatformTaggedFixtureWhileRampageIsInactive() {
        // The core existing guarantee this whole feature must not regress:
        // NORMAL platforms stay permanently solid outside rampage, no
        // matter how much momentum the ball has.
        rampageActive = false;
        createPlatform(0f, 0f, "platform");
        createFastFallingBall(0f, BALL_RADIUS + 0.05f);

        stepUntilContactOrTimeout(10);

        assertFalse(anyPlatformDestroyed(), "a platform-tagged fixture must never break while rampage is inactive, regardless of momentum");
    }

    @Test
    void fastBallBreaksAWeakPlatformRegardlessOfRampageState() {
        // Pre-existing behavior (predates rampage entirely) -- confirms
        // it's unaffected by the new rampage-active branch either way.
        rampageActive = false;
        createPlatform(0f, 0f, "weakPlatform");
        createFastFallingBall(0f, BALL_RADIUS + 0.05f);

        stepUntilContactOrTimeout(10);

        assertTrue(anyPlatformDestroyed(), "a weakPlatform-tagged fixture must break regardless of rampage state");
    }
}
