package com.foukas.dropbox2d;

import com.foukas.dropbox2d.events.BallOffTop;
import com.foukas.dropbox2d.events.GameEvent;
import com.foukas.dropbox2d.events.GameEventListener;

public class GameOverController implements GameEventListener {
    private boolean gameOver;

    @Override
    public void onEvent(GameEvent event) {
        if (event instanceof BallOffTop) {
            gameOver = true;
        }
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void reset() {
        gameOver = false;
    }
}
