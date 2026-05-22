package com.createnuclearindustrys;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.*;

@EventBusSubscriber(modid = CreateNuclearIndustrys.MODID, value = Dist.CLIENT)
public class ClientRadiationRenderer {

    private static final Map<UUID, ClientParticle> particles = new HashMap<>();
    private static final RandomSource CLIENT_RNG = RandomSource.create();

    // ── Block color: tint shifts white → orange as heat_level increases ─────────

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            float t = state.getValue(UraniumFuelRod.HEAT_LEVEL) / 15f;
            int r = 255;
            int g = (int)(255 - t * 100); // 255 → 155
            int b = (int)(255 * (1f - t)); // 255 → 0
            return (0xFF << 24) | (r << 16) | (g << 8) | b;
        }, CreateNuclearIndustrys.URANIUM_FUEL_ROD.get());
    }

    // ── Packet registration (mod bus, auto-detected via IModBusEvent) ──────────

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            RadiationBirthPacket.TYPE,
            RadiationBirthPacket.STREAM_CODEC,
            (pkt, ctx) -> ctx.enqueueWork(() -> handleBirth(pkt))
        );
        event.registrar("1").playToClient(
            HeatPipeConnectionPacket.TYPE,
            HeatPipeConnectionPacket.STREAM_CODEC,
            (pkt, ctx) -> ctx.enqueueWork(() -> handlePipeConnection(pkt))
        );
    }

    public static void handleBirth(RadiationBirthPacket pkt) {
        particles.put(pkt.id(), new ClientParticle(pkt));
    }

    public static void handlePipeConnection(HeatPipeConnectionPacket pkt) {
        if (pkt.connected()) {
            boolean exists = connectedPairs.stream().anyMatch(p ->
                (p[0] == pkt.posA() && p[1] == pkt.posB()) ||
                (p[0] == pkt.posB() && p[1] == pkt.posA()));
            if (!exists) connectedPairs.add(new long[]{pkt.posA(), pkt.posB()});
        } else {
            connectedPairs.removeIf(p ->
                (p[0] == pkt.posA() && p[1] == pkt.posB()) ||
                (p[0] == pkt.posB() && p[1] == pkt.posA()));
        }
    }

    private static final List<long[]> connectedPairs = new ArrayList<>();

    // ── Client tick: step physics ─────────────────────────────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { particles.clear(); return; }

        List<UUID> dead = new ArrayList<>();
        for (ClientParticle p : particles.values()) {
            if (--p.ticksLeft <= 0) { dead.add(p.id); continue; }
            p.prevPos = p.pos;
            step(p, level);
        }
        dead.forEach(particles::remove);
    }

    // ── Render: draw colored lines ────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (particles.isEmpty()) return;

        Camera camera  = event.getCamera();
        Vec3   camPos  = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        PoseStack.Pose mat = pose.last();

        MultiBufferSource.BufferSource buf =
            Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());

        for (ClientParticle p : particles.values()) {
            if (p.prevPos == null || p.prevPos.equals(p.pos)) continue;

            float x1 = (float)(p.prevPos.x - camPos.x);
            float y1 = (float)(p.prevPos.y - camPos.y);
            float z1 = (float)(p.prevPos.z - camPos.z);
            float x2 = (float)(p.pos.x - camPos.x);
            float y2 = (float)(p.pos.y - camPos.y);
            float z2 = (float)(p.pos.z - camPos.z);

            float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
            float len = (float)Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (len == 0) continue;
            float nx = dx / len, ny = dy / len, nz = dz / len;

            lines.addVertex(mat, x1, y1, z1).setColor(p.r, p.g, p.b, 1f).setNormal(mat, nx, ny, nz);
            lines.addVertex(mat, x2, y2, z2).setColor(p.r, p.g, p.b, 1f).setNormal(mat, nx, ny, nz);
        }

        buf.endBatch(RenderType.lines());

        // Render copper heat pipes as billboard quads (thick, minimal sag)
        if (!connectedPairs.isEmpty()) {
            VertexConsumer quads = buf.getBuffer(RenderType.debugQuads());
            float halfThick = 0.04f;
            for (long[] pair : connectedPairs) {
                BlockPos posA = BlockPos.of(pair[0]);
                BlockPos posB = BlockPos.of(pair[1]);
                Vec3 a = Vec3.atCenterOf(posA).subtract(camPos);
                Vec3 b = Vec3.atCenterOf(posB).subtract(camPos);
                double dist = a.distanceTo(b);
                double sag = dist * 0.04;
                int segments = Math.max(8, (int)(dist * 4));
                Vec3 prev = a;
                for (int i = 1; i <= segments; i++) {
                    double t = i / (double) segments;
                    Vec3 cur = new Vec3(
                        a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t - sag * 4 * t * (1 - t),
                        a.z + (b.z - a.z) * t
                    );
                    Vec3 segDir = cur.subtract(prev);
                    double sLen = segDir.length();
                    if (sLen < 1e-9) { prev = cur; continue; }
                    segDir = segDir.scale(1.0 / sLen);

                    // Billboard: right = segDir × toCam (camera is at origin in camera-relative space)
                    Vec3 mid = new Vec3((prev.x+cur.x)*0.5, (prev.y+cur.y)*0.5, (prev.z+cur.z)*0.5);
                    double mLen = mid.length();
                    Vec3 toCam = mLen < 1e-6 ? new Vec3(0, 1, 0) : mid.scale(-1.0 / mLen);
                    Vec3 right = segDir.cross(toCam);
                    double rLen = right.length();
                    if (rLen < 1e-9) { prev = cur; continue; }
                    right = right.scale(halfThick / rLen);

                    float p1x = (float)(prev.x - right.x), p1y = (float)(prev.y - right.y), p1z = (float)(prev.z - right.z);
                    float p2x = (float)(prev.x + right.x), p2y = (float)(prev.y + right.y), p2z = (float)(prev.z + right.z);
                    float c1x = (float)(cur.x  - right.x), c1y = (float)(cur.y  - right.y), c1z = (float)(cur.z  - right.z);
                    float c2x = (float)(cur.x  + right.x), c2y = (float)(cur.y  + right.y), c2z = (float)(cur.z  + right.z);

                    quads.addVertex(mat, p1x, p1y, p1z).setColor(0.72f, 0.45f, 0.20f, 1f);
                    quads.addVertex(mat, p2x, p2y, p2z).setColor(0.72f, 0.45f, 0.20f, 1f);
                    quads.addVertex(mat, c2x, c2y, c2z).setColor(0.72f, 0.45f, 0.20f, 1f);
                    quads.addVertex(mat, c1x, c1y, c1z).setColor(0.72f, 0.45f, 0.20f, 1f);

                    prev = cur;
                }
            }
            buf.endBatch(RenderType.debugQuads());
        }
    }

    // ── Physics (mirrors server-side RadiationManager.step exactly) ──────────

    private static void step(ClientParticle p, ClientLevel level) {
        Vec3 next = p.pos.add(p.vel);
        if (!level.isLoaded(BlockPos.containing(next))) return;

        if (BlockPos.containing(p.pos).equals(p.source)) {
            p.pos = next;
            return;
        }

        if (!isPointInSolid(level, next)) {
            p.pos = next;
            return;
        }

        BlockPos hitBlock = BlockPos.containing(next);

        // Mirror server absorption so client particles fade at the same rate
        if (CLIENT_RNG.nextFloat() < getAbsorption(level.getBlockState(hitBlock))) {
            p.ticksLeft = 0;
            return;
        }

        double vx = p.vel.x, vy = p.vel.y, vz = p.vel.z;
        if (isPointInSolid(level, new Vec3(p.pos.x + p.vel.x, p.pos.y,            p.pos.z           ))) vx = -vx;
        if (isPointInSolid(level, new Vec3(p.pos.x,            p.pos.y + p.vel.y, p.pos.z           ))) vy = -vy;
        if (isPointInSolid(level, new Vec3(p.pos.x,            p.pos.y,            p.pos.z + p.vel.z))) vz = -vz;
        if (vx == p.vel.x && vy == p.vel.y && vz == p.vel.z) { vx = -vx; vy = -vy; vz = -vz; }
        p.vel = new Vec3(vx, vy, vz);
    }

    private static float getAbsorption(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.IRON_BLOCK || b instanceof UraniumFuelRod) return 0f;
        if (b == Blocks.GOLD_BLOCK || b == Blocks.DIAMOND_BLOCK || b == Blocks.NETHERITE_BLOCK) return 0.05f;
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return 0.4f;
        if (b == Blocks.STONE || b == Blocks.COBBLESTONE || b == Blocks.DEEPSLATE
                || b == Blocks.STONE_BRICKS || b == Blocks.BRICKS || b == Blocks.SANDSTONE) return 0.15f;
        if (b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.GRAVEL
                || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK) return 0.25f;
        return 0.1f;
    }

    static boolean isPointInSolid(ClientLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        if (!level.isLoaded(pos)) return true;
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double lx = point.x - pos.getX();
        double ly = point.y - pos.getY();
        double lz = point.z - pos.getZ();
        for (AABB aabb : shape.toAabbs()) {
            if (lx >= aabb.minX && lx <= aabb.maxX &&
                ly >= aabb.minY && ly <= aabb.maxY &&
                lz >= aabb.minZ && lz <= aabb.maxZ) return true;
        }
        return false;
    }

    // ── Client-side particle data ─────────────────────────────────────────────

    static class ClientParticle {
        final UUID id;
        Vec3 prevPos, pos, vel;
        final float r, g, b;
        final float energy;
        int ticksLeft;
        final BlockPos source;

        ClientParticle(RadiationBirthPacket pkt) {
            this.id        = pkt.id();
            this.pos       = new Vec3(pkt.px(), pkt.py(), pkt.pz());
            this.prevPos   = this.pos;
            this.vel       = new Vec3(pkt.vx(), pkt.vy(), pkt.vz());
            this.r         = pkt.r();
            this.g         = pkt.g();
            this.b         = pkt.b();
            this.energy    = pkt.energy();
            this.ticksLeft = pkt.lifetime();
            this.source    = BlockPos.of(pkt.sourcePos());
        }
    }
}
