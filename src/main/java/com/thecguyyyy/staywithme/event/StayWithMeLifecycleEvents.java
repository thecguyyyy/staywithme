package com.thecguyyyy.staywithme.event;

import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.entity.CompanionLifecycle;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;

public final class StayWithMeLifecycleEvents {
    private StayWithMeLifecycleEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !StayWithMeConfig.AUTO_SUMMON_COMPANION.get()) {
            return;
        }

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
