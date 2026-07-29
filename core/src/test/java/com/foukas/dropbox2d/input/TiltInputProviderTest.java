package com.foukas.dropbox2d.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TiltInputProviderTest {

    @Test
    void withinDeadZoneProducesNoSteer() {
        assertEquals(0f, TiltInputProvider.normalize(0.5f));
        assertEquals(0f, TiltInputProvider.normalize(-0.9f));
        assertEquals(0f, TiltInputProvider.normalize(0f));
    }

    @Test
    void justPastDeadZoneProducesSmallSteer() {
        float steer = TiltInputProvider.normalize(1.5f);
        assertTrue(steer > 0f && steer < 0.2f, "just past the dead zone should be a small, not full, steer value");
    }

    @Test
    void atOrBeyondMaxTiltSaturatesAtFullSteer() {
        assertEquals(1f, TiltInputProvider.normalize(6f), 0.001f);
        assertEquals(1f, TiltInputProvider.normalize(20f), 0.001f, "beyond max tilt must clamp, not exceed 1");
    }

    @Test
    void negativeTiltMirrorsPositive() {
        assertEquals(-1f, TiltInputProvider.normalize(-6f), 0.001f);
        assertEquals(0f, TiltInputProvider.normalize(-0.5f));
    }

    @Test
    void midRangeTiltIsProportional() {
        float quarterRange = 1.0f + (6.0f - 1.0f) * 0.5f; // halfway between dead zone and max
        float steer = TiltInputProvider.normalize(quarterRange);
        assertEquals(0.5f, steer, 0.01f);
    }
}
