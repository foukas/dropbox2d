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

class RampagePowerUpTest {

    private World world;
    private Body ballBody;
    private RampagePowerUp powerUp;
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

        powerUp = new RampagePowerUp(ballBody);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void startsInactive() {
        assertFalse(powerUp.isActive());
        assertEquals(normalMass, ballBody.getMass(), 0.0001f);
        assertEquals(0, powerUp.getSmashCount());
        assertEquals(0, powerUp.getRunBonus());
    }

    @Test
    void activateIncreasesActualBallMass() {
        powerUp.activate();

        assertTrue(powerUp.isActive());
        assertTrue(ballBody.getMass() > normalMass * 5f, "rampage mass should be a large multiple of normal mass");
    }

    @Test
    void expiresAfterItsDurationAndRestoresNormalMass() {
        powerUp.activate();
        powerUp.update(4.9f);
        assertTrue(powerUp.isActive(), "should still be active just before its duration elapses");

        powerUp.update(0.2f); // crosses the 5s duration
        assertFalse(powerUp.isActive());
        assertEquals(normalMass, ballBody.getMass(), 0.0001f, "mass must revert to normal once the power-up expires");
    }

    @Test
    void resetForcesInactiveAndNormalMassImmediately() {
        powerUp.activate();
        powerUp.reset();

        assertFalse(powerUp.isActive());
        assertEquals(normalMass, ballBody.getMass(), 0.0001f);
    }

    @Test
    void recordSmashIncrementsBothLiveCountAndRunBonus() {
        powerUp.activate();
        powerUp.recordSmash();
        powerUp.recordSmash();
        powerUp.recordSmash();

        assertEquals(3, powerUp.getSmashCount());
        assertEquals(3, powerUp.getRunBonus());
    }

    @Test
    void smashCountDecaysOnExpiryButRunBonusDoesNot() {
        powerUp.activate();
        powerUp.recordSmash();
        powerUp.recordSmash();
        powerUp.update(5f); // expires

        assertEquals(0, powerUp.getSmashCount(), "live counter resets on expiry, not on touch");
        assertEquals(2, powerUp.getRunBonus(), "run-scoped bonus survives expiry -- only a fresh instance clears it");
    }

    @Test
    void smashCountDecaysOnResetButRunBonusDoesNot() {
        powerUp.activate();
        powerUp.recordSmash();
        powerUp.reset(); // e.g. forced off by PowerUpManager's exclusivity fix

        assertEquals(0, powerUp.getSmashCount());
        assertEquals(1, powerUp.getRunBonus());
    }

    @Test
    void secondActivationSmashCountStartsFreshButRunBonusKeepsAccumulating() {
        powerUp.activate();
        powerUp.recordSmash();
        powerUp.update(5f); // expires, smashCount decays

        powerUp.activate(); // picked up again later in the same run
        powerUp.recordSmash();
        powerUp.recordSmash();

        assertEquals(2, powerUp.getSmashCount(), "new activation window, live counter starts fresh");
        assertEquals(3, powerUp.getRunBonus(), "bonus keeps accumulating across activations within the same run");
    }

    // User feedback, rampage follow-up 2026-08-06: picking up wrecking ball
    // while rampage is active should extend rampage's timer, not switch
    // power-ups (rampage is a strict upgrade over wrecking ball).
    @Test
    void extendByWreckingBallPickupAddsTimeWithoutResettingSmashState() {
        powerUp.activate();
        powerUp.recordSmash();
        float remainingBefore = powerUp.getRemaining();

        powerUp.extendByWreckingBallPickup();

        assertTrue(powerUp.getRemaining() > remainingBefore, "extending should add time, not just leave it unchanged");
        assertTrue(powerUp.isActive());
        assertEquals(1, powerUp.getSmashCount(), "extending is not an expiry or reset -- the live streak must survive it");
    }
}
