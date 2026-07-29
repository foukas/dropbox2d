package com.foukas.dropbox2d.events;

/** Dispatched when the ball's fall crosses below a platform row's Y level. */
public record GapPassed(float rowY) implements GameEvent {
}
