package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.client.StayWithMeClientConfigPacketHandler;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class LlmConfigSnapshotPacket {
    private final boolean allowed;
    private final boolean refreshValues;
    private final boolean saveAcknowledged;
    private final boolean testing;
    private final boolean enabled;
    private final boolean apiKeyConfigured;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final int cooldownSeconds;
    private final boolean usePlayerEngineController;
    private final boolean useSmartBrainLibBehaviors;
    private final boolean useBaritoneWhenAvailable;
    private final String status;

    public LlmConfigSnapshotPacket(
            boolean allowed,
            boolean refreshValues,
            boolean saveAcknowledged,
            boolean testing,
            boolean enabled,
            boolean apiKeyConfigured,
            String baseUrl,
            String model,
            int timeoutSeconds,
            int cooldownSeconds,
            boolean usePlayerEngineController,
            boolean useSmartBrainLibBehaviors,
            boolean useBaritoneWhenAvailable,
            String status
    ) {
        this.allowed = allowed;
        this.refreshValues = refreshValues;
        this.saveAcknowledged = saveAcknowledged;
        this.testing = testing;
        this.enabled = enabled;
        this.apiKeyConfigured = apiKeyConfigured;
        this.baseUrl = clean(baseUrl);
        this.model = clean(model);
        this.timeoutSeconds = timeoutSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.usePlayerEngineController = usePlayerEngineController;
        this.useSmartBrainLibBehaviors = useSmartBrainLibBehaviors;
        this.useBaritoneWhenAvailable = useBaritoneWhenAvailable;
        this.status = clean(status);
    }

    public static void send(ServerPlayer player, boolean allowed, boolean refreshValues, boolean testing, String status) {
        send(player, allowed, refreshValues, testing, false, status);
    }

    public static void send(
            ServerPlayer player,
            boolean allowed,
            boolean refreshValues,
            boolean testing,
            boolean saveAcknowledged,
            String status
    ) {
        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new LlmConfigSnapshotPacket(
                        allowed,
                        refreshValues,
                        saveAcknowledged,
                        testing,
                        StayWithMeConfig.LLM_ENABLED.get(),
                        !StayWithMeConfig.LLM_API_KEY.get().isBlank(),
                        StayWithMeConfig.LLM_BASE_URL.get(),
                        StayWithMeConfig.LLM_MODEL.get(),
                        StayWithMeConfig.LLM_TIMEOUT_SECONDS.get(),
                        StayWithMeConfig.LLM_COOLDOWN_SECONDS.get(),
                        StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get(),
                        StayWithMeConfig.USE_SMARTBRAINLIB_BEHAVIORS.get(),
                        StayWithMeConfig.USE_BARITONE_WHEN_AVAILABLE.get(),
                        status
                )
        );
    }

    public static boolean canManageConfig(ServerPlayer player) {
        return player != null
                && player.getServer() != null
                && (player.getServer().isSingleplayer() || player.hasPermissions(2));
    }

    public static void encode(LlmConfigSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.allowed);
        buffer.writeBoolean(packet.refreshValues);
        buffer.writeBoolean(packet.saveAcknowledged);
        buffer.writeBoolean(packet.testing);
        buffer.writeBoolean(packet.enabled);
        buffer.writeBoolean(packet.apiKeyConfigured);
        buffer.writeUtf(packet.baseUrl, 2048);
        buffer.writeUtf(packet.model, 512);
        buffer.writeInt(packet.timeoutSeconds);
        buffer.writeInt(packet.cooldownSeconds);
        buffer.writeBoolean(packet.usePlayerEngineController);
        buffer.writeBoolean(packet.useSmartBrainLibBehaviors);
        buffer.writeBoolean(packet.useBaritoneWhenAvailable);
        buffer.writeUtf(packet.status, 1024);
    }

    public static LlmConfigSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new LlmConfigSnapshotPacket(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(2048),
                buffer.readUtf(512),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(1024)
        );
    }

    public static void handle(LlmConfigSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> StayWithMeClientConfigPacketHandler.handle(packet)
        ));
        context.setPacketHandled(true);
    }

    public boolean allowed() {
        return this.allowed;
    }

    public boolean refreshValues() {
        return this.refreshValues;
    }

    public boolean saveAcknowledged() {
        return this.saveAcknowledged;
    }

    public boolean testing() {
        return this.testing;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public boolean apiKeyConfigured() {
        return this.apiKeyConfigured;
    }

    public String baseUrl() {
        return this.baseUrl;
    }

    public String model() {
        return this.model;
    }

    public int timeoutSeconds() {
        return this.timeoutSeconds;
    }

    public int cooldownSeconds() {
        return this.cooldownSeconds;
    }

    public boolean usePlayerEngineController() {
        return this.usePlayerEngineController;
    }

    public boolean useSmartBrainLibBehaviors() {
        return this.useSmartBrainLibBehaviors;
    }

    public boolean useBaritoneWhenAvailable() {
        return this.useBaritoneWhenAvailable;
    }

    public String status() {
        return this.status;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
