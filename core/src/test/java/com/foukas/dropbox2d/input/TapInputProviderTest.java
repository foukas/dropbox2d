package com.foukas.dropbox2d.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TapInputProviderTest {

    @Test
    void leftHalfStearsLeft() {
        assertEquals(-1f, TapInputProvider.sideOf(100f, 800f));
    }

    @Test
    void rightHalfSteersRight() {
        assertEquals(1f, TapInputProvider.sideOf(700f, 800f));
    }

    @Test
    void exactCenterCountsAsRight() {
        // touchX < half is the only left condition -- exact center is not
        // "< half" so it falls to right. Documenting the boundary rather
        // than leaving it accidental.
        assertEquals(1f, TapInputProvider.sideOf(400f, 800f));
    }
}
