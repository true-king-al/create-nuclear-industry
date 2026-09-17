package com.createnuclearindustrys;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Mod-bus event subscriber for common (server + client) setup that
 * doesn't belong in the main mod class — capability registrations live here.
 */
@EventBusSubscriber(modid = CreateNuclearIndustrys.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CommonModEvents {

    /**
     * Register the Thermal Generator's fluid handler capability so Create pipes
     * (and any other mod using Capabilities.FluidHandler.BLOCK) can pump water in
     * and steam out automatically.
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CreateNuclearIndustrys.STEAM_TURBINE_BLOCK_ENTITY.get(),
                (be, side) -> {
                    if (side == null) return be.getOutputHandler();
                    Direction facing = be.getBlockState().getValue(DirectionalBlock.FACING);
                    // Back face = steam inlet (fill-only)
                    if (side == facing.getOpposite()) return be.getInputHandler();
                    // All other faces = steam outlet (drain-only)
                    return be.getOutputHandler();
                }
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CreateNuclearIndustrys.BOILER_BLOCK_ENTITY.get(),
                (be, side) -> be.getFluidHandler()
        );
    }
}
