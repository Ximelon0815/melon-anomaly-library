package com.melon.anomaly;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import net.minecraft.sounds.SoundEvent;

@Mod(MelonAnomalyLibrary.MODID)
public class MelonAnomalyLibrary
{
    public static final String MODID = "melon_anomaly";
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<SoundEvent> ELECTRIC_SHOCK_SOUND = SOUNDS.register("electric_shock", 
            () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(MODID, "electric_shock")));

    public static final RegistryObject<MobEffect> ELECTRIC_SHOCK = MOB_EFFECTS.register("electric_shock", ElectricShockEffect::new);

    public static final RegistryObject<Block> COPYCAT_BLOCK = BLOCKS.register("copycat_block", () -> new CopycatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noOcclusion()));
    public static final RegistryObject<Item> COPYCAT_BLOCK_ITEM = ITEMS.register("copycat_block", () -> new BlockItem(COPYCAT_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<CopycatBlockEntity>> COPYCAT_BLOCK_ENTITY = BLOCK_ENTITIES.register("copycat_block", () -> BlockEntityType.Builder.of(CopycatBlockEntity::new, COPYCAT_BLOCK.get()).build(null));

    public static final RegistryObject<Item> TAROT_THE_CHARIOT = ITEMS.register("tarot_the_chariot", () -> new TarotTheChariotItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HAMMER = ITEMS.register("hammer", HammerItem::new);
    public static final RegistryObject<Item> NAIL = ITEMS.register("nail", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MONOCLE = ITEMS.register("monocle", MonocleItem::new);

    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.example_tab"))
            .icon(() -> TAROT_THE_CHARIOT.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(TAROT_THE_CHARIOT.get());
                output.accept(HAMMER.get());
                output.accept(NAIL.get());
                output.accept(COPYCAT_BLOCK_ITEM.get());
                output.accept(MONOCLE.get());
            }).build());

    public MelonAnomalyLibrary(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        SOUNDS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(COPYCAT_BLOCK_ENTITY.get(), CopycatBlockEntityRenderer::new);
        }
    }
}
