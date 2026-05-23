package com.createnuclearindustrys;

import com.simibubi.create.foundation.item.ItemDescription;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CreateNuclearIndustrys.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemDescription.useKey(CreateNuclearIndustrys.BORON_CONTROL_ROD_ITEM.get(),   "block.createnuclearindustrys.boron_control_rod");
            ItemDescription.useKey(CreateNuclearIndustrys.URANIUM_FUEL_ROD_ITEM.get(),    "block.createnuclearindustrys.uranium_fuel_rod");
            ItemDescription.useKey(CreateNuclearIndustrys.HEAT_GAUGE_ITEM.get(),          "block.createnuclearindustrys.heat_gauge");
            ItemDescription.useKey(CreateNuclearIndustrys.HEAT_PIPE_ITEM.get(),           "block.createnuclearindustrys.heat_pipe");
            ItemDescription.useKey(CreateNuclearIndustrys.THERMAL_GENERATOR_ITEM.get(),   "block.createnuclearindustrys.thermal_generator");
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                CreateNuclearIndustrys.THERMAL_GENERATOR_BLOCK_ENTITY.get(),
                ThermalGeneratorRenderer::new);
    }
}
