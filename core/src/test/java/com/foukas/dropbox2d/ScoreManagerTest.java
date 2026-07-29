package com.foukas.dropbox2d;

import com.foukas.dropbox2d.events.BallTouchedPlatform;
import com.foukas.dropbox2d.events.GapPassed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreManagerTest {

    @Test
    void consecutiveGapPassesBuildCombo() {
        ScoreManager manager = new ScoreManager();
        manager.onEvent(new GapPassed(0f));
        manager.onEvent(new GapPassed(-2.6f));
        manager.onEvent(new GapPassed(-5.2f));

        assertEquals(3, manager.getComboChain());
    }

    @Test
    void platformContactResetsComboOnNextGapPass() {
        ScoreManager manager = new ScoreManager();
        manager.onEvent(new GapPassed(0f));
        manager.onEvent(new GapPassed(-2.6f));
        manager.onEvent(new BallTouchedPlatform());
        manager.onEvent(new GapPassed(-5.2f));

        assertEquals(0, manager.getComboChain());
    }

    @Test
    void comboBuildsAgainAfterAReset() {
        ScoreManager manager = new ScoreManager();
        manager.onEvent(new BallTouchedPlatform());
        manager.onEvent(new GapPassed(0f)); // resets to 0 (touched before this pass)
        manager.onEvent(new GapPassed(-2.6f)); // no touch since -> increments

        assertEquals(1, manager.getComboChain());
    }

    @Test
    void resetClearsState() {
        ScoreManager manager = new ScoreManager();
        manager.onEvent(new GapPassed(0f));
        manager.onEvent(new GapPassed(-2.6f));
        manager.reset();

        assertEquals(0, manager.getComboChain());
    }
}
