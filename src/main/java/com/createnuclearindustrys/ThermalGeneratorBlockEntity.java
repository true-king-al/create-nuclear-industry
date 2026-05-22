package com.createnuclearindustrys;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ThermalGeneratorBlockEntity extends GeneratingKineticBlockEntity {

    private static final float MAX_SU = 512f;
    float heat = 0f;

    public ThermalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(CreateNuclearIndustrys.THERMAL_GENERATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // no extra Create behaviours needed
    }

    @Override
    public float getGeneratedSpeed() {
        return heat > 10f ? 16f : 0f;
    }

    @Override
    public float calculateAddedStressCapacity() {
        return Math.max(0f, (heat / 1000f) * MAX_SU);
    }

    public void setHeat(float newHeat) {
        if (Math.abs(heat - newHeat) < 0.5f) return;
        boolean wasRunning = heat > 10f;
        heat = newHeat;
        boolean nowRunning = heat > 10f;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            updateGeneratedRotation(); // safe — has internal hasNetwork() guard
            if (wasRunning != nowRunning && hasNetwork())
                notifyStressCapacityChange(calculateAddedStressCapacity());
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("heat", heat);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        heat = tag.getFloat("heat");
    }
}
