package com.foukas.dropbox2d.fx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Simple expanding/fading dots for combo and platform-break feedback.
 * Not Box2D bodies -- just position/velocity floats, since these are pure
 * visual flourish with no physics interaction (unlike DebrisManager's
 * fragments, which are real Box2D bodies because they need to bounce and
 * settle on the world). */
public class ParticleSystem {
    private static final float LIFETIME = 0.5f;
    private static final float SPEED = 3f;

    private final List<Particle> particles = new ArrayList<>();

    public void burst(float x, float y, Color color, int count) {
        for (int i = 0; i < count; i++) {
            float angle = MathUtils.random(0f, MathUtils.PI2);
            float speed = MathUtils.random(SPEED * 0.5f, SPEED);
            particles.add(new Particle(x, y, MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed, color));
        }
    }

    public void update(float delta) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle particle = it.next();
            particle.age += delta;
            if (particle.age >= LIFETIME) {
                it.remove();
                continue;
            }
            particle.x += particle.vx * delta;
            particle.y += particle.vy * delta;
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }

    public int count() {
        return particles.size();
    }

    public void clear() {
        particles.clear();
    }

    public static class Particle {
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float age;
        public final Color color;

        Particle(float x, float y, float vx, float vy, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }

        /** 1 when freshly spawned, decaying to 0 at end of life -- use as
         * an alpha multiplier when rendering for a fade-out. */
        public float lifeFraction() {
            return 1f - Math.min(age / LIFETIME, 1f);
        }
    }
}
