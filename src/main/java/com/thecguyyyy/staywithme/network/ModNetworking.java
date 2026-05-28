package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.StayWithMeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StayWithMeMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(LlmConfigUpdatePacket.class, id++)
                .encoder(LlmConfigUpdatePacket::encode)
                .decoder(LlmConfigUpdatePacket::decode)
                .consumerMainThread(LlmConfigUpdatePacket::handle)
                .add();
    }
}
