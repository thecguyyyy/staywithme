package com.thecguyyyy.staywithme.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LlmConfigRequestPacket {
    public static void encode(LlmConfigRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static LlmConfigRequestPacket decode(FriendlyByteBuf buffer) {
        return new LlmConfigRequestPacket();
    }

    public static void handle(LlmConfigRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                boolean allowed = LlmConfigSnapshotPacket.canManageConfig(player);
                LlmConfigSnapshotPacket.send(player, allowed, true, false,
                        allowed ? "Loaded server API configuration." : "Operator permission is required on dedicated servers.");
            }
        });
        context.setPacketHandled(true);
    }
}
