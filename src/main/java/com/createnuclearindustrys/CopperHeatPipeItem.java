package com.createnuclearindustrys;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CopperHeatPipeItem extends Item {
    // Server-side: first rod each player has selected
    private static final Map<UUID, BlockPos> pendingFirst = new HashMap<>();

    public CopperHeatPipeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos clicked = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        UUID playerId = player.getUUID();
        ServerLevel serverLevel = (ServerLevel) level;
        RadiationManager manager = RadiationManager.get(serverLevel);

        Block clickedBlock = level.getBlockState(clicked).getBlock();
        if (!(clickedBlock instanceof UraniumFuelRod) && !(clickedBlock instanceof HeatGaugeBlock)) {
            pendingFirst.remove(playerId);
            return InteractionResult.PASS;
        }

        // Shift-click: remove all connections from this rod and refund items
        if (player.isShiftKeyDown()) {
            int removed = manager.removeAllPipeConnections(clicked, serverLevel);
            if (!player.isCreative()) {
                for (int i = 0; i < removed; i++) {
                    player.getInventory().add(new net.minecraft.world.item.ItemStack(
                            CreateNuclearIndustrys.COPPER_HEAT_PIPE.get()));
                }
            }
            pendingFirst.remove(playerId);
            return InteractionResult.SUCCESS;
        }

        BlockPos first = pendingFirst.get(playerId);

        // First click: remember this rod
        if (first == null || first.equals(clicked)) {
            pendingFirst.put(playerId, clicked.immutable());
            return InteractionResult.SUCCESS;
        }

        // Second click on a different rod: create connection
        if (!manager.hasPipeConnection(first, clicked)) {
            manager.addPipeConnection(first, clicked, serverLevel);
            if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        }

        pendingFirst.remove(playerId);
        return InteractionResult.SUCCESS;
    }
}
