package com.thecguyyyy.staywithme.event;

import com.thecguyyyy.staywithme.command.StayWithMeCommands;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;

import java.util.Locale;
import java.util.Optional;

public final class StayWithMeChatEvents {
    private StayWithMeChatEvents() {
    }

    public static void onServerChat(ServerChatEvent event) {
        String prompt = extractPrompt(event.getRawText());
        if (prompt == null || prompt.isBlank()) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        Optional<FriendEntity> friend = StayWithMeCommands.findNearestFriend(player);
        if (friend.isEmpty()) {
            player.sendSystemMessage(Component.translatable("commands.staywithme.no_friend"));
            return;
        }

        StayWithMeCommands.submitAsk(player.createCommandSourceStack(), player, friend.get(), prompt);
    }

    private static String extractPrompt(String rawText) {
        if (rawText == null) {
            return null;
        }

        String trimmed = rawText.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String[] prefixes = {"friend ", "companion ", "buddy ", "@friend ", "伙伴 ", "伙伴，", "伙伴,", "朋友 "};
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
