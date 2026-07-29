package com.foukas.dropbox2d.events;

import com.badlogic.gdx.physics.box2d.Body;

/** Dispatched from ContactDispatcher's preSolve when a weak platform's
 * break-momentum threshold is exceeded (before the collision solver runs,
 * so the contact can be disabled and the ball passes through with no
 * bounce). Carries the body reference so the subscriber can queue it for
 * destruction -- Box2D forbids destroying bodies from inside a collision
 * callback, so this only ever queues, never destroys directly. Width lets
 * the debris spawn scale with how big a chunk just broke. */
public record PlatformDestroyed(Body body, float x, float y, float width) implements GameEvent {
}
