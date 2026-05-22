package com.createnuclearindustrys;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = CreateNuclearIndustrys.MODID)
public class RadiationEvents {

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        RadiationManager manager = RadiationManager.get(serverLevel);
        manager.tick(serverLevel);

        List<RadiationParticle> born = manager.drainPendingBroadcast();
        for (RadiationParticle p : born) {
            PacketDistributor.sendToPlayersInDimension(serverLevel, new RadiationBirthPacket(
                p.id,
                p.pos.x, p.pos.y, p.pos.z,
                p.vel.x, p.vel.y, p.vel.z,
                p.r, p.g, p.b,
                p.energy,
                p.ticksLeft,
                p.source.asLong()
            ));
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Block placed = event.getPlacedBlock().getBlock();
        if (placed instanceof UraniumFuelRod || placed instanceof HeatGaugeBlock) {
            RadiationManager.get(serverLevel).registerRod(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        Block broken = event.getState().getBlock();
        if (broken instanceof UraniumFuelRod || broken instanceof HeatGaugeBlock) {
            RadiationManager.get(serverLevel).removeRod(event.getPos(), serverLevel);
        }
    }
}
