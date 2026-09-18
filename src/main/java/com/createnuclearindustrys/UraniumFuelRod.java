package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class UraniumFuelRod extends Block implements SimpleWaterloggedBlock {
    public static final IntegerProperty HEAT_LEVEL = IntegerProperty.create("heat_level", 0, 15);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public UraniumFuelRod(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT_LEVEL, 0).setValue(WATERLOGGED, false));
    }

    /** Glows with heat; a submerged hot rod lights the pool more, backing up the Cherenkov glow. */
    public static int lightLevel(BlockState state) {
        int heat = state.getValue(HEAT_LEVEL);
        return state.getValue(WATERLOGGED) && heat > 0 ? Math.min(15, heat + 5) : heat;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT_LEVEL, WATERLOGGED);
    }

    /**
     * Waterlogging. Create's contraptions leave the water behind when they pick this block up,
     * and recompute waterlogging from wherever the block lands, so a rod hauled out of a
     * reactor pool by a pulley does not carry water up with it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean inWater = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, inWater);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED))
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
