package com.foukas.dropbox2d.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructiblePlatformTest {

    @Test
    void impulseBelowThresholdDoesNotBreak() {
        assertFalse(DestructiblePlatform.shouldBreak(5f, 8f));
    }

    @Test
    void impulseAtThresholdBreaks() {
        assertTrue(DestructiblePlatform.shouldBreak(8f, 8f));
    }

    @Test
    void impulseAboveThresholdBreaks() {
        assertTrue(DestructiblePlatform.shouldBreak(20f, 8f));
    }

    @Test
    void defaultThresholdIsUsedByOneArgOverload() {
        assertFalse(DestructiblePlatform.shouldBreak(1f));
        assertTrue(DestructiblePlatform.shouldBreak(1000f));
    }
}
