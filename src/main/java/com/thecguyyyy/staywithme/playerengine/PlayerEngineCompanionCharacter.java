package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import net.minecraft.server.level.ServerPlayer;

final class PlayerEngineCompanionCharacter {
    private PlayerEngineCompanionCharacter() {
    }

    static Character from(FriendEntity friend) {
        Character fallback = CharacterUtils.DEFAULT_CHARACTER;
        return friend.getOwnerPlayer()
                .map(owner -> fromOwnerMemory(owner, fallback))
                .orElseGet(() -> fromEntityName(friend, fallback));
    }

    private static Character fromOwnerMemory(ServerPlayer owner, Character fallback) {
        FriendMemory memory = JsonMemoryStore.load(owner.getUUID(), owner.getGameProfile().getName());
        String name = companionName(memory.companionName, fallback);
        String ownerName = owner.getGameProfile().getName();
        return new Character(
                characterId(memory.companionId, fallback),
                name,
                shortName(name),
                "I am " + name + ", " + ownerName + "'s Minecraft companion.",
                "You are " + name + ", an embodied Minecraft companion. Use PlayerEngine tasks to act in the world, follow "
                        + ownerName
                        + "'s instructions, and keep behavior grounded in vanilla survival rules.",
                fallback.skinURL(),
                fallback.voiceIds()
        );
    }

    private static Character fromEntityName(FriendEntity friend, Character fallback) {
        String name = companionName(friend.getDisplayName().getString(), fallback);
        return new Character(
                characterId(friend.getUUID().toString(), fallback),
                name,
                shortName(name),
                fallback.greetingInfo(),
                fallback.description(),
                fallback.skinURL(),
                fallback.voiceIds()
        );
    }

    private static String characterId(String rawId, Character fallback) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isBlank()) {
            id = fallback.id() == null || fallback.id().isBlank() ? "staywithme-companion" : fallback.id();
        }
        return limit(id, 64);
    }

    private static String companionName(String rawName, Character fallback) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            name = fallback.shortName() == null || fallback.shortName().isBlank() ? "Companion" : fallback.shortName();
        }
        return limit(name, 32);
    }

    private static String shortName(String name) {
        String trimmed = name == null ? "Companion" : name.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace > 0) {
            trimmed = trimmed.substring(0, firstSpace);
        }
        return limit(trimmed.isBlank() ? "Companion" : trimmed, 16);
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
