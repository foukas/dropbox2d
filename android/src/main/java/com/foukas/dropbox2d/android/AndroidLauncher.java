package com.foukas.dropbox2d.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.foukas.dropbox2d.DropGame;

public class AndroidLauncher extends AndroidApplication {
    private final AndroidSafeAreaInsets safeAreaInsets = new AndroidSafeAreaInsets();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        // Needed for TiltInputProvider (Approach B1) -- was correctly false
        // when the project was scaffolded and nothing used it yet.
        config.useAccelerometer = true;
        config.useCompass = false;
        initialize(new DropGame(safeAreaInsets), config);

        // Next Step 12 device verification found HUD corner text hidden
        // under the status bar/gesture nav -- apps targeting SDK 35+ get
        // edge-to-edge enforced with no opt-out, so the decor view now draws
        // under the system bars by default. minSdk is 24, well below the
        // API 30 getInsets(int)/WindowInsetsCompat baseline, so this uses
        // the older getSystemWindowInset*() accessors (deprecated but still
        // functional) rather than pulling in an androidx.core dependency
        // for a single value read.
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
            safeAreaInsets.update(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
}
