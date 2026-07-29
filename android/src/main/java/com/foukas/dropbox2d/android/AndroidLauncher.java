package com.foukas.dropbox2d.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.foukas.dropbox2d.DropGame;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        // Needed for TiltInputProvider (Approach B1) -- was correctly false
        // when the project was scaffolded and nothing used it yet.
        config.useAccelerometer = true;
        config.useCompass = false;
        initialize(new DropGame(), config);
    }
}
