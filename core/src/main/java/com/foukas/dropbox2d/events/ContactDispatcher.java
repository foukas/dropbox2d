package com.foukas.dropbox2d.events;

import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;

/** Box2D's own contact callback, wrapped to dispatch typed events instead of
 * mutating game state directly. Approach C's destructible-platform break
 * detection and power-up pickup detection extend this class -- the fixture
 * user-data tagging convention ("ball", "platform", and later "weakPlatform"/
 * "powerUp") is what makes that a pure addition, not a rewrite. */
public class ContactDispatcher implements ContactListener {
    private final GameEventBus bus;

    public ContactDispatcher(GameEventBus bus) {
        this.bus = bus;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture a = contact.getFixtureA();
        Fixture b = contact.getFixtureB();
        if (isBallPlatformContact(a, b)) {
            bus.dispatch(new BallTouchedPlatform());
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
    }

    private boolean isBallPlatformContact(Fixture a, Fixture b) {
        Object ua = a.getUserData();
        Object ub = b.getUserData();
        return ("ball".equals(ua) && "platform".equals(ub)) || ("ball".equals(ub) && "platform".equals(ua));
    }
}
