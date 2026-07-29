package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsNaNGuardTest {

    private World world;
    private Body body;

    @BeforeAll
    static void initBox2D() {
        // Box2D.init() alone only loads the "gdx-box2d" native lib. Body
        // internally uses BufferUtils, whose native methods live in
        // libGDX's own base "gdx" native lib -- normally loaded as a side
        // effect of the full Application lifecycle (Lwjgl3Application /
        // AndroidApplication), which doesn't exist in a bare JUnit test.
        // Load it explicitly or Body's constructor throws
        // UnsatisfiedLinkError on BufferUtils.getBufferAddress.
        GdxNativesLoader.load();
        Box2D.init();
    }

    @BeforeEach
    void setUp() {
        world = new World(new Vector2(0, -10f), true);
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.DynamicBody;
        def.position.set(1f, 2f);
        body = world.createBody(def);
        CircleShape shape = new CircleShape();
        shape.setRadius(0.4f);
        body.createFixture(shape, 1f);
        shape.dispose();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void healthyBodyIsUntouched() {
        body.setLinearVelocity(2f, -3f);
        boolean corrected = PhysicsNaNGuard.checkAndFix(body, 0f, 0f);

        assertFalse(corrected);
        assertEquals(1f, body.getPosition().x, 0.001f);
        assertEquals(2f, body.getPosition().y, 0.001f);
        assertEquals(2f, body.getLinearVelocity().x, 0.001f);
        assertEquals(-3f, body.getLinearVelocity().y, 0.001f);
    }

    @Test
    void nanPositionIsResetToSafeSpot() {
        body.setTransform(Float.NaN, 5f, 0f);
        boolean corrected = PhysicsNaNGuard.checkAndFix(body, 3f, 4f);

        assertTrue(corrected);
        assertEquals(3f, body.getPosition().x, 0.001f);
        assertEquals(4f, body.getPosition().y, 0.001f);
        assertEquals(0f, body.getLinearVelocity().x, 0.001f);
        assertEquals(0f, body.getLinearVelocity().y, 0.001f);
    }

    @Test
    void infiniteVelocityIsZeroed() {
        body.setLinearVelocity(Float.POSITIVE_INFINITY, 0f);
        boolean corrected = PhysicsNaNGuard.checkAndFix(body, 3f, 4f);

        assertTrue(corrected);
        assertEquals(0f, body.getLinearVelocity().x, 0.001f);
        assertEquals(0f, body.getLinearVelocity().y, 0.001f);
    }

    @Test
    void absurdMagnitudeVelocityIsZeroedEvenWithoutNaN() {
        body.setLinearVelocity(500000f, 0f);
        boolean corrected = PhysicsNaNGuard.checkAndFix(body, 3f, 4f);

        assertTrue(corrected);
        assertEquals(0f, body.getLinearVelocity().x, 0.001f);
    }

    @Test
    void absurdMagnitudePositionIsResetEvenWithoutNaN() {
        body.setTransform(10_000_000f, 0f, 0f);
        boolean corrected = PhysicsNaNGuard.checkAndFix(body, 3f, 4f);

        assertTrue(corrected);
        assertEquals(3f, body.getPosition().x, 0.001f);
        assertEquals(4f, body.getPosition().y, 0.001f);
    }
}
