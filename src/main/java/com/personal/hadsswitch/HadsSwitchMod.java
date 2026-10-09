package com.personal.hadsswitch;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(HadsSwitchMod.MOD_ID)
public class HadsSwitchMod {
    public static final String MOD_ID = "hadsswitch";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Block> HADS_SWITCH = BLOCKS.register("hads_switch",
            () -> new HadsSwitchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.0f)
                    .lightLevel(state -> state.getValue(HadsSwitchBlock.ACTIVE) ? 7 : 0)));
    public static final RegistryObject<Item> HADS_SWITCH_ITEM = ITEMS.register("hads_switch",
            () -> new BlockItem(HADS_SWITCH.get(), new Item.Properties()));

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    /** Played only when HADS makes the TARDIS dematerialise (file: assets/hadsswitch/sounds/hads_demat.ogg). */
    public static final RegistryObject<SoundEvent> HADS_DEMAT_SOUND = SOUNDS.register("hads_demat",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "hads_demat")));

    /** Same song, but low-passed with a little echo, so it sounds like it is playing from inside the box. */
    public static final RegistryObject<SoundEvent> HADS_DEMAT_MUFFLED_SOUND = SOUNDS.register("hads_demat_muffled",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "hads_demat_muffled")));

    public HadsSwitchMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HadsConfig.COMMON);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HadsConfig.CLIENT);
        bus.addListener(this::commonSetup);
        bus.addListener(this::addToCreativeTab);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(HadsNetwork::register);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(HADS_SWITCH_ITEM);
        }
    }
}
