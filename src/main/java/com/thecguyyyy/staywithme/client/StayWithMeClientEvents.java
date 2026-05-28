package com.thecguyyyy.staywithme.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thecguyyyy.staywithme.StayWithMeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StayWithMeMod.MOD_ID, value = Dist.CLIENT)
public final class StayWithMeClientEvents {
    private StayWithMeClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = StayWithMeMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ClientKeyMappings.OPEN_LLM_CONFIG);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (ClientKeyMappings.OPEN_LLM_CONFIG.consumeClick()) {
            minecraft.setScreen(new StayWithMeLlmConfigScreen(minecraft.screen));
        }
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> command = LiteralArgumentBuilder.<CommandSourceStack>literal("staywithmeconfig")
                .executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new StayWithMeLlmConfigScreen(minecraft.screen));
                    return 1;
                });
        event.getDispatcher().register(command);
    }
}
