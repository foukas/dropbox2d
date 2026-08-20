package com.foukas.dropbox2d.powerups;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Type-keyed registry with a single active slot -- only one power-up is
 * active at a time, matching current game design. Adding a second power-up
 * type is `register("newType", new NewPowerUp(...))` plus tagging its
 * pickup fixtures with that type string; nothing else here changes. */
public class PowerUpManager {
    private final Map<String, PowerUp> registry = new LinkedHashMap<>();
    private String activeType;

    public void register(String type, PowerUp powerUp) {
        registry.put(type, powerUp);
    }

    public void activate(String type) {
        PowerUp powerUp = registry.get(type);
        if (powerUp == null) {
            return; // unknown type tag -- ignore rather than crash
        }
        // Reset whichever OTHER power-up was previously active before
        // activating the new one (rampage design doc, plan-eng-review
        // 2026-08-06) -- "only one power-up is active at a time" was true
        // by coincidence until a second density-driven power-up existed;
        // without this, e.g. picking up rampage while wrecking ball is
        // still active would leave wrecking ball's density change applied
        // underneath rampage's, until whichever timer expires first
        // silently snaps density back out from under the other. Skipped
        // when reactivating the SAME type -- that's just a duration
        // refresh (see WreckingBallPowerUpTest.reactivatingWhileActive
        // RefreshesDuration), not an exclusivity conflict.
        if (activeType != null && !activeType.equals(type)) {
            PowerUp previous = registry.get(activeType);
            if (previous != null) {
                previous.reset();
            }
        }
        powerUp.activate();
        activeType = type;
    }

    public void update(float delta) {
        for (PowerUp powerUp : registry.values()) {
            powerUp.update(delta);
        }
    }

    public boolean isActive() {
        return activeType != null && registry.get(activeType).isActive();
    }

    public float getRemaining() {
        return isActive() ? registry.get(activeType).getRemaining() : 0f;
    }

    public String getActiveType() {
        return isActive() ? activeType : null;
    }

    public Set<String> registeredTypes() {
        return registry.keySet();
    }

    public void reset() {
        for (PowerUp powerUp : registry.values()) {
            powerUp.reset();
        }
        activeType = null;
    }
}
