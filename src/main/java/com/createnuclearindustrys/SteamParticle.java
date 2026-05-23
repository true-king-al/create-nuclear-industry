package com.createnuclearindustrys;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Rising steam cloud particle — light cyan, slow upward drift, long lifetime.
 * Uses the vanilla cloud sprite (white puff) tinted light cyan.
 */
public class SteamParticle extends TextureSheetParticle {

    private SteamParticle(ClientLevel level, double x, double y, double z,
                          double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);

        this.pickSprite(sprites);

        // Light cyan tint — (0.91, 0.97, 1.0) in RGB, looks like wisps of steam
        this.rCol = 0.91f;
        this.gCol = 0.97f;
        this.bCol = 1.00f;
        this.alpha = 0.55f;

        // Size: medium puff, slight randomness
        this.quadSize = 0.35f + this.random.nextFloat() * 0.35f;

        // Lifetime: 3–6 seconds — "long lasting"
        this.lifetime = 60 + this.random.nextInt(60);

        // Initial velocity from the spawner, plus a guaranteed upward push
        this.xd = vx + (this.random.nextDouble() - 0.5) * 0.02;
        this.yd = Math.max(vy, 0.04) + this.random.nextDouble() * 0.04;
        this.zd = vz + (this.random.nextDouble() - 0.5) * 0.02;

        // Negative gravity → particle slowly rises instead of falling
        this.gravity = -0.04f;
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

        // Move the particle
        this.move(this.xd, this.yd, this.zd);

        // Dampen velocity each tick so it slows as it rises
        this.xd *= 0.94;
        this.yd *= 0.96;
        this.zd *= 0.94;

        // Fade out over the last quarter of its life
        float fadeStart = this.lifetime * 0.75f;
        if (this.age > fadeStart) {
            this.alpha = 0.55f * (1f - (this.age - fadeStart) / (this.lifetime - fadeStart));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SteamParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
