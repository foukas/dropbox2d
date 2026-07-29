package com.foukas.dropbox2d;

import com.foukas.dropbox2d.events.BallTouchedPlatform;
import com.foukas.dropbox2d.events.GameEvent;
import com.foukas.dropbox2d.events.GameEventListener;
import com.foukas.dropbox2d.events.GapPassed;

/** Tracks the gap-pass combo chain independently of collision-detection
 * code -- subscribes to events instead of being called into directly. */
public class ScoreManager implements GameEventListener {
    private int comboChain;
    private boolean touchedSinceLastRow;

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof BallTouchedPlatform) {
            touchedSinceLastRow = true;
        } else if (event instanceof GapPassed) {
            if (touchedSinceLastRow) {
                comboChain = 0;
            } else {
                comboChain++;
            }
            touchedSinceLastRow = false;
        }
    }

    public int getComboChain() {
        return comboChain;
    }

    public void reset() {
        comboChain = 0;
        touchedSinceLastRow = false;
    }
}
