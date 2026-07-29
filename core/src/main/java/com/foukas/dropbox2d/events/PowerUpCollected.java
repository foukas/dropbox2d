package com.foukas.dropbox2d.events;

import com.badlogic.gdx.physics.box2d.Body;

/** Dispatched on beginContact between the ball and a power-up pickup
 * sensor. `type` is the pickup's tag (e.g. "wreckingBall"), matching a key
 * registered in PowerUpManager. Same queue-don't-destroy rule as
 * PlatformDestroyed applies. */
public record PowerUpCollected(Body body, String type) implements GameEvent {
}
