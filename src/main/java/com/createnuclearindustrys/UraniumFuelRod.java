package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class UraniumFuelRod extends Block {
    public static final IntegerProperty HEAT_LEVEL = IntegerProperty.create("heat_level", 0, 15);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public UraniumFuelRod(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT_LEVEL);
    }

    /**
     * Fires whenever a rod appears here by any means — pistons, Create contraptions,
     * falling blocks, /setblock — so a moved rod keeps radiating from its new position.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Heat-level updates also re-set this block; only react when a rod newly arrives
        if (oldState.is(this) || !(level instanceof ServerLevel serverLevel)) return;
        RadiationManager.get(serverLevel).registerMovedRod(serverLevel, pos, state.getValue(HEAT_LEVEL));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
