package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovingPlatformManagerTest {

    private World world;
    private MovingPlatformManager manager;

    @BeforeAll
    static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @BeforeEach
    void setUp() {
        world = new World(new Vector2(0, -25f), true);
        manager = new MovingPlatformManager();
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Body kinematicBodyAt(float x) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.KinematicBody;
        bodyDef.position.set(x, 0f);
        return world.createBody(bodyDef);
    }

    @Test
    void checkBoundsWithNoTrackedPlatformsIsANoOp() {
        manager.checkBounds();
        assertEquals(0, manager.count());
    }

    @Test
    void trackStartsTheBodyMovingTowardMaxX() {
        Body body = kinematicBodyAt(1f);
        manager.track(body, 0f, 2f);
        assertTrue(body.getLinearVelocity().x > 0f, "track() should start the body moving toward maxX");
    }

    @Test
    void checkBoundsReversesVelocityAtMaxXBound() {
        Body body = kinematicBodyAt(2f);
        manager.track(body, 0f, 2f); // starts moving toward maxX=2, already there
        manager.checkBounds();
        assertTrue(body.getLinearVelocity().x < 0f, "reaching maxX while moving toward it should reverse to negative");
    }

    @Test
    void checkBoundsReversesVelocityAtMinXBound() {
        Body body = kinematicBodyAt(0f);
        manager.track(body, 0f, 2f);
        body.setLinearVelocity(-0.8f, 0f); // simulate having patrolled back to minX
        manager.checkBounds();
        assertTrue(body.getLinearVelocity().x > 0f, "reaching minX while moving toward it should reverse to positive");
    }

    @Test
    void checkBoundsDoesNotDoubleReverseABodySittingAtABound() {
        Body body = kinematicBodyAt(2f);
        manager.track(body, 0f, 2f);
        manager.checkBounds(); // reverses to negative, now moving away from maxX
        float velocityAfterFirstCheck = body.getLinearVelocity().x;
        manager.checkBounds(); // still at x=2 (position hasn't integrated yet), but now moving away
        assertEquals(velocityAfterFirstCheck, body.getLinearVelocity().x, 0.0001f,
                "a body already moving away from the bound it's sitting at should not be reversed again");
    }

    @Test
    void untrackStopsTheBodyFromBeingChecked() {
        Body body = kinematicBodyAt(2f);
        manager.track(body, 0f, 2f);
        manager.untrack(body);
        assertEquals(0, manager.count());

        body.setLinearVelocity(0.8f, 0f); // still moving toward maxX
        manager.checkBounds();
        assertTrue(body.getLinearVelocity().x > 0f, "an untracked body must not be reversed by checkBounds()");
    }

    @Test
    void untrackingAnUntrackedBodyIsANoOp() {
        Body body = kinematicBodyAt(1f);
        manager.untrack(body); // never tracked
        assertEquals(0, manager.count());
    }
}
