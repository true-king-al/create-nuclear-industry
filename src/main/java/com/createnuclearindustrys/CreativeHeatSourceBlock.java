package com.createnuclearindustrys;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CreativeHeatSourceBlock extends Block implements IBE<CreativeHeatSourceBlockEntity> {

    public CreativeHeatSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<CreativeHeatSourceBlockEntity> getBlockEntityClass() {
        return CreativeHeatSourceBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CreativeHeatSourceBlockEntity> getBlockEntityType() {
        return CreateNuclearIndustrys.CREATIVE_HEAT_SOURCE_BLOCK_ENTITY.get();
    }
}
