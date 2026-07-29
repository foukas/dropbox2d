package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebrisManagerTest {

    private World world;
    private DebrisManager manager;

    @BeforeAll
    static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @BeforeEach
    void setUp() {
        world = new World(new Vector2(0, -25f), true);
        manager = new DebrisManager(world);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void spawnCreatesTheExpectedFragmentCount() {
        manager.spawnDebris(0f, 0f);
        assertEquals(4, manager.count());
    }

    @Test
    void debrisCountStaysBoundedOverALongSimulatedSession() {
        // Simulate a long session: repeatedly spawn debris (as if many
        // platforms broke over time) while advancing time far past each
        // batch's lifetime -- this is exactly the failure mode flagged in
        // the eng review (unbounded debris growth), and this test fails
        // loudly if the despawn logic regresses.
        for (int breakEvent = 0; breakEvent < 50; breakEvent++) {
            manager.spawnDebris(breakEvent * 0.1f, 0f);
            manager.update(4f, 1000f); // 4s > DEBRIS_LIFETIME, well within recycle bounds
        }

        assertEquals(0, manager.count(), "debris older than its lifetime must be despawned every update, not accumulate");
    }

    @Test
    void debrisAboveRecycleLineIsDespawnedEvenIfYoung() {
        manager.spawnDebris(0f, 500f); // spawn "off in the distance" above the recycle line
        manager.update(0.01f, 10f); // young (age << lifetime) but clearly above recycleAboveY=10

        assertEquals(0, manager.count(), "debris above the recycle line must despawn even before its lifetime expires");
    }

    @Test
    void youngDebrisBelowRecycleLineSurvives() {
        manager.spawnDebris(0f, 0f);
        manager.update(0.5f, 1000f); // well under lifetime, well under recycle line

        assertEquals(4, manager.count());
    }

    @Test
    void clearRemovesAllFragmentsImmediately() {
        manager.spawnDebris(0f, 0f);
        manager.spawnDebris(1f, 0f);
        assertTrue(manager.count() > 0);

        manager.clear();

        assertEquals(0, manager.count());
    }
}
