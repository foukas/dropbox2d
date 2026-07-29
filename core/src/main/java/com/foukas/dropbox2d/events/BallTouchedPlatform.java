package com.foukas.dropbox2d.events;

/** Dispatched from the Box2D ContactListener when the ball contacts any platform fixture. */
public record BallTouchedPlatform() implements GameEvent {
}
