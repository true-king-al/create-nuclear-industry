package com.createnuclearindustrys;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import java.util.List;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateNuclearIndustrys.MODID)
public class CreateNuclearIndustrys {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "createnuclearindustrys";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MODID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);

    // ── Create Display Sources ─────────────────────────────────────────────────
    // Must be in CreateBuiltInRegistries.DISPLAY_SOURCE so Create's Display Link
    // can find the source by its ResourceLocation and generate a translation key.
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MODID);

    public static final DeferredHolder<DisplaySource, HeatGaugeDisplaySource> HEAT_GAUGE_DISPLAY_SOURCE =
            DISPLAY_SOURCES.register("heat_gauge_temperature", HeatGaugeDisplaySource::new);

    public static final DeferredHolder<MobEffect, RadiationSicknessEffect> RADIATION_SICKNESS =
            MOB_EFFECTS.register("radiation_sickness", RadiationSicknessEffect::new);

    // ── Steam fluid ───────────────────────────────────────────────────────────

    public static final DeferredHolder<FluidType, SteamFluidType> STEAM_FLUID_TYPE =
            FLUID_TYPES.register("steam", () -> new SteamFluidType(
                    FluidType.Properties.create()
                            .density(-200)       // rises (negative = lighter than air)
                            .viscosity(200)
                            .temperature(400)));

    public static final DeferredHolder<Fluid, SteamFluid.Still> STEAM_STILL =
            FLUIDS.register("steam", SteamFluid.Still::new);
    public static final DeferredHolder<Fluid, SteamFluid.Flowing> STEAM_FLOWING =
            FLUIDS.register("flowing_steam", SteamFluid.Flowing::new);

    public static final DeferredBlock<LiquidBlock> STEAM_BLOCK =
            BLOCKS.registerBlock("steam_fluid",
                    p -> new SteamFluidBlock(STEAM_STILL.get(), p),
                    BlockBehaviour.Properties.of()
                            .noCollission().strength(100f).noLootTable().replaceable());

    public static final DeferredItem<BucketItem> STEAM_BUCKET =
            ITEMS.register("steam_bucket",
                    () -> new BucketItem(STEAM_STILL.get(),
                            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    // ── Steam particle ────────────────────────────────────────────────────────

    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> STEAM_PARTICLE =
            PARTICLE_TYPES.register("steam", () -> new SimpleParticleType(false));

    public static final DeferredBlock<HeatGaugeBlock> HEAT_GAUGE = BLOCKS.registerBlock("heat_gauge",
            HeatGaugeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0f, 6.0f).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> HEAT_GAUGE_ITEM = ITEMS.registerSimpleBlockItem("heat_gauge", HEAT_GAUGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeatGaugeBlockEntity>> HEAT_GAUGE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("heat_gauge", () -> BlockEntityType.Builder.of(
                    HeatGaugeBlockEntity::new, HEAT_GAUGE.get()).build(null));

    public static final DeferredBlock<BoronControlRod> BORON_CONTROL_ROD = BLOCKS.registerBlock("boron_control_rod",
            BoronControlRod::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<BlockItem> BORON_CONTROL_ROD_ITEM = ITEMS.registerSimpleBlockItem("boron_control_rod", BORON_CONTROL_ROD);

    public static final DeferredBlock<UraniumFuelRod> URANIUM_FUEL_ROD = BLOCKS.registerBlock("uranium_fuel_rod",
            UraniumFuelRod::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.5f, 6.0f).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> state.getValue(UraniumFuelRod.HEAT_LEVEL)));
    public static final DeferredItem<BlockItem> URANIUM_FUEL_ROD_ITEM = ITEMS.registerSimpleBlockItem("uranium_fuel_rod", URANIUM_FUEL_ROD);

    public static final DeferredBlock<HeatPipeBlock> HEAT_PIPE = BLOCKS.registerBlock("heat_pipe",
            HeatPipeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5f, 6.0f).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> HEAT_PIPE_ITEM = ITEMS.registerSimpleBlockItem("heat_pipe", HEAT_PIPE);

    public static final DeferredBlock<UraniumOreBlock> URANIUM_ORE_BLOCK = BLOCKS.registerBlock("uranium_ore_block",
            UraniumOreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f).requiresCorrectToolForDrops().randomTicks());
    public static final DeferredItem<BlockItem> URANIUM_ORE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("uranium_ore_block", URANIUM_ORE_BLOCK);

    public static final DeferredItem<Item> URANIUM_ORE = ITEMS.registerSimpleItem("uranium_ore", new Item.Properties());
    public static final DeferredItem<Item> ENRICHED_URANIUM_NUGGET = ITEMS.registerSimpleItem("enriched_uranium_nugget", new Item.Properties());
    public static final DeferredItem<Item> ENRICHED_URANIUM = ITEMS.registerSimpleItem("enriched_uranium", new Item.Properties());

    public static final DeferredBlock<ThermalGeneratorBlock> THERMAL_GENERATOR = BLOCKS.registerBlock("thermal_generator",
            ThermalGeneratorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0f, 8.0f).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<BlockItem> THERMAL_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("thermal_generator", THERMAL_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermalGeneratorBlockEntity>> THERMAL_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("thermal_generator", () -> BlockEntityType.Builder.of(
                    ThermalGeneratorBlockEntity::new, THERMAL_GENERATOR.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.createnuclearindustrys")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> URANIUM_FUEL_ROD_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(URANIUM_ORE_BLOCK_ITEM.get());
                output.accept(URANIUM_ORE.get());
                output.accept(ENRICHED_URANIUM_NUGGET.get());
                output.accept(ENRICHED_URANIUM.get());
                output.accept(URANIUM_FUEL_ROD_ITEM.get());
                output.accept(BORON_CONTROL_ROD_ITEM.get());
                output.accept(HEAT_GAUGE_ITEM.get());
                output.accept(HEAT_PIPE_ITEM.get());
                output.accept(THERMAL_GENERATOR_ITEM.get());
                output.accept(STEAM_BUCKET.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CreateNuclearIndustrys(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register block entity types
        BLOCK_ENTITY_TYPES.register(modEventBus);
        // Register mob effects
        MOB_EFFECTS.register(modEventBus);
        // Register fluids and fluid types
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        // Register particle types
        PARTICLE_TYPES.register(modEventBus);
        // Register display sources into Create's registry
        DISPLAY_SOURCES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (CreateNuclearIndustrys) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        // Map the registered DisplaySource instance to the Heat Gauge block entity type
        // so Create's Display Link can discover it when pointed at the gauge.
        // Must use the registered holder (.get()) so getTranslationKey() works in the GUI.
        event.enqueueWork(() ->
            DisplaySource.BY_BLOCK_ENTITY.register(
                HEAT_GAUGE_BLOCK_ENTITY.get(),
                List.of(HEAT_GAUGE_DISPLAY_SOURCE.get())
            )
        );

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
