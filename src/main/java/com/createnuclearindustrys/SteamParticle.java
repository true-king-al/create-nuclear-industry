package com.createnuclearindustrys;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Rising steam cloud particle.
 *
 * Lifetime : 60–90 seconds (1200–1800 ticks)
 * Motion   : strong upward drift that is maintained throughout life
 *            (gravity is negative so the particle always wants to rise;
 *            horizontal spread slows over time for a realistic plume)
 * Colour   : light cyan tint (0.88 / 0.96 / 1.0 RGB) applied over a
 *            white soft-puff sprite
 * Fade     : slow fade-in over first 0.5 s, gentle fade-out over last 5 s
 */
public class SteamParticle extends TextureSheetParticle {

    private SteamParticle(ClientLevel level, double x, double y, double z,
                          double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.setSprite(sprites.get(this.random)); // pick the single steam sprite

        // Light cyan tint over the white puff texture
        this.rCol = 0.88f;
        this.gCol = 0.96f;
        this.bCol = 1.00f;
        this.alpha = 0.0f; // start transparent, fade in

        // Large puff — scales with random variation
        this.quadSize = 0.55f + this.random.nextFloat() * 0.45f;

        // 60–90 second lifetime in ticks
        this.lifetime = 1200 + this.random.nextInt(600);

        // Initial upward velocity — used with a sustained lift force
        this.xd = vx + (this.random.nextDouble() - 0.5) * 0.04;
        this.yd = Math.max(vy, 0.03) + this.random.nextDouble() * 0.03;
        this.zd = vz + (this.random.nextDouble() - 0.5) * 0.04;

        // Negative gravity → the particle continuously rises
        // At -0.08 it applies  +0.08 * 0.04 = +0.0032 yd per tick (gentle sustained lift)
        this.gravity = -0.08f;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // Apply gravity (negative → adds upward force each tick)
        this.yd -= 0.04 * this.gravity; // 0.04 * 0.08 = +0.0032 upward each tick

        // Move
        this.move(this.xd, this.yd, this.zd);

        // Horizontal drift slows down — particles converge into a rising column
        this.xd *= 0.96;
        this.zd *= 0.96;
        // Vertical velocity has gentle damping but gravity keeps it rising
        this.yd *= 0.99;

        // Grow slightly as it rises (steam expands)
        if (this.age < this.lifetime * 0.5f) {
            this.quadSize += 0.003f;
        }

        // Alpha: fade in over first 10 ticks, hold, fade out over last 100 ticks
        if (this.age < 10) {
            this.alpha = this.age / 10.0f * 0.5f;
        } else if (this.age > this.lifetime - 100) {
            float fadeProgress = (this.age - (this.lifetime - 100)) / 100.0f;
            this.alpha = 0.5f * (1.0f - fadeProgress);
        } else {
            this.alpha = 0.5f;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
