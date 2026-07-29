package com.foukas.dropbox2d.powerups;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WreckingBallManagerTest {

    private World world;
    private Body ballBody;
    private WreckingBallManager manager;
    private float normalMass;

    @BeforeAll
    static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @BeforeEach
    void setUp() {
        world = new World(new Vector2(0, -25f), true);
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        ballBody = world.createBody(def);
        CircleShape shape = new CircleShape();
        shape.setRadius(0.4f);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.density = 1f;
        ballBody.createFixture(fixtureDef);
        shape.dispose();
        ballBody.resetMassData();
        normalMass = ballBody.getMass();

        manager = new WreckingBallManager(ballBody);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void startsInactive() {
        assertFalse(manager.isActive());
        assertEquals(normalMass, ballBody.getMass(), 0.0001f);
    }

    @Test
    void activateIncreasesActualBallMass() {
        manager.activate();

        assertTrue(manager.isActive());
        assertTrue(ballBody.getMass() > normalMass * 5f, "wrecking-ball mass should be a large multiple of normal mass");
    }

    @Test
    void expiresAfterItsDurationAndRestoresNormalMass() {
        manager.activate();
        manager.update(4.9f);
        assertTrue(manager.isActive(), "should still be active just before its duration elapses");

        manager.update(0.2f); // crosses the 5s duration
        assertFalse(manager.isActive());
        assertEquals(normalMass, ballBody.getMass(), 0.0001f, "mass must revert to normal once the power-up expires");
    }

    @Test
    void resetForcesInactiveAndNormalMassImmediately() {
        manager.activate();
        manager.reset();

        assertFalse(manager.isActive());
        assertEquals(normalMass, ballBody.getMass(), 0.0001f);
    }

    @Test
    void reactivatingWhileActiveRefreshesDuration() {
        manager.activate();
        manager.update(4f);
        manager.activate(); // picked up again before the first one expired
        manager.update(4f); // would have expired the first activation, not the refreshed one

        assertTrue(manager.isActive());
    }
}
