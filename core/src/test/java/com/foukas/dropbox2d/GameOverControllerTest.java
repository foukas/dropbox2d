package com.foukas.dropbox2d;

import com.foukas.dropbox2d.events.BallOffTop;
import com.foukas.dropbox2d.events.GapPassed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameOverControllerTest {

    @Test
    void startsNotGameOver() {
        GameOverController controller = new GameOverController();
        assertFalse(controller.isGameOver());
    }

    @Test
    void ballOffTopTriggersGameOver() {
        GameOverController controller = new GameOverController();
        controller.onEvent(new BallOffTop());
        assertTrue(controller.isGameOver());
    }

    @Test
    void unrelatedEventsDoNotTriggerGameOver() {
        GameOverController controller = new GameOverController();
        controller.onEvent(new GapPassed(0f));
        assertFalse(controller.isGameOver());
    }

    @Test
    void resetClearsGameOverState() {
        GameOverController controller = new GameOverController();
        controller.onEvent(new BallOffTop());
        controller.reset();
        assertFalse(controller.isGameOver());
    }
}
