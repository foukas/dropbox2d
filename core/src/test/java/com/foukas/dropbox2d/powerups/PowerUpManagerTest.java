package com.foukas.dropbox2d.powerups;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerUpManagerTest {

    /** Minimal fake -- exercises the manager's own orchestration logic
     * without needing a real Box2D body, same pattern as testing
     * GameEventBus against fake listeners. */
    private static class FakePowerUp implements PowerUp {
        boolean active;
        float remaining;
        int activateCalls;

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
}
