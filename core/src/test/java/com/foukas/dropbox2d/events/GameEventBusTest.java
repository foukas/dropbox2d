package com.foukas.dropbox2d.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEventBusTest {

    @Test
    void dispatchesToAllSubscribers() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent> receivedA = new ArrayList<>();
        List<GameEvent> receivedB = new ArrayList<>();
        bus.subscribe(receivedA::add);
        bus.subscribe(receivedB::add);

        GameEvent event = new BallOffTop();
        bus.dispatch(event);

        assertEquals(1, receivedA.size());
        assertEquals(1, receivedB.size());
        assertTrue(receivedA.get(0) instanceof BallOffTop);
    }

    @Test
    void unsubscribeAllStopsFurtherDelivery() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent> received = new ArrayList<>();
        bus.subscribe(received::add);

        bus.unsubscribeAll();
        bus.dispatch(new BallOffTop());

        assertEquals(0, received.size());
    }

    @Test
    void listenerOnlyReceivesDispatchedEventType() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent> received = new ArrayList<>();
        bus.subscribe(received::add);

        bus.dispatch(new GapPassed(12.5f));

        assertEquals(1, received.size());
        assertTrue(received.get(0) instanceof GapPassed);
        assertEquals(12.5f, ((GapPassed) received.get(0)).rowY(), 0.001f);
    }
}
