package com.foukas.dropbox2d.events;

import com.badlogic.gdx.physics.box2d.Body;

/** Dispatched from ContactDispatcher's postSolve when a weak platform's
 * break-impulse threshold is exceeded. Carries the body reference so the
 * subscriber can queue it for destruction -- Box2D forbids destroying
 * bodies from inside a collision callback, so this only ever queues,
 * never destroys directly. */
public record PlatformDestroyed(Body body, float x, float y) implements GameEvent {
}
