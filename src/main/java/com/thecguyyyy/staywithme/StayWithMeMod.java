package com.thecguyyyy.staywithme;

import com.mojang.logging.LogUtils;
import com.thecguyyyy.staywithme.command.StayWithMeCommands;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.entity.ModEntities;
import com.thecguyyyy.staywithme.event.StayWithMeChatEvents;
import com.thecguyyyy.staywithme.event.StayWithMeLifecycleEvents;
import com.thecguyyyy.staywithme.item.ModItems;
import com.thecguyyyy.staywithme.network.ModNetworking;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(StayWithMeMod.MOD_ID)
public class StayWithMeMod {
    public static final String MOD_ID = "staywithme";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StayWithMeMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        modEventBus.addListener(ModItems::addCreative);
        StayWithMeConfig.register();
        ModNetworking.register();

        MinecraftForge.EVENT_BUS.addListener(StayWithMeCommands::register);
        MinecraftForge.EVENT_BUS.addListener(StayWithMeChatEvents::onServerChat);
        MinecraftForge.EVENT_BUS.addListener(StayWithMeLifecycleEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(StayWithMeLifecycleEvents::onPlayerLoggedOut);
    }
}
