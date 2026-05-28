package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LlmConfigUpdatePacket {
    private final boolean enabled;
    private final String baseUrl;
    private final boolean updateApiKey;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final int cooldownSeconds;
    private final boolean usePlayerEngineController;
    private final boolean useSmartBrainLibBehaviors;
    private final boolean useBaritoneWhenAvailable;

    public LlmConfigUpdatePacket(
            boolean enabled,
            String baseUrl,
            boolean updateApiKey,
            String apiKey,
            String model,
            int timeoutSeconds,
            int cooldownSeconds,
            boolean usePlayerEngineController,
            boolean useSmartBrainLibBehaviors,
            boolean useBaritoneWhenAvailable
    ) {
        this.enabled = enabled;
        this.baseUrl = clean(baseUrl);
        this.updateApiKey = updateApiKey;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = clean(model);
        this.timeoutSeconds = timeoutSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.usePlayerEngineController = usePlayerEngineController;
        this.useSmartBrainLibBehaviors = useSmartBrainLibBehaviors;
        this.useBaritoneWhenAvailable = useBaritoneWhenAvailable;
    }

    public static void encode(LlmConfigUpdatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled);
        buffer.writeUtf(packet.baseUrl, 2048);
        buffer.writeBoolean(packet.updateApiKey);
        buffer.writeUtf(packet.apiKey, 4096);
        buffer.writeUtf(packet.model, 512);
        buffer.writeInt(packet.timeoutSeconds);
        buffer.writeInt(packet.cooldownSeconds);
        buffer.writeBoolean(packet.usePlayerEngineController);
        buffer.writeBoolean(packet.useSmartBrainLibBehaviors);
        buffer.writeBoolean(packet.useBaritoneWhenAvailable);
    }

    public static LlmConfigUpdatePacket decode(FriendlyByteBuf buffer) {
        return new LlmConfigUpdatePacket(
                buffer.readBoolean(),
                buffer.readUtf(2048),
                buffer.readBoolean(),
                buffer.readUtf(4096),
                buffer.readUtf(512),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    public static void handle(LlmConfigUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            boolean allowed = player.getServer() != null && (player.getServer().isSingleplayer() || player.hasPermissions(2));
            if (!allowed) {
                player.sendSystemMessage(Component.literal("StayWithMe config requires operator permission on dedicated servers."));
                return;
            }

            StayWithMeConfig.LLM_ENABLED.set(packet.enabled);
            StayWithMeConfig.LLM_BASE_URL.set(packet.baseUrl.isBlank() ? "https://api.openai.com/v1" : packet.baseUrl);
            if (packet.updateApiKey) {
                StayWithMeConfig.LLM_API_KEY.set(packet.apiKey);
            }
            StayWithMeConfig.LLM_MODEL.set(packet.model.isBlank() ? "gpt-4o-mini" : packet.model);
            StayWithMeConfig.LLM_TIMEOUT_SECONDS.set(clamp(packet.timeoutSeconds, 3, 120));
            StayWithMeConfig.LLM_COOLDOWN_SECONDS.set(clamp(packet.cooldownSeconds, 0, 300));
            StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.set(packet.usePlayerEngineController);
            StayWithMeConfig.USE_SMARTBRAINLIB_BEHAVIORS.set(packet.useSmartBrainLibBehaviors);
            StayWithMeConfig.USE_BARITONE_WHEN_AVAILABLE.set(packet.useBaritoneWhenAvailable);
            StayWithMeConfig.SERVER_SPEC.save();

            player.sendSystemMessage(Component.literal("StayWithMe API config saved. Restart the world if an integration toggle does not affect existing companions."));
        });
        context.setPacketHandled(true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
