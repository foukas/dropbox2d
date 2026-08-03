package com.foukas.dropbox2d.platform;

/**
 * System-bar/cutout insets in device pixels (plan-eng-review Next Step 12 --
 * discovered on physical-device verification, not desktop, since desktop has
 * no status bar/gesture nav to draw under). Android targets SDK 35+, where
 * edge-to-edge is enforced and cannot be opted out of -- the background can
 * still draw full-bleed under the system bars, but corner-positioned HUD
 * text needs these values added to its margins so it isn't hidden behind
 * them. Desktop has nothing to inset around, hence NoOpSafeAreaInsets.
 */
public interface SafeAreaInsets {
    float top();

    float bottom();

    float left();

    float right();
}
