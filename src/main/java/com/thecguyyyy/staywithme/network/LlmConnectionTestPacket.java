package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.llm.LlmRequest;
import com.thecguyyyy.staywithme.llm.OpenAICompatibleClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LlmConnectionTestPacket {
    private static final String SYSTEM_PROMPT = """
            Return exactly one JSON object and nothing else:
            {"action":"SAY","target":"","amount":1,"message":"ok","reason":"connection test"}
            """;

    public static void encode(LlmConnectionTestPacket packet, FriendlyByteBuf buffer) {
    }

    public static LlmConnectionTestPacket decode(FriendlyByteBuf buffer) {
        return new LlmConnectionTestPacket();
    }

    public static void handle(LlmConnectionTestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            boolean allowed = LlmConfigSnapshotPacket.canManageConfig(player);
            if (!allowed) {
                LlmConfigSnapshotPacket.send(player, false, false, false,
                        "Operator permission is required to test the server API configuration.");
                return;
            }
            if (StayWithMeConfig.LLM_API_KEY.get().isBlank()) {
                LlmConfigSnapshotPacket.send(player, true, false, false,
                        "Configure and save an API key before testing.");
                return;
            }

            LlmConfigSnapshotPacket.send(player, true, false, true, "Testing API connection...");
            MinecraftServer server = player.getServer();
            new OpenAICompatibleClient().plan(new LlmRequest(
                    StayWithMeConfig.LLM_BASE_URL.get(),
                    StayWithMeConfig.LLM_API_KEY.get(),
                    StayWithMeConfig.LLM_MODEL.get(),
                    StayWithMeConfig.LLM_TIMEOUT_SECONDS.get(),
                    SYSTEM_PROMPT,
                    "Return the connection-test JSON now."
            )).whenComplete((response, error) -> {
                if (server == null) {
                    return;
                }
                server.execute(() -> {
                    if (error == null && response != null && response.action() != null) {
                        LlmConfigSnapshotPacket.send(player, true, false, false, "API connection test passed.");
                        return;
                    }
                    LlmConfigSnapshotPacket.send(player, true, false, false,
                            "API connection test failed: " + shortMessage(error));
                });
            });
        });
        context.setPacketHandled(true);
    }

    private static String shortMessage(Throwable error) {
        if (error == null) {
            return "empty response";
        }
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() <= 180 ? message : message.substring(0, 180);
    }
}
