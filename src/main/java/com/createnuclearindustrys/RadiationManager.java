package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

import java.util.*;

public class RadiationManager extends SavedData {
    private static final String DATA_ID = CreateNuclearIndustrys.MODID + "_radiation";
    private static final int EMIT_INTERVAL = 10;
    private static final float MELTDOWN_TEMP = 1000f;

    private final Map<UUID, RadiationParticle> particles = new LinkedHashMap<>();
    private final Set<BlockPos> rods = new HashSet<>();
    private final Map<BlockPos, Float> rodHeat = new HashMap<>();
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
        setDirty();
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
        // Validate registered nodes still exist (handles pistons, explosions, commands)
        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos pos : rods) {
            if (level.isLoaded(pos) && !isHeatNode(level.getBlockState(pos).getBlock()))
                gone.add(pos);
        }
        for (BlockPos pos : gone) removeRod(pos, level);

        // Dissipate heat; uranium rods melt at 1000°C, everything else just caps
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

        // Vertical conduction between stacked uranium rods
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

        // Heat pipe block conduction: each pipe block equalizes with all 6 neighbors
        Set<Long> pipeProcessed = new HashSet<>();
        for (BlockPos pos : new ArrayList<>(rods)) {
            if (!(level.getBlockState(pos).getBlock() instanceof HeatPipeBlock)) continue;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!rods.contains(neighbor)) continue;
                long la = pos.asLong(), lb = neighbor.asLong();
                long key = la < lb ? la * 31L + lb : lb * 31L + la;
                if (!pipeProcessed.add(key)) continue;
                float heatA = rodHeat.getOrDefault(pos, 0f);
                float heatB = rodHeat.getOrDefault(neighbor, 0f);
                float transfer = (heatA - heatB) * 0.05f;
                if (Math.abs(transfer) < 0.01f) continue;
                rodHeat.put(pos, heatA - transfer);
                rodHeat.put(neighbor.immutable(), heatB + transfer);
            }
        }

        // Sync heat_level block state to clients (drives light + tint)
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            BlockPos pos = entry.getKey();
            int newLevel = Math.min(15, (int)(entry.getValue() / MELTDOWN_TEMP * 15));
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() instanceof UraniumFuelRod
                    && current.getValue(UraniumFuelRod.HEAT_LEVEL) != newLevel) {
                level.setBlock(pos, current.setValue(UraniumFuelRod.HEAT_LEVEL, newLevel), 2);
            }
        }

        // Emit one particle per uranium rod per trigger
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

    private static boolean isHeatNode(Block b) {
        return b instanceof UraniumFuelRod || b instanceof HeatGaugeBlock || b instanceof HeatPipeBlock;
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

        if (BlockPos.containing(p.pos).equals(p.source)) { p.pos = next; return; }

        if (!isPointInSolid(level, next)) { p.pos = next; return; }

        BlockPos hitBlock = BlockPos.containing(next);

        if (rng.nextFloat() < getAbsorption(level.getBlockState(hitBlock))) {
            p.ticksLeft = 0;
            return;
        }

        if (rods.contains(hitBlock) && !hitBlock.equals(p.source)
                && !(level.getBlockState(hitBlock).getBlock() instanceof HeatPipeBlock)) {
            addHeat(hitBlock, p.energy * 5f);
        }

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
        if (b == Blocks.IRON_BLOCK || b instanceof UraniumFuelRod || b instanceof HeatPipeBlock) return 0f;
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
        return m;
    }
}
