package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.List;

public final class PlayerEngineCharacterProfiles {
    private PlayerEngineCharacterProfiles() {
    }

    public static List<CompanionCharacterProfile> requestCharacters(Player player) {
        if (player == null) {
            return List.of();
        }
        Character[] characters = CharacterUtils.requestCharacters(player, PlayerEngineGameId.ID);
        if (characters == null || characters.length == 0) {
            return List.of();
        }
        return Arrays.stream(characters)
                .filter(character -> character != null)
                .map(PlayerEngineCharacterProfiles::profile)
                .toList();
    }

    private static CompanionCharacterProfile profile(Character character) {
        return new CompanionCharacterProfile(
                character.id(),
                character.name(),
                character.shortName(),
                character.greetingInfo(),
                character.description(),
                character.skinURL(),
                character.voiceIds() == null ? List.of() : Arrays.asList(character.voiceIds())
        );
    }
}
