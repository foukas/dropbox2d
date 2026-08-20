package com.foukas.dropbox2d.events;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.foukas.dropbox2d.physics.DestructiblePlatform;

import java.util.function.BooleanSupplier;

/** Box2D's own contact callback, wrapped to dispatch typed events instead of
 * mutating game state directly. Fixture user-data tagging convention:
 * "ball", "platform" (solid, permanent -- also breakable while rampage is
 * active, rampage design doc plan-eng-review 2026-08-06), "weakPlatform"
 * (always breakable), "movingPlatform" (solid, kinematic, patrols -- also
 * breakable while rampage is active), "powerUp:&lt;type&gt;" (sensor
 * pickup, colon-delimited type tag). Every event here is dispatched once,
 * from the contact that triggered it -- nothing tracks ongoing "is
 * touching" state, so destroying a body afterward (which this class never
 * does directly -- see DestructiblePlatform's class comment) never leaves
 * anything stale. */
public class ContactDispatcher implements ContactListener {
    private static final String POWERUP_PREFIX = "powerUp:";

    private final GameEventBus bus;
    // Deliberately a BooleanSupplier, not a hard dependency on
    // PowerUpManager's concrete type -- keeps this events-package class
    // decoupled from the powerups package (rampage design doc Constraints).
    // GameplayScreen wires it to the manager's own exclusivity guarantee:
    // () -> "rampage".equals(powerUpManager.getActiveType()).
    private final BooleanSupplier rampageActive;

    public ContactDispatcher(GameEventBus bus, BooleanSupplier rampageActive) {
        this.bus = bus;
        this.rampageActive = rampageActive;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture a = contact.getFixtureA();
        Fixture b = contact.getFixtureB();

        // "movingPlatform" must fire BallTouchedPlatform exactly like
        // "platform"/"weakPlatform" do, or landing on a moving platform
        // silently stops resetting the combo chain (plan-eng-review Test
        // Review Iron Rule -- guarded by ContactDispatcherTest, this
        // class's first-ever unit test).
        if (isBallVs(a, b, "platform") || isBallVs(a, b, "weakPlatform") || isBallVs(a, b, "movingPlatform")) {
            bus.dispatch(new BallTouchedPlatform());
        }

        String powerUpTag = ballVsTaggedPrefix(a, b, POWERUP_PREFIX);
        if (powerUpTag != null) {
            Body pickupBody = ballVsTaggedBody(a, b, powerUpTag);
            bus.dispatch(new PowerUpCollected(pickupBody, powerUpTag.substring(POWERUP_PREFIX.length())));
        }
    }

    @Override
    public void endContact(Contact contact) {
    }

    /** Decided BEFORE the collision solver runs, not after: if a weak
     * platform should break, disabling the contact here means Box2D skips
     * collision response entirely for this step -- the ball passes
     * through with no bounce, instead of bouncing (like postSolve-based
     * detection did) and only breaking a step late. See
     * DestructiblePlatform's class comment for why momentum was chosen
     * over resolved impulse. */
    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
        Fixture a = contact.getFixtureA();
        Fixture b = contact.getFixtureB();

        // "weakPlatform" is always break-eligible. "platform"/"movingPlatform"
        // only become break-eligible while rampage is active -- the same
        // momentum math applies regardless of tag (rampage design doc
        // Constraints: "no new physics re-derivation needed"), only the
        // set of eligible tags changes.
        Body breakableBody = ballVsTaggedBody(a, b, "weakPlatform");
        if (breakableBody == null && rampageActive.getAsBoolean()) {
            breakableBody = ballVsTaggedBody(a, b, "platform");
            if (breakableBody == null) {
                breakableBody = ballVsTaggedBody(a, b, "movingPlatform");
            }
        }
        if (breakableBody == null) {
            return;
        }

        Body ballBody = "ball".equals(a.getUserData()) ? a.getBody() : b.getBody();
        float momentum = ballBody.getMass() * ballBody.getLinearVelocity().len();

        if (DestructiblePlatform.shouldBreak(momentum)) {
            contact.setEnabled(false);
            float width = platformWidth(breakableBody);
            bus.dispatch(new PlatformDestroyed(breakableBody, breakableBody.getPosition().x, breakableBody.getPosition().y, width));
        }
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }

    private float platformWidth(Body platformBody) {
        PolygonShape shape = (PolygonShape) platformBody.getFixtureList().get(0).getShape();
        com.badlogic.gdx.math.Vector2 v = new com.badlogic.gdx.math.Vector2();
        shape.getVertex(0, v);
        return Math.abs(v.x) * 2f;
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

    /** Returns the non-ball fixture's userData string if this is a
     * ball-vs-tagged-prefix contact (e.g. "powerUp:wreckingBall" matches
     * prefix "powerUp:"), otherwise null. */
    private String ballVsTaggedPrefix(Fixture a, Fixture b, String prefix) {
        Object ua = a.getUserData();
        Object ub = b.getUserData();
        if ("ball".equals(ua) && ub instanceof String && ((String) ub).startsWith(prefix)) {
            return (String) ub;
        }
        if ("ball".equals(ub) && ua instanceof String && ((String) ua).startsWith(prefix)) {
            return (String) ua;
        }
        return null;
    }
}
