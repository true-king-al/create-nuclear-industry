package com.createnuclearindustrys;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

public class SteamTurbineBlockEntity extends GeneratingKineticBlockEntity {

    private static final float MAX_SU = 512f;
    // Max steam processed per tick; actual throughput is pipe-limited
    private static final int MAX_THROUGHPUT = 100;
    // Fraction of steam consumed to generate power; the rest exits the output tank
    private static final float CONSUMPTION_RATIO = 0.10f;

    private static final int TANK_CAPACITY = 4000;

    /** Steam fed in by pipes. */
    private final FluidTank steamInputTank = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid() == CreateNuclearIndustrys.STEAM_STILL.get());

    /** Reduced steam pushed out to pipes. */
    private final FluidTank steamOutputTank = new FluidTank(TANK_CAPACITY);

    /**
     * Fill-only handler exposed on the back face (steam enters here).
     * Pipes see no fluid to drain, preventing them from treating the input as a source.
     */
    private final IFluidHandler inputOnlyHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return steamInputTank.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return steamInputTank.isFluidValid(stack); }
        @Override public int fill(FluidStack resource, FluidAction action) {
            int filled = steamInputTank.fill(resource, action);
            if (filled > 0 && action.execute()) setChanged();
            return filled;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };

    /**
     * Drain-only handler exposed on all non-back faces (processed steam exits here).
     * Pipes cannot fill through this side, eliminating ambiguity.
     */
    private final IFluidHandler outputOnlyHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return steamOutputTank.getFluid(); }
        @Override public int getTankCapacity(int tank) { return steamOutputTank.getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack drained = steamOutputTank.drain(resource, action);
            if (!drained.isEmpty() && action.execute()) setChanged();
            return drained;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = steamOutputTank.drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute()) setChanged();
            return drained;
        }
    };

    // How much steam was consumed this tick — determines stress capacity
    private int steamConsumedThisTick = 0;
    private int prevConsumed = -1;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(CreateNuclearIndustrys.STEAM_TURBINE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // ── Kinetic output ────────────────────────────────────────────────────────

    @Override
    public float getGeneratedSpeed() {
        return steamConsumedThisTick > 0 ? 16f : 0f;
    }

    @Override
    public float calculateAddedStressCapacity() {
        return ((float) steamConsumedThisTick / MAX_THROUGHPUT) * MAX_SU;
    }

    // ── Per-tick processing ───────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;

        int available = Math.min(MAX_THROUGHPUT, steamInputTank.getFluidAmount());
        int consumed = 0;

        if (available > 0 && steamOutputTank.getFluidAmount() < TANK_CAPACITY) {
            int passThrough = (int) (available * (1f - CONSUMPTION_RATIO));
            consumed = available - passThrough;

            steamInputTank.drain(available, IFluidHandler.FluidAction.EXECUTE);
            if (passThrough > 0)
                steamOutputTank.fill(
                        new FluidStack(CreateNuclearIndustrys.STEAM_STILL.get(), passThrough),
                        IFluidHandler.FluidAction.EXECUTE);
            setChanged();
        }

        steamConsumedThisTick = consumed;

        if (consumed != prevConsumed) {
            boolean wasActive = prevConsumed > 0;
            boolean isActive  = consumed > 0;
            prevConsumed = consumed;

            if (isActive != wasActive) {
                // Speed crossing zero — updateGeneratedRotation handles the full network
                // rebuild. Calling notifyStressCapacityChange on the same tick would
                // reach connected block entities (e.g. stress gauge) before their network
                // keys are restored, causing a NPE inside Create's KineticNetwork.
                updateGeneratedRotation();
            } else if (hasNetwork()) {
                // Speed unchanged (both ticks non-zero), network is stable —
                // just update the capacity value.
                notifyStressCapacityChange(calculateAddedStressCapacity());
            }
        }
    }

    // ── Fluid API ─────────────────────────────────────────────────────────────

    public IFluidHandler getInputHandler()  { return inputOnlyHandler; }
    public IFluidHandler getOutputHandler() { return outputOnlyHandler; }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("steamIn",  steamInputTank.writeToNBT(registries, new CompoundTag()));
        tag.put("steamOut", steamOutputTank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("steamIn"))  steamInputTank.readFromNBT(registries,  tag.getCompound("steamIn"));
        if (tag.contains("steamOut")) steamOutputTank.readFromNBT(registries, tag.getCompound("steamOut"));
    }
}
