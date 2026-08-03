package com.foukas.dropbox2d.android;

import com.foukas.dropbox2d.platform.SafeAreaInsets;

/**
 * Written from the Activity's WindowInsets listener (main/UI thread) in
 * AndroidLauncher, read from GameplayRenderer.draw() (the GL thread) --
 * volatile is enough here, no synchronized needed, since each value is
 * independent and a one-frame-stale read is harmless for a visual margin.
 */
final class AndroidSafeAreaInsets implements SafeAreaInsets {
    private volatile float top;
    private volatile float bottom;
    private volatile float left;
    private volatile float right;

    void update(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    @Override
    public float top() {
        return top;
    }

    @Override
    public float bottom() {
        return bottom;
    }

    @Override
    public float left() {
        return left;
    }

    @Override
    public float right() {
        return right;
    }
}
