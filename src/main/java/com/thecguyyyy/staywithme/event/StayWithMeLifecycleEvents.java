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
            ensureDefaultCompanion(player);
            return;
        }
        CompletableFuture
                .supplyAsync(() -> PlayerEngineCharacterProfiles.requestCharacters(player))
                .whenCompleteAsync((profiles, error) -> {
                    if (error != null) {
                        StayWithMeMod.LOGGER.warn(
                                "Failed to load PlayerEngine companion characters for {}",
                                player.getGameProfile().getName(),
                                error
                        );
                        ensureDefaultCompanion(player);
                        return;
                    }
                    List<CompanionCharacterProfile> safeProfiles = profiles == null ? List.of() : profiles;
                    if (safeProfiles.isEmpty()) {
                        ensureDefaultCompanion(player);
                        return;
                    }
                    CompanionLifecycle.syncSessionCompanionsFor(player, safeProfiles);
                    player.sendSystemMessage(Component.literal("Companions ready: " + safeProfiles.size()));
                }, player.getServer());
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
