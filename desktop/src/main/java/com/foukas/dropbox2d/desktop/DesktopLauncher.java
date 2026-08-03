package com.foukas.dropbox2d.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.foukas.dropbox2d.DropGame;
import com.foukas.dropbox2d.platform.NoOpSafeAreaInsets;

/** Desktop launcher for fast local iteration -- click and hold with the
 * mouse as a stand-in for touch while testing on desktop. The real
 * validation target is on-device (see design doc Success Criteria). */
public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("dropbox2d (dev)");
        config.setWindowedMode(540, 960);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new DropGame(new NoOpSafeAreaInsets()), config);
    }
}
