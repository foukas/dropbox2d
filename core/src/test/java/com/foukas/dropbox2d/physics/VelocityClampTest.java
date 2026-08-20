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

class VelocityClampTest {

    private static final float MAX_SAFE_SPEED = 40f; // mirrors VelocityClamp's own constant

    private World world;
    private Body body;

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
    void velocityBelowThresholdIsUntouched() {
        body.setLinearVelocity(3f, -5f);
        boolean clamped = VelocityClamp.checkAndClamp(body);

        assertFalse(clamped);
        assertEquals(3f, body.getLinearVelocity().x, 0.001f);
        assertEquals(-5f, body.getLinearVelocity().y, 0.001f);
    }

    @Test
    void velocityExactlyAtThresholdIsUntouched() {
        body.setLinearVelocity(MAX_SAFE_SPEED, 0f);
        boolean clamped = VelocityClamp.checkAndClamp(body);

        assertFalse(clamped);
        assertEquals(MAX_SAFE_SPEED, body.getLinearVelocity().x, 0.001f);
    }

    @Test
    void velocityAboveThresholdIsScaledDownPreservingDirection() {
        body.setLinearVelocity(60f, -80f); // magnitude 100, direction (0.6, -0.8)
        boolean clamped = VelocityClamp.checkAndClamp(body);

        assertTrue(clamped);
        Vector2 result = body.getLinearVelocity();
        assertEquals(MAX_SAFE_SPEED, result.len(), 0.01f, "magnitude must be scaled to the safe max");
        assertEquals(0.6f, result.x / result.len(), 0.001f, "direction (unit x) must be preserved");
        assertEquals(-0.8f, result.y / result.len(), 0.001f, "direction (unit y) must be preserved");
    }

    @Test
    void purelyVerticalHighVelocityIsClamped() {
        // The actual off-screen-launch scenario this guard exists for: a
        // component-wise clamp on vel.x alone (the bug the design doc
        // explicitly rejected) would leave this completely unclamped.
        body.setLinearVelocity(0f, -90f);
        boolean clamped = VelocityClamp.checkAndClamp(body);

        assertTrue(clamped);
        assertEquals(0f, body.getLinearVelocity().x, 0.001f);
        assertEquals(-MAX_SAFE_SPEED, body.getLinearVelocity().y, 0.01f);
    }
}
