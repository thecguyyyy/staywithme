package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.StayWithMeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "2";

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
        CHANNEL.messageBuilder(LlmConfigUpdatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LlmConfigUpdatePacket::encode)
                .decoder(LlmConfigUpdatePacket::decode)
                .consumerMainThread(LlmConfigUpdatePacket::handle)
                .add();
        CHANNEL.messageBuilder(LlmConfigRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LlmConfigRequestPacket::encode)
                .decoder(LlmConfigRequestPacket::decode)
                .consumerMainThread(LlmConfigRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(LlmConfigSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LlmConfigSnapshotPacket::encode)
                .decoder(LlmConfigSnapshotPacket::decode)
                .consumerMainThread(LlmConfigSnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(LlmConnectionTestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LlmConnectionTestPacket::encode)
                .decoder(LlmConnectionTestPacket::decode)
                .consumerMainThread(LlmConnectionTestPacket::handle)
                .add();
        CHANNEL.messageBuilder(CompanionCharacterActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CompanionCharacterActionPacket::encode)
                .decoder(CompanionCharacterActionPacket::decode)
                .consumerMainThread(CompanionCharacterActionPacket::handle)
                .add();
    }
}
