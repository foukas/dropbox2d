package com.foukas.dropbox2d.events;

import java.util.ArrayList;
import java.util.List;

/** Minimal pub-sub bus. No priority, no async dispatch -- listeners are
 * called synchronously in subscription order, which is all this game needs. */
public class GameEventBus {
    private final List<GameEventListener> listeners = new ArrayList<>();

    public void subscribe(GameEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribeAll() {
        listeners.clear();
    }

    public void dispatch(GameEvent event) {
        for (GameEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
