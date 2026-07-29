package com.foukas.dropbox2d.events;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.foukas.dropbox2d.physics.DestructiblePlatform;

/** Box2D's own contact callback, wrapped to dispatch typed events instead of
 * mutating game state directly. Fixture user-data tagging convention:
 * "ball", "platform" (solid, permanent), "weakPlatform" (breakable),
 * "powerUp" (sensor pickup). Every event here is dispatched once, from the
 * contact that triggered it -- nothing tracks ongoing "is touching" state,
 * so destroying a body afterward (which this class never does directly --
 * see DestructiblePlatform's class comment) never leaves anything stale. */
public class ContactDispatcher implements ContactListener {
    private final GameEventBus bus;

    public ContactDispatcher(GameEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture a = contact.getFixtureA();
        Fixture b = contact.getFixtureB();

        if (isBallVs(a, b, "platform") || isBallVs(a, b, "weakPlatform")) {
            bus.dispatch(new BallTouchedPlatform());
        }

        Body powerUpBody = ballVsTaggedBody(a, b, "powerUp");
        if (powerUpBody != null) {
            bus.dispatch(new PowerUpCollected(powerUpBody));
        }
    }

    @Override
    public void endContact(Contact contact) {
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
        Fixture a = contact.getFixtureA();
        Fixture b = contact.getFixtureB();

        Body weakPlatformBody = ballVsTaggedBody(a, b, "weakPlatform");
        if (weakPlatformBody != null) {
            float magnitude = maxNormalImpulse(impulse);
            if (DestructiblePlatform.shouldBreak(magnitude)) {
                bus.dispatch(new PlatformDestroyed(weakPlatformBody, weakPlatformBody.getPosition().x, weakPlatformBody.getPosition().y));
            }
        }
    }

    private float maxNormalImpulse(ContactImpulse impulse) {
        float max = 0f;
        for (float value : impulse.getNormalImpulses()) {
            max = Math.max(max, value);
        }
        return max;
    }

    private boolean isBallVs(Fixture a, Fixture b, String tag) {
        Object ua = a.getUserData();
        Object ub = b.getUserData();
        return ("ball".equals(ua) && tag.equals(ub)) || ("ball".equals(ub) && tag.equals(ua));
    }

    /** Returns the fixture body tagged with `tag` if this contact is
     * ball-vs-tag, otherwise null. */
    private Body ballVsTaggedBody(Fixture a, Fixture b, String tag) {
        Object ua = a.getUserData();
        Object ub = b.getUserData();
        if ("ball".equals(ua) && tag.equals(ub)) {
            return b.getBody();
        }
        if ("ball".equals(ub) && tag.equals(ua)) {
            return a.getBody();
        }
        return null;
    }
}
