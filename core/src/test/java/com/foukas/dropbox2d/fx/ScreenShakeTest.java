package com.foukas.dropbox2d.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenShakeTest {

    @Test
    void notShakingByDefault() {
        ScreenShake shake = new ScreenShake();
        assertFalse(shake.isShaking());
        assertEquals(0f, shake.getOffset().x);
        assertEquals(0f, shake.getOffset().y);
    }

    @Test
    void triggerStartsShaking() {
        ScreenShake shake = new ScreenShake();
        shake.trigger(0.3f, 0.2f);
        assertTrue(shake.isShaking());
    }

    @Test
    void offsetStaysWithinMagnitudeBounds() {
        ScreenShake shake = new ScreenShake();
        shake.trigger(0.3f, 0.2f);
        shake.update(0.01f);

        assertTrue(Math.abs(shake.getOffset().x) <= 0.2f);
        assertTrue(Math.abs(shake.getOffset().y) <= 0.2f);
    }

    @Test
    void stopsAfterItsDurationElapses() {
        ScreenShake shake = new ScreenShake();
        shake.trigger(0.3f, 0.2f);

        shake.update(0.2f);
        assertTrue(shake.isShaking(), "should still be shaking before duration elapses");

        shake.update(0.2f); // total 0.4s > 0.3s duration
        assertFalse(shake.isShaking());
        assertEquals(0f, shake.getOffset().x);
        assertEquals(0f, shake.getOffset().y);
    }

    @Test
    void strongerTriggerReplacesAWeakerActiveShake() {
        ScreenShake shake = new ScreenShake();
        shake.trigger(0.1f, 0.1f);
        shake.trigger(0.5f, 0.5f);
        shake.update(0.01f);

        assertTrue(Math.abs(shake.getOffset().x) <= 0.5f);
    }

    @Test
    void weakerTriggerDoesNotShortenAStrongerActiveShake() {
        ScreenShake shake = new ScreenShake();
        shake.trigger(1f, 0.5f);
        shake.trigger(0.05f, 0.01f); // weaker, should be ignored

        shake.update(0.5f); // would have expired the weak trigger, not the strong one
        assertTrue(shake.isShaking());
    }
}
