package com.foukas.dropbox2d.fx;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleSystemTest {

    @Test
    void burstCreatesTheRequestedCount() {
        ParticleSystem system = new ParticleSystem();
        system.burst(0f, 0f, Color.WHITE, 6);
        assertEquals(6, system.count());
    }

    @Test
    void particlesMoveOverTime() {
        ParticleSystem system = new ParticleSystem();
        system.burst(0f, 0f, Color.WHITE, 1);
        ParticleSystem.Particle particle = system.getParticles().get(0);
        float startX = particle.x;
        float startY = particle.y;

        system.update(0.1f);

        assertTrue(particle.x != startX || particle.y != startY, "a particle with nonzero velocity should have moved");
    }

    @Test
    void particlesDespawnAfterTheirLifetime() {
        ParticleSystem system = new ParticleSystem();
        system.burst(0f, 0f, Color.WHITE, 5);

        system.update(1f); // well past the 0.5s lifetime

        assertEquals(0, system.count());
    }

    @Test
    void lifeFractionDecaysToZeroAtEndOfLife() {
        ParticleSystem system = new ParticleSystem();
        system.burst(0f, 0f, Color.WHITE, 1);
        ParticleSystem.Particle particle = system.getParticles().get(0);

        assertEquals(1f, particle.lifeFraction(), 0.001f);

        particle.age = 0.5f; // exactly at lifetime
        assertEquals(0f, particle.lifeFraction(), 0.001f);
    }

    @Test
    void clearRemovesAllParticlesImmediately() {
        ParticleSystem system = new ParticleSystem();
        system.burst(0f, 0f, Color.WHITE, 4);

        system.clear();

        assertEquals(0, system.count());
    }
}
