package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class HeatGaugeBlock extends Block implements EntityBlock {

    public HeatGaugeBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatGaugeBlockEntity(pos, state);
    }


    /**
     * Fires whenever this block appears here by any means — pistons, Create contraptions,
     * falling blocks, /setblock — so a moved gauge stays on the heat network.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (oldState.is(this) || !(level instanceof ServerLevel serverLevel)) return;
        RadiationManager.get(serverLevel).registerMovedNode(serverLevel, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        float heat = RadiationManager.get((ServerLevel) level).getHeat(pos);
        player.sendSystemMessage(Component.literal(String.format("[Heat Gauge] %.1f°C", heat)));

        return InteractionResult.SUCCESS;
    }

    // ── Analog redstone output ────────────────────────────────────────────────

    /** The Heat Gauge emits a comparator/redstone signal proportional to its heat. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * Returns 0–15 linearly mapped to 0°C – 1000°C (meltdown temperature).
     * A comparator placed next to the gauge will read this value directly.
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HeatGaugeBlockEntity be)
            return Math.max(0, Math.min(15, (int)(be.heat / 1000f * 15)));
        return 0;
    }

    /** Needed alongside isSignalSource so the game knows to call getAnalogOutputSignal. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }
}
