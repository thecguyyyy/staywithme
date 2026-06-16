package com.thecguyyyy.staywithme.client;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import com.thecguyyyy.staywithme.network.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.playerengine.PlayerEngineGameId;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
final class PlayerEngineCharacterClient {
    private PlayerEngineCharacterClient() {
    }

    static CompletableFuture<List<CompanionCharacterProfile>> loadCharacters(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            if (player == null) {
                return List.of();
            }
            Character[] characters = CharacterUtils.requestCharacters(player, PlayerEngineGameId.ID);
            if (characters == null || characters.length == 0) {
                return List.of();
            }
            return Arrays.stream(characters)
                    .filter(character -> character != null)
                    .map(PlayerEngineCharacterClient::profile)
                    .toList();
        });
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
