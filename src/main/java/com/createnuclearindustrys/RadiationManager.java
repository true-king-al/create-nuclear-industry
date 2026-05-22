package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class RadiationManager extends SavedData {
    private static final String DATA_ID = CreateNuclearIndustrys.MODID + "_radiation";
    private static final int EMIT_INTERVAL = 10;
    private static final float MELTDOWN_TEMP = 1000f;

    private final Map<UUID, RadiationParticle> particles = new LinkedHashMap<>();
    private final Set<BlockPos> rods = new HashSet<>();
    private final Map<BlockPos, Float> rodHeat = new HashMap<>();
    private final Map<BlockPos, Set<BlockPos>> pipeConnections = new HashMap<>();
    private final RandomSource rng = RandomSource.create();
    private final List<RadiationParticle> pendingBroadcast = new ArrayList<>();

    public static RadiationManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(RadiationManager::new, RadiationManager::load),
            DATA_ID
        );
    }

    public void registerRod(BlockPos pos) {
        rods.add(pos.immutable());
        rodHeat.put(pos.immutable(), 0f);
        setDirty();
    }

    public void removeRod(BlockPos pos, ServerLevel level) {
        rods.remove(pos);
        rodHeat.remove(pos);
        // Clean up all pipe connections involving this rod
        Set<BlockPos> peers = pipeConnections.remove(pos);
        if (peers != null) {
            for (BlockPos peer : peers) {
                Set<BlockPos> peerSet = pipeConnections.get(peer);
                if (peerSet != null) {
                    peerSet.remove(pos);
                    if (peerSet.isEmpty()) pipeConnections.remove(peer);
                }
                PacketDistributor.sendToPlayersInDimension(level,
                    new HeatPipeConnectionPacket(pos.asLong(), peer.asLong(), false));
            }
        }
        setDirty();
    }

    public boolean hasPipeConnection(BlockPos a, BlockPos b) {
        Set<BlockPos> peers = pipeConnections.get(a);
        return peers != null && peers.contains(b);
    }

    public void addPipeConnection(BlockPos a, BlockPos b, ServerLevel level) {
        pipeConnections.computeIfAbsent(a.immutable(), k -> new HashSet<>()).add(b.immutable());
        pipeConnections.computeIfAbsent(b.immutable(), k -> new HashSet<>()).add(a.immutable());
        PacketDistributor.sendToPlayersInDimension(level,
            new HeatPipeConnectionPacket(a.asLong(), b.asLong(), true));
        setDirty();
    }

    public int removeAllPipeConnections(BlockPos pos, ServerLevel level) {
        Set<BlockPos> peers = pipeConnections.remove(pos);
        if (peers == null) return 0;
        for (BlockPos peer : peers) {
            Set<BlockPos> peerSet = pipeConnections.get(peer);
            if (peerSet != null) {
                peerSet.remove(pos);
                if (peerSet.isEmpty()) pipeConnections.remove(peer);
            }
            PacketDistributor.sendToPlayersInDimension(level,
                new HeatPipeConnectionPacket(pos.asLong(), peer.asLong(), false));
        }
        setDirty();
        return peers.size();
    }

    public void addHeat(BlockPos pos, float amount) {
        if (!rods.contains(pos)) return;
        rodHeat.merge(pos.immutable(), amount, Float::sum);
        setDirty();
    }

    public float getHeat(BlockPos pos) {
        return rodHeat.getOrDefault(pos, 0f);
    }

    public List<RadiationParticle> drainPendingBroadcast() {
        if (pendingBroadcast.isEmpty()) return List.of();
        List<RadiationParticle> out = new ArrayList<>(pendingBroadcast);
        pendingBroadcast.clear();
        return out;
    }

    public Collection<RadiationParticle> getParticles() {
        return Collections.unmodifiableCollection(particles.values());
    }

    public void tick(ServerLevel level) {
        // Validate registered rods still exist (handles pistons, explosions, commands)
        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos rod : rods) {
            if (level.isLoaded(rod)) {
                Block b = level.getBlockState(rod).getBlock();
                if (!(b instanceof UraniumFuelRod) && !(b instanceof HeatGaugeBlock))
                    gone.add(rod);
            }
        }
        for (BlockPos rod : gone) removeRod(rod, level);

        // Dissipate heat; melt uranium rods that hit 1000°C (gauges just cap)
        List<BlockPos> melted = new ArrayList<>();
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            float heat = entry.getValue();
            if (heat >= MELTDOWN_TEMP) {
                if (level.getBlockState(entry.getKey()).getBlock() instanceof UraniumFuelRod)
                    melted.add(entry.getKey());
                else
                    entry.setValue(MELTDOWN_TEMP - 1f);
            } else if (heat > 0f) {
                entry.setValue(heat * 0.999f);
            }
        }
        for (BlockPos pos : melted) {
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
            rods.remove(pos);
            rodHeat.remove(pos);
        }
        if (!melted.isEmpty()) setDirty();

        // Vertical heat conduction: stacked fuel rods share heat with each other
        for (BlockPos pos : new ArrayList<>(rods)) {
            BlockPos above = pos.above();
            if (!rods.contains(above)) continue;
            float heatHere  = rodHeat.getOrDefault(pos, 0f);
            float heatAbove = rodHeat.getOrDefault(above, 0f);
            float transfer  = (heatHere - heatAbove) * 0.05f;
            if (Math.abs(transfer) < 0.01f) continue;
            rodHeat.put(pos, heatHere - transfer);
            rodHeat.put(above.immutable(), heatAbove + transfer);
        }

        // Pipe heat conduction: connected rods equalize heat
        Set<String> processed = new HashSet<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : new ArrayList<>(pipeConnections.entrySet())) {
            BlockPos posA = entry.getKey();
            for (BlockPos posB : entry.getValue()) {
                String key = posA.asLong() < posB.asLong()
                    ? posA.asLong() + ":" + posB.asLong()
                    : posB.asLong() + ":" + posA.asLong();
                if (!processed.add(key)) continue;
                float heatA = rodHeat.getOrDefault(posA, 0f);
                float heatB = rodHeat.getOrDefault(posB, 0f);
                float transfer = (heatA - heatB) * 0.05f;
                if (Math.abs(transfer) < 0.01f) continue;
                rodHeat.put(posA, heatA - transfer);
                rodHeat.put(posB.immutable(), heatB + transfer);
            }
        }

        // Sync heat_level block state property to clients (drives light + tint)
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            BlockPos pos = entry.getKey();
            int newLevel = Math.min(15, (int)(entry.getValue() / MELTDOWN_TEMP * 15));
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() instanceof UraniumFuelRod
                    && current.getValue(UraniumFuelRod.HEAT_LEVEL) != newLevel) {
                level.setBlock(pos, current.setValue(UraniumFuelRod.HEAT_LEVEL, newLevel), 2);
            }
        }

        // Emit one particle per uranium rod per trigger — smooth continuous stream
        for (BlockPos rod : new ArrayList<>(rods)) {
            if (!level.isLoaded(rod)) continue;
            if (!(level.getBlockState(rod).getBlock() instanceof UraniumFuelRod)) continue;
            if (rng.nextInt(EMIT_INTERVAL) != 0) continue;
            emitFromRod(rod);
        }

        List<UUID> dead = new ArrayList<>();
        for (RadiationParticle p : particles.values()) {
            if (--p.ticksLeft <= 0) { dead.add(p.id); continue; }
            step(p, level);
        }
        if (!dead.isEmpty()) { dead.forEach(particles::remove); setDirty(); }
    }

    private void emitFromRod(BlockPos pos) {
        double theta = rng.nextDouble() * Math.PI * 2;
        double phi   = Math.acos(2.0 * rng.nextDouble() - 1.0);
        double speed = 0.1 + rng.nextDouble() * 0.3;
        double vx    = speed * Math.sin(phi) * Math.cos(theta);
        double vy    = speed * Math.cos(phi);
        double vz    = speed * Math.sin(phi) * Math.sin(theta);

        float r = rng.nextFloat(), g = rng.nextFloat(), b = rng.nextFloat();
        float energy = 1.0f;
        int lifetime = 80 + rng.nextInt(120);

        RadiationParticle p = new RadiationParticle(
            UUID.randomUUID(), Vec3.atCenterOf(pos),
            new Vec3(vx, vy, vz), r, g, b, energy, lifetime, pos
        );
        particles.put(p.id, p);
        pendingBroadcast.add(p);
        setDirty();
    }

    private void step(RadiationParticle p, ServerLevel level) {
        Vec3 next = p.pos.add(p.vel);
        if (!level.isLoaded(BlockPos.containing(next))) return;

        // Free movement while still inside emitting block
        if (BlockPos.containing(p.pos).equals(p.source)) { p.pos = next; return; }

        if (!isPointInSolid(level, next)) { p.pos = next; return; }

        BlockPos hitBlock = BlockPos.containing(next);

        // Absorption: particle dies on contact based on block type
        if (rng.nextFloat() < getAbsorption(level.getBlockState(hitBlock))) {
            p.ticksLeft = 0;
            return;
        }

        // Heat transfer: only from other rods, not the particle's own source
        if (rods.contains(hitBlock) && !hitBlock.equals(p.source)) {
            addHeat(hitBlock, p.energy * 5f);
        }

        // Reflect per axis using point-in-solid test
        double vx = p.vel.x, vy = p.vel.y, vz = p.vel.z;
        if (isPointInSolid(level, new Vec3(p.pos.x + p.vel.x, p.pos.y,            p.pos.z           ))) vx = -vx;
        if (isPointInSolid(level, new Vec3(p.pos.x,            p.pos.y + p.vel.y, p.pos.z           ))) vy = -vy;
        if (isPointInSolid(level, new Vec3(p.pos.x,            p.pos.y,            p.pos.z + p.vel.z))) vz = -vz;
        if (vx == p.vel.x && vy == p.vel.y && vz == p.vel.z) { vx = -vx; vy = -vy; vz = -vz; }
        p.vel = new Vec3(vx, vy, vz);
    }

    static float getAbsorption(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof BoronControlRod) return 0.6f;
        if (b == Blocks.IRON_BLOCK || b instanceof UraniumFuelRod) return 0f;
        if (b == Blocks.GOLD_BLOCK || b == Blocks.DIAMOND_BLOCK || b == Blocks.NETHERITE_BLOCK) return 0.05f;
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return 0.4f;
        if (b == Blocks.STONE || b == Blocks.COBBLESTONE || b == Blocks.DEEPSLATE
                || b == Blocks.STONE_BRICKS || b == Blocks.BRICKS || b == Blocks.SANDSTONE) return 0.15f;
        if (b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.GRAVEL
                || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK) return 0.25f;
        return 0.1f;
    }

    private static boolean isPointInSolid(ServerLevel level, Vec3 point) {
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag particleList = new ListTag();
        for (RadiationParticle p : particles.values()) particleList.add(p.save());
        tag.put("particles", particleList);
        tag.putLongArray("rods", rods.stream().mapToLong(BlockPos::asLong).toArray());
        CompoundTag heatTag = new CompoundTag();
        rodHeat.forEach((pos, heat) -> heatTag.putFloat(String.valueOf(pos.asLong()), heat));
        tag.put("rodHeat", heatTag);
        CompoundTag pipesTag = new CompoundTag();
        pipeConnections.forEach((pos, peers) -> {
            long[] arr = peers.stream().mapToLong(BlockPos::asLong).toArray();
            pipesTag.put(String.valueOf(pos.asLong()), new LongArrayTag(arr));
        });
        tag.put("pipeConnections", pipesTag);
        return tag;
    }

    public static RadiationManager load(CompoundTag tag, HolderLookup.Provider registries) {
        RadiationManager m = new RadiationManager();
        ListTag list = tag.getList("particles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            RadiationParticle p = RadiationParticle.load(list.getCompound(i));
            m.particles.put(p.id, p);
        }
        for (long l : tag.getLongArray("rods")) m.rods.add(BlockPos.of(l));
        CompoundTag heatTag = tag.getCompound("rodHeat");
        for (String key : heatTag.getAllKeys()) {
            m.rodHeat.put(BlockPos.of(Long.parseLong(key)), heatTag.getFloat(key));
        }
        CompoundTag pipesTag = tag.getCompound("pipeConnections");
        for (String key : pipesTag.getAllKeys()) {
            BlockPos pos = BlockPos.of(Long.parseLong(key));
            Set<BlockPos> peers = new HashSet<>();
            for (long l : pipesTag.getLongArray(key)) peers.add(BlockPos.of(l));
            m.pipeConnections.put(pos, peers);
        }
        return m;
    }
}
