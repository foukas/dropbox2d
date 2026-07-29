package com.foukas.dropbox2d.events;

/** Dispatched when the ball's screen-relative position exceeds the top kill-zone. */
public record BallOffTop() implements GameEvent {
}
