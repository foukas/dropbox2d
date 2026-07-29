package com.foukas.dropbox2d.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Debris fragments spawned when a weak platform breaks. Despawns by age or
 * once scrolled off-screen, whichever comes first -- closes the failure
 * mode the eng review flagged: unbounded debris growth over a long
 * session is a real perf/memory leak, not a hypothetical one.
 */
public class DebrisManager {
    private static final float DEBRIS_LIFETIME = 3f;
    private static final float DEBRIS_SIZE = 0.15f;
    private static final int DEBRIS_PER_BREAK = 4;
    private static final float DEBRIS_SPEED = 4f;

    private final World world;
    private final List<Fragment> fragments = new ArrayList<>();

    public DebrisManager(World world) {
        this.world = world;
    }

    public void spawnDebris(float x, float y) {
        for (int i = 0; i < DEBRIS_PER_BREAK; i++) {
            BodyDef bodyDef = new BodyDef();
            bodyDef.type = BodyDef.BodyType.DynamicBody;
            bodyDef.position.set(x + MathUtils.random(-0.3f, 0.3f), y);
            Body body = world.createBody(bodyDef);

            PolygonShape shape = new PolygonShape();
            shape.setAsBox(DEBRIS_SIZE / 2f, DEBRIS_SIZE / 2f);
            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape = shape;
            fixtureDef.density = 0.5f;
            fixtureDef.friction = 0.4f;
            fixtureDef.restitution = 0.3f;
            Fixture fixture = body.createFixture(fixtureDef);
            fixture.setUserData("debris");
            shape.dispose();

            float angle = MathUtils.random(0f, MathUtils.PI2);
            body.setLinearVelocity(MathUtils.cos(angle) * DEBRIS_SPEED, MathUtils.sin(angle) * DEBRIS_SPEED);
            body.setAngularVelocity(MathUtils.random(-10f, 10f));

            fragments.add(new Fragment(body));
        }
    }

    /** Advances fragment ages and despawns anything past its lifetime or
     * above recycleAboveY (mirrors the platform-row recycling rule). */
    public void update(float delta, float recycleAboveY) {
        Iterator<Fragment> it = fragments.iterator();
        while (it.hasNext()) {
            Fragment fragment = it.next();
            fragment.age += delta;
            if (fragment.age >= DEBRIS_LIFETIME || fragment.body.getPosition().y > recycleAboveY) {
                world.destroyBody(fragment.body);
                it.remove();
            }
        }
    }

    public List<Body> getBodies() {
        List<Body> bodies = new ArrayList<>(fragments.size());
        for (Fragment fragment : fragments) {
            bodies.add(fragment.body);
        }
        return bodies;
    }

    public int count() {
        return fragments.size();
    }

    public void clear() {
        for (Fragment fragment : fragments) {
            world.destroyBody(fragment.body);
        }
        fragments.clear();
    }

    private static class Fragment {
        final Body body;
        float age;

        Fragment(Body body) {
            this.body = body;
        }
    }
}
