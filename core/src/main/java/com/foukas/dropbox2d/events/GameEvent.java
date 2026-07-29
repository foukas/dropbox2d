package com.foukas.dropbox2d.events;

/**
 * Typed events dispatched during play. Approach C adds new event types here
 * (PlatformDestroyed, PowerUpCollected) without touching the collision code
 * that produces the events below -- that's the whole point of routing
 * everything through this sealed interface instead of direct field mutation.
 */
public sealed interface GameEvent permits GapPassed, BallOffTop, BallTouchedPlatform {
}
