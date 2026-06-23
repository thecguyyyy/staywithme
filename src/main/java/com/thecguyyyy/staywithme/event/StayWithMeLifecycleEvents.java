package com.thecguyyyy.staywithme.event;

import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.entity.CompanionLifecycle;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.playerengine.PlayerEngineCharacterProfiles;
import com.thecguyyyy.staywithme.StayWithMeMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class StayWithMeLifecycleEvents {
    private StayWithMeLifecycleEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !StayWithMeConfig.AUTO_SUMMON_COMPANION.get()) {
            return;
        }

        if (IntegrationStatus.isPlayerEngineLoaded() && StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get()) {
            summonPlayerEngineCompanionsAsync(player);
            return;
        }

        ensureDefaultCompanion(player);
    }

    private static void summonPlayerEngineCompanionsAsync(ServerPlayer player) {
        if (player.getServer() == null) {
            player.sendSystemMessage(Component.literal("Could not request PlayerEngine companion characters."));
            return;
        }
        CompletableFuture
                .supplyAsync(() -> PlayerEngineCharacterProfiles.requestCharacters(player))
                .whenCompleteAsync((profiles, error) -> {
                    if (!isPlayerStillOnline(player)) {
                        return;
                    }
                    if (error != null) {
                        StayWithMeMod.LOGGER.warn(
                                "Failed to load PlayerEngine companion characters for {}",
                                player.getGameProfile().getName(),
                                error
                        );
                        player.sendSystemMessage(Component.literal("Could not load PlayerEngine companion characters."));
                        return;
                    }
                    List<CompanionCharacterProfile> safeProfiles = profiles == null ? List.of() : profiles;
                    CompanionLifecycle.syncSessionCompanionsFor(player, safeProfiles);
                    long assignedCount = safeProfiles.stream()
                            .filter(profile -> profile != null && profile.hasIdentity())
                            .count();
                    if (assignedCount <= 0L) {
                        player.sendSystemMessage(Component.literal("No PlayerEngine companion characters are assigned."));
                        return;
                    }
                    player.sendSystemMessage(Component.literal("Companions ready: " + assignedCount));
                }, player.getServer());
    }

    private static boolean isPlayerStillOnline(ServerPlayer player) {
        return player != null
                && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void ensureDefaultCompanion(ServerPlayer player) {
        Optional<FriendEntity> companion = CompanionLifecycle.ensureCompanionFor(player);
        if (companion.isPresent()) {
            player.sendSystemMessage(Component.translatable("commands.staywithme.companion_ready"));
        } else {
            player.sendSystemMessage(Component.literal("Could not create companion entity."));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !StayWithMeConfig.DISMISS_AUTO_SUMMONED_COMPANIONS_ON_LOGOUT.get()) {
            return;
        }
        CompanionLifecycle.dismissSessionCompanion(player);
    }
}
