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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpManagerTest {

    // Box2D lifecycle -- only needed by the real-Body integration test
    // below (activatingASecondRealDensityPowerUpCorrectlyOverwritesTheFirst);
    // the FakePowerUp-based tests above/below don't touch it.
    private World world;
    private Body ballBody;
    private float normalMass;

    @BeforeAll
    static void initBox2D() {
        GdxNativesLoader.load();
        Box2D.init();
    }

    @BeforeEach
    void setUpBox2D() {
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
    }

    @AfterEach
    void tearDownBox2D() {
        world.dispose();
    }

    /** Minimal fake -- exercises the manager's own orchestration logic
     * without needing a real Box2D body, same pattern as testing
     * GameEventBus against fake listeners. */
    private static class FakePowerUp implements PowerUp {
        boolean active;
        float remaining;
        int activateCalls;
        int resetCalls;

        @Override
        public void activate() {
            active = true;
            remaining = 5f;
            activateCalls++;
        }

        @Override
        public void update(float delta) {
            if (!active) return;
            remaining -= delta;
            if (remaining <= 0f) {
                remaining = 0f;
                active = false;
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public float getRemaining() {
            return remaining;
        }

        @Override
        public void reset() {
            active = false;
            remaining = 0f;
            resetCalls++;
        }
    }

    @Test
    void activatingAnUnknownTypeIsANoOp() {
        PowerUpManager manager = new PowerUpManager();
        manager.activate("doesNotExist");

        assertFalse(manager.isActive());
        assertNull(manager.getActiveType());
    }

    @Test
    void activatesTheRegisteredTypeByTag() {
        PowerUpManager manager = new PowerUpManager();
        FakePowerUp fake = new FakePowerUp();
        manager.register("test", fake);

        manager.activate("test");

        assertTrue(manager.isActive());
        assertEquals("test", manager.getActiveType());
        assertEquals(1, fake.activateCalls);
    }

    @Test
    void updateAdvancesTheActivePowerUpAndDeactivatesOnExpiry() {
        PowerUpManager manager = new PowerUpManager();
        FakePowerUp fake = new FakePowerUp();
        manager.register("test", fake);
        manager.activate("test");

        manager.update(6f); // past the fake's 5s duration

        assertFalse(manager.isActive());
        assertNull(manager.getActiveType());
    }

    @Test
    void multipleRegisteredTypesDoNotInterfere() {
        PowerUpManager manager = new PowerUpManager();
        FakePowerUp a = new FakePowerUp();
        FakePowerUp b = new FakePowerUp();
        manager.register("a", a);
        manager.register("b", b);

        manager.activate("a");

        assertTrue(a.isActive());
        assertFalse(b.isActive());
        assertEquals("a", manager.getActiveType());
        assertEquals(2, manager.registeredTypes().size());
    }

    @Test
    void resetClearsActiveState() {
        PowerUpManager manager = new PowerUpManager();
        FakePowerUp fake = new FakePowerUp();
        manager.register("test", fake);
        manager.activate("test");

        manager.reset();

        assertFalse(manager.isActive());
        assertNull(manager.getActiveType());
        assertFalse(fake.isActive());
    }

    // Exclusivity fix (rampage design doc, plan-eng-review 2026-08-06):
    // activating a second type while a first is still active must reset
    // the first. Orchestration-level check via FakePowerUp -- confirms
    // PowerUpManager itself calls reset() on the right target, not
    // whether density state composes correctly (that's the real-Body
    // integration test below, added specifically because a FakePowerUp
    // test alone can't catch an ordering bug in how two REAL
    // AbstractDensityPowerUp subclasses share one Body).
    @Test
    void activatingASecondTypeResetsThePreviouslyActiveOne() {
        PowerUpManager manager = new PowerUpManager();
        FakePowerUp first = new FakePowerUp();
        FakePowerUp second = new FakePowerUp();
        manager.register("first", first);
        manager.register("second", second);

        manager.activate("first");
        manager.activate("second");

        assertEquals(1, first.resetCalls, "the previously-active power-up must be reset when a different one activates");
        assertFalse(first.isActive());
        assertTrue(second.isActive());
        assertEquals("second", manager.getActiveType());
    }

    @Test
    void reactivatingTheSameTypeDoesNotResetIt() {
        PowerUpManager manager = new PowerUpManager();
        FakePowerUp fake = new FakePowerUp();
        manager.register("test", fake);

        manager.activate("test");
        manager.activate("test"); // e.g. picked up again before it expired

        assertEquals(0, fake.resetCalls, "reactivating the same type is a duration refresh, not an exclusivity conflict");
        assertEquals(2, fake.activateCalls);
    }

    // Real-Body integration test (plan-eng-review outside-voice finding):
    // the FakePowerUp tests above verify PowerUpManager calls reset() on
    // the right target, but FakePowerUp has no real density/Box2D state,
    // so they can't catch an ordering bug where reset() fires AFTER
    // activate() and clobbers the real density change back to normal.
    // Uses two real AbstractDensityPowerUp subclasses (WreckingBallPowerUp,
    // RampagePowerUp) sharing one real Body -- if activate()'s internal
    // ordering were wrong (new power-up's activate() before the old one's
    // reset()), the ball's final mass would incorrectly read back at
    // normal (1x) instead of the second power-up's target (6x), even
    // though both share the same target density value.
    @Test
    void activatingASecondRealDensityPowerUpCorrectlyOverwritesTheFirst() {
        PowerUpManager manager = new PowerUpManager();
        WreckingBallPowerUp wreckingBall = new WreckingBallPowerUp(ballBody);
        RampagePowerUp rampage = new RampagePowerUp(ballBody);
        manager.register("wreckingBall", wreckingBall);
        manager.register("rampage", rampage);

        manager.activate("wreckingBall");
        assertTrue(ballBody.getMass() > normalMass * 5f, "wrecking ball should have multiplied the real mass");

        manager.activate("rampage");

        assertFalse(wreckingBall.isActive(), "wrecking ball must be reset when rampage activates");
        assertTrue(rampage.isActive());
        assertTrue(ballBody.getMass() > normalMass * 5f,
                "final density must reflect rampage's target, not have been clobbered back to normal by a wrong reset/activate ordering");
    }
}
