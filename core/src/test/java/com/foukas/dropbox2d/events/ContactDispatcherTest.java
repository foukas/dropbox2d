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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** ContactDispatcher had zero test coverage before the moving-platforms
 * design doc (2026-08-06) -- these tests exist specifically to guard the
 * regression the design doc's Success Criteria named: extending the tag
 * check with a new value must not silently stop BallTouchedPlatform (and
 * the combo-chain reset it drives) from firing. Mirrors DebrisManagerTest's
 * headless Box2D setup. */
class ContactDispatcherTest {

    private static final float BALL_RADIUS = 0.4f;

    private World world;
    private GameEventBus bus;
    private List<GameEvent> dispatched;

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
        world.setContactListener(new ContactDispatcher(bus));
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
}
