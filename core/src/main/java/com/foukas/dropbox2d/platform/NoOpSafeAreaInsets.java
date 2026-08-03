package com.foukas.dropbox2d.platform;

/** Desktop's SafeAreaInsets -- an LWJGL3 window has no system bars to draw
 * under, so every inset is always zero. */
public final class NoOpSafeAreaInsets implements SafeAreaInsets {
    @Override
    public float top() {
        return 0f;
    }

    @Override
    public float bottom() {
        return 0f;
    }

    @Override
    public float left() {
        return 0f;
    }

    @Override
    public float right() {
        return 0f;
    }
}
