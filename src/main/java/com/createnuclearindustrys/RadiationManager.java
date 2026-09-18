package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

public class RadiationManager extends SavedData {
    private static final String DATA_ID = CreateNuclearIndustrys.MODID + "_radiation";
    private static final int EMIT_INTERVAL = 10;
    private static final float MELTDOWN_TEMP = 1000f;
    // Water moderates: particles slowed by water are absorbed far more effectively than fast ones
    private static final float MODERATED_HEAT = 2.0f;
    private static final float UNMODERATED_HEAT = 0.5f;
    // Water also cools: a submerged rod sheds heat about 3x faster than one in air.
    // Kept mild on purpose — moderation has to win, or a submerged reactor never reaches
    // the boiler's 100C and makes no power.
    private static final float COOLING_IN_AIR = 0.999f;
    private static final float COOLING_IN_WATER = 0.997f;

    private final Map<UUID, RadiationParticle> particles = new LinkedHashMap<>();
    private final Set<BlockPos> rods = new HashSet<>();
    private final Map<BlockPos, Float> rodHeat = new HashMap<>();
    // Biome ambient temperature per node; not saved, recomputed on demand
    private final Map<BlockPos, Float> ambientHeat = new HashMap<>();
    private final RandomSource rng = RandomSource.create();
    private final List<RadiationParticle> pendingBroadcast = new ArrayList<>();

    public static RadiationManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(RadiationManager::new, RadiationManager::load),
            DATA_ID
        );
    }

    public void registerRod(BlockPos pos, ServerLevel level) {
        registerRod(pos, getAmbientHeat(level, pos));
    }

    private void registerRod(BlockPos pos, float startHeat) {
        rods.add(pos.immutable());
        rodHeat.put(pos.immutable(), startHeat);
        setDirty();
    }

    /**
     * Registers a heat node that arrived here by being moved (piston, contraption, falling block,
     * /setblock). Gauges and boilers get their heat back from the network on the next tick.
     */
    public void registerMovedNode(ServerLevel level, BlockPos pos) {
        if (rods.contains(pos)) return;
        registerRod(pos, getAmbientHeat(level, pos));
    }

    /**
     * Registers a rod that arrived here by being moved.
     * Its heat is carried over approximately from the heat_level block state.
     */
    public void registerMovedRod(ServerLevel level, BlockPos pos, int heatLevel) {
        if (rods.contains(pos)) return;
        float ambient = getAmbientHeat(level, pos);
        // heat_level 0 covers everything below ~67°C, so ambient is the best estimate there.
        // Capped below meltdown: a rod must never arrive somewhere already melting, or a rod
        // thrown clear by one meltdown lands and detonates again, forever.
        float carried = heatLevel == 0 ? ambient
                : Math.min(MELTDOWN_TEMP * 0.9f, Math.max(ambient, heatLevel / 15f * MELTDOWN_TEMP));
        registerRod(pos, carried);
    }

    public void removeRod(BlockPos pos, ServerLevel level) {
        rods.remove(pos);
        rodHeat.remove(pos);
        ambientHeat.remove(pos);
        setDirty();
    }

    /**
     * Ambient temperature in °C from the biome. Vanilla freezes water below a biome temperature
     * of 0.15, so that maps to 0°C; plains (0.8) maps to 20°C and deserts (2.0) to ~57°C.
     */
    private float getAmbientHeat(ServerLevel level, BlockPos pos) {
        return ambientHeat.computeIfAbsent(pos.immutable(),
                p -> (level.getBiome(p).value().getBaseTemperature() - 0.15f) * (20f / 0.65f));
    }

    public void addHeat(BlockPos pos, float amount) {
        if (!rods.contains(pos)) return;
        rodHeat.merge(pos.immutable(), amount, Float::sum);
        setDirty();
    }

    public float getHeat(BlockPos pos) {
        return rodHeat.getOrDefault(pos, 0f);
    }

    public void forceSetHeat(BlockPos pos, int value) {
        BlockPos key = pos.immutable();
        if (!rods.contains(key)) registerRod(key, (float) value);
        rodHeat.put(key, (float) value);
        setDirty();
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
        // Validate registered nodes still exist (handles pistons, explosions, /fill, etc.)
        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos pos : rods) {
            if (level.isLoaded(pos) && !isHeatNode(level.getBlockState(pos).getBlock()))
                gone.add(pos);
        }
        for (BlockPos pos : gone) removeRod(pos, level);

        // Auto-discover heat nodes adjacent to the known network that weren't registered
        // (catches blocks placed by /fill, commands, pistons, or FallingBlockEntity landing)
        List<BlockPos> toRegister = new ArrayList<>();
        for (BlockPos pos : new ArrayList<>(rods)) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (rods.contains(neighbor.immutable())) continue;
                if (!level.isLoaded(neighbor)) continue;
                if (isHeatNode(level.getBlockState(neighbor).getBlock()))
                    toRegister.add(neighbor.immutable());
            }
        }
        for (BlockPos pos : toRegister) registerRod(pos, level);

        // Dissipate heat toward ambient; uranium rods melt at 1000°C, everything else just caps
        List<BlockPos> melted = new ArrayList<>();
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            float heat = entry.getValue();
            if (heat >= MELTDOWN_TEMP) {
                if (level.getBlockState(entry.getKey()).getBlock() instanceof UraniumFuelRod)
                    melted.add(entry.getKey());
            } else {
                float ambient = getAmbientHeat(level, entry.getKey());
                float cooling = isWaterlogged(level.getBlockState(entry.getKey()))
                        ? COOLING_IN_WATER : COOLING_IN_AIR;
                entry.setValue(ambient + (heat - ambient) * cooling);
            }
        }
        for (BlockPos pos : melted) {
            triggerMeltdown(pos, level);
            rods.remove(pos);
            rodHeat.remove(pos);
            ambientHeat.remove(pos);
        }
        if (!melted.isEmpty()) setDirty();

        // Boilers drain heat while converting water to steam (no kinetic output) — only while
        // actually boiling, so a boiler whose steam has nowhere to go stops cooling the reactor
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            if (!(level.getBlockState(entry.getKey()).getBlock() instanceof BoilerBlock)) continue;
            float heat = entry.getValue();
            if (heat < 100f) continue;
            if (!(level.getBlockEntity(entry.getKey()) instanceof BoilerBlockEntity bbe)
                    || !bbe.isBoiling()) continue;
            entry.setValue(Math.max(0f, heat - heat * 0.005f));
        }

        // Vertical conduction between stacked uranium rods
        for (BlockPos pos : new ArrayList<>(rods)) {
            BlockPos above = pos.above();
            if (!rods.contains(above)) continue;
            float heatHere  = rodHeat.getOrDefault(pos, 0f);
            float heatAbove = rodHeat.getOrDefault(above, 0f);
            float transfer  = (heatHere - heatAbove) * 0.20f;
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
                float transfer = (heatA - heatB) * 0.25f;
                if (Math.abs(transfer) < 0.01f) continue;
                rodHeat.put(pos, heatA - transfer);
                rodHeat.put(neighbor.immutable(), heatB + transfer);
            }
        }

        // Re-pin creative heat sources after conduction so they always output their target temp
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            if (level.getBlockState(entry.getKey()).getBlock() instanceof CreativeHeatSourceBlock
                    && level.getBlockEntity(entry.getKey()) instanceof CreativeHeatSourceBlockEntity cbe) {
                entry.setValue((float) cbe.targetTemperature.value);
            }
        }

        // Sync heat_level block state to clients (drives light + tint)
        for (Map.Entry<BlockPos, Float> entry : rodHeat.entrySet()) {
            BlockPos pos = entry.getKey();
            float heat = entry.getValue();
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() instanceof UraniumFuelRod) {
                int newLevel = Math.max(0, Math.min(15, (int)(heat / MELTDOWN_TEMP * 15)));
                if (current.getValue(UraniumFuelRod.HEAT_LEVEL) != newLevel)
                    level.setBlock(pos, current.setValue(UraniumFuelRod.HEAT_LEVEL, newLevel), 2);
            } else if (current.getBlock() instanceof HeatGaugeBlock
                    && level.getBlockEntity(pos) instanceof HeatGaugeBlockEntity be) {
                be.setHeat(heat);
            } else if (current.getBlock() instanceof BoilerBlock
                    && level.getBlockEntity(pos) instanceof BoilerBlockEntity bbe) {
                bbe.setHeat(heat);
            }
        }

        // Emit particles — rate scales with heat to simulate criticality
        for (BlockPos rod : new ArrayList<>(rods)) {
            if (!level.isLoaded(rod)) continue;
            if (!(level.getBlockState(rod).getBlock() instanceof UraniumFuelRod)) continue;
            float heatFrac = Math.max(0f, Math.min(1f, rodHeat.getOrDefault(rod, 0f) / MELTDOWN_TEMP));
            // interval shrinks from 10 (cold) down to 1 (at meltdown temp) — 10× more particles
            int interval = Math.max(1, (int)(EMIT_INTERVAL * (1f - heatFrac * 0.9f)));
            if (rng.nextInt(interval) != 0) continue;
            emitFromRod(rod);
            // Near criticality: burst a second particle
            if (heatFrac > 0.8f && rng.nextFloat() < (heatFrac - 0.8f) * 5f)
                emitFromRod(rod);

        }

        List<UUID> dead = new ArrayList<>();
        for (RadiationParticle p : particles.values()) {
            if (--p.ticksLeft <= 0) { dead.add(p.id); continue; }
            step(p, level);
        }
        if (!dead.isEmpty()) { dead.forEach(particles::remove); setDirty(); }
    }

    private void triggerMeltdown(BlockPos epicenter, ServerLevel level) {
        // Power scales with ALL registered rods nearby — big reactors should make bigger booms
        int nearbyRods = 0;
        for (BlockPos rodPos : rods) {
            if (!rodPos.equals(epicenter) && rodPos.distSqr(epicenter) <= 100)
                nearbyRods++;
        }

        // Fling surrounding blocks outward before the explosion
        int flingRadius = 6;
        for (int dx = -flingRadius; dx <= flingRadius; dx++) {
            for (int dy = -flingRadius; dy <= flingRadius; dy++) {
                for (int dz = -flingRadius; dz <= flingRadius; dz++) {
                    if (dx*dx + dy*dy + dz*dz > flingRadius * flingRadius) continue;
                    if (rng.nextFloat() > 0.15f) continue;

                    BlockPos scanPos = epicenter.offset(dx, dy, dz);
                    if (!level.isLoaded(scanPos)) continue;

                    BlockState state = level.getBlockState(scanPos);
                    if (state.isAir()) continue;
                    if (!state.getFluidState().isEmpty()) continue;
                    if (state.getDestroySpeed(level, scanPos) < 0) continue;
                    // Fuel rods are consumed by the meltdown. Throwing them clear used to reseed
                    // the explosion wherever they landed.
                    if (state.getBlock() instanceof UraniumFuelRod) continue;

                    FallingBlockEntity flung = FallingBlockEntity.fall(level, scanPos, state);

                    Vec3 dir = new Vec3(dx + 0.001, Math.max(dy, 0) + 0.6, dz + 0.001).normalize();
                    double speed = 0.6 + rng.nextDouble() * 1.4;
                    flung.setDeltaMovement(dir.scale(speed));
                    flung.dropItem = false;
                }
            }
        }

        // The rod itself melts down into a single lava block — the only lava a meltdown creates
        level.setBlock(epicenter, Blocks.LAVA.defaultBlockState(), 3);

        // Explosion power: base 7, +1.5 per nearby rod, capped at 20
        float power = Math.min(20f, 7f + nearbyRods * 1.5f);
        level.explode(null,
            epicenter.getX() + 0.5, epicenter.getY() + 0.5, epicenter.getZ() + 0.5,
            power, true, Level.ExplosionInteraction.TNT);

        // Chain reaction: dump heat into nearby rods so they melt next tick
        // This ensures a big reactor cascades outward rather than fizzling
        for (BlockPos rodPos : new ArrayList<>(rods)) {
            if (!rodPos.equals(epicenter) && rodPos.distSqr(epicenter) <= 25) // 5-block radius
                addHeat(rodPos, MELTDOWN_TEMP * 1.2f);
        }
    }

    static boolean isWaterlogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private static boolean isHeatNode(Block b) {
        return b instanceof UraniumFuelRod || b instanceof HeatGaugeBlock
                || b instanceof HeatPipeBlock || b instanceof BoilerBlock || b instanceof CreativeHeatSourceBlock;
    }

    public void emitFromOre(BlockPos pos) {
        double theta = rng.nextDouble() * Math.PI * 2;
        double phi   = Math.acos(2.0 * rng.nextDouble() - 1.0);
        double speed = 0.05 + rng.nextDouble() * 0.12;
        double vx    = speed * Math.sin(phi) * Math.cos(theta);
        double vy    = speed * Math.cos(phi);
        double vz    = speed * Math.sin(phi) * Math.sin(theta);

        RadiationParticle p = new RadiationParticle(
            UUID.randomUUID(), Vec3.atCenterOf(pos),
            new Vec3(vx, vy, vz),
            0.3f + rng.nextFloat() * 0.4f, 0.7f + rng.nextFloat() * 0.3f, 0.1f,
            0.15f + rng.nextFloat() * 0.1f, 30 + rng.nextInt(40), pos
        );
        particles.put(p.id, p);
        pendingBroadcast.add(p);
        setDirty();
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

        // Check if the particle passes through a player — if so, give them radiation sickness.
        // Creative and spectator players are immune; particles pass straight through them.
        AABB particleHitbox = new AABB(next.x - 0.4, next.y - 0.4, next.z - 0.4,
                                       next.x + 0.4, next.y + 0.4, next.z + 0.4);
        List<Player> players = level.getEntitiesOfClass(Player.class, particleHitbox,
                player -> !player.isCreative() && !player.isSpectator());
        if (!players.isEmpty()) {
            applyRadiationSickness(players.get(0));
            p.ticksLeft = 0; // absorbed by the player's body
            return;
        }

        // Water slows the particle down (moderation), making the next rod it hits absorb it properly
        if (!p.moderated && level.getFluidState(BlockPos.containing(next)).getType() == Fluids.WATER)
            p.moderated = true;

        if (!isPointInSolid(level, next)) { p.pos = next; return; }

        BlockPos hitBlock = BlockPos.containing(next);

        if (rng.nextFloat() < getAbsorption(level.getBlockState(hitBlock))) {
            p.ticksLeft = 0;
            return;
        }

        if (rods.contains(hitBlock) && !hitBlock.equals(p.source)
                && !hitBlock.equals(p.source.above()) && !hitBlock.equals(p.source.below())
                && !(level.getBlockState(hitBlock).getBlock() instanceof HeatPipeBlock)) {
            addHeat(hitBlock, p.energy * 5f * (p.moderated ? MODERATED_HEAT : UNMODERATED_HEAT));
        }

        double vx = p.vel.x, vy = p.vel.y, vz = p.vel.z;
        if (isPointInSolid(level, new Vec3(p.pos.x + p.vel.x, p.pos.y,            p.pos.z           ))) vx = -vx;
        if (isPointInSolid(level, new Vec3(p.pos.x,            p.pos.y + p.vel.y, p.pos.z           ))) vy = -vy;
        if (isPointInSolid(level, new Vec3(p.pos.x,            p.pos.y,            p.pos.z + p.vel.z))) vz = -vz;
        if (vx == p.vel.x && vy == p.vel.y && vz == p.vel.z) { vx = -vx; vy = -vy; vz = -vz; }
        p.vel = new Vec3(vx, vy, vz);
    }

    /** Extends (or starts) the Radiation Sickness effect on the given player by +1 second. */
    private static void applyRadiationSickness(LivingEntity entity) {
        var holder = CreateNuclearIndustrys.RADIATION_SICKNESS;
        MobEffectInstance existing = entity.getEffect(holder);
        int newDuration = (existing != null ? existing.getDuration() : 0) + 20;
        entity.addEffect(new MobEffectInstance(holder, newDuration, 0, false, true, true));
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
