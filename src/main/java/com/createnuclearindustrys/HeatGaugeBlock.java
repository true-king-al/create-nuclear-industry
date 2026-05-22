package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HeatGaugeBlock extends Block {

    public HeatGaugeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        float heat = RadiationManager.get((ServerLevel) level).getHeat(pos);
        int percent = (int)(heat / 1000f * 100);

        String status;
        if      (heat < 50)  status = "Stable";
        else if (heat < 200) status = "Warm";
        else if (heat < 500) status = "Hot";
        else if (heat < 800) status = "Critical";
        else                 status = "§cDANGER";

        player.sendSystemMessage(Component.literal(
            String.format("[Heat Gauge] %.1f°C  (%d%% to meltdown) — %s", heat, percent, status)
        ));

        return InteractionResult.SUCCESS;
    }
}
