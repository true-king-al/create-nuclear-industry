package com.createnuclearindustrys;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ThermalGeneratorBlock extends RotatedPillarKineticBlock implements IBE<ThermalGeneratorBlockEntity> {

    public ThermalGeneratorBlock(Properties props) {
        super(props);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(BlockStateProperties.AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(BlockStateProperties.AXIS);
    }

    @Override
    public Class<ThermalGeneratorBlockEntity> getBlockEntityClass() {
        return ThermalGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ThermalGeneratorBlockEntity> getBlockEntityType() {
        return CreateNuclearIndustrys.THERMAL_GENERATOR_BLOCK_ENTITY.get();
    }
}
