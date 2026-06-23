package com.thecguyyyy.staywithme.network;

import com.thecguyyyy.staywithme.entity.CompanionLifecycle;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.playerengine.PlayerEngineCharacterProfiles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CompanionCharacterActionPacket {
    private final Action action;
    private final CompanionCharacterProfile profile;

    public CompanionCharacterActionPacket(Action action, CompanionCharacterProfile profile) {
        this.action = action == null ? Action.SUMMON : action;
        this.profile = profile == null ? CompanionCharacterProfile.empty() : profile;
    }

    public static CompanionCharacterActionPacket summon(CompanionCharacterProfile profile) {
        return new CompanionCharacterActionPacket(Action.SUMMON, profile);
    }

    public static CompanionCharacterActionPacket despawn(CompanionCharacterProfile profile) {
        return new CompanionCharacterActionPacket(Action.DESPAWN, profile);
    }

    public static void encode(CompanionCharacterActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        CompanionCharacterProfile.encode(packet.profile, buffer);
    }

    public static CompanionCharacterActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        CompanionCharacterProfile profile = CompanionCharacterProfile.decode(buffer);
        return new CompanionCharacterActionPacket(action, profile);
    }

    public static void handle(CompanionCharacterActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (shouldValidateAssignedProfile(packet.profile)) {
                validateAssignedProfileAsync(player, packet);
                return;
            }
            handleValidatedAction(player, packet.action, packet.profile);
        });
        context.setPacketHandled(true);
    }

    private static boolean shouldValidateAssignedProfile(CompanionCharacterProfile profile) {
        return profile != null
                && profile.hasIdentity()
                && IntegrationStatus.isPlayerEngineLoaded()
                && StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get();
    }

    private static void validateAssignedProfileAsync(ServerPlayer player, CompanionCharacterActionPacket packet) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            player.sendSystemMessage(Component.literal("Could not verify PlayerEngine character assignment."));
            return;
        }
        CompletableFuture
                .supplyAsync(() -> PlayerEngineCharacterProfiles.requestCharacters(player))
                .whenCompleteAsync((profiles, error) -> {
                    if (!isPlayerStillOnline(player)) {
                        return;
                    }
                    if (error != null) {
                        player.sendSystemMessage(Component.literal("Could not verify PlayerEngine character assignment."));
                        return;
                    }
                    Optional<CompanionCharacterProfile> assignedProfile = findAssignedProfile(packet.profile, profiles);
                    if (assignedProfile.isEmpty()) {
                        player.sendSystemMessage(Component.literal("That PlayerEngine character is no longer assigned to you."));
                        return;
                    }
                    handleValidatedAction(player, packet.action, assignedProfile.get());
                }, server);
    }

    private static Optional<CompanionCharacterProfile> findAssignedProfile(
            CompanionCharacterProfile requested,
            List<CompanionCharacterProfile> assignedProfiles
    ) {
        if (requested == null || assignedProfiles == null || assignedProfiles.isEmpty()) {
            return Optional.empty();
        }
        String requestedKey = requested.key();
        return assignedProfiles.stream()
                .filter(profile -> profile != null && profile.hasIdentity() && profile.key().equals(requestedKey))
                .findFirst();
    }

    private static boolean isPlayerStillOnline(ServerPlayer player) {
        return player != null
                && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void handleValidatedAction(ServerPlayer player, Action action, CompanionCharacterProfile profile) {
        if (action == Action.DESPAWN) {
            if (CompanionLifecycle.dismissCompanion(player, profile)) {
                player.sendSystemMessage(Component.translatable("commands.staywithme.dismissed"));
            } else {
                player.sendSystemMessage(Component.translatable("commands.staywithme.no_friend"));
            }
            return;
        }

        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        profile.applyTo(memory);
        JsonMemoryStore.save(memory);

        Optional<FriendEntity> companion = CompanionLifecycle.ensureCompanionFor(player, profile, true);
        if (companion.isPresent()) {
            player.sendSystemMessage(Component.literal("Companion selected: " + profile.displayName()));
        } else {
            player.sendSystemMessage(Component.literal("Could not create companion entity."));
        }
    }

    public enum Action {
        SUMMON,
        DESPAWN
    }
}
