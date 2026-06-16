package com.thecguyyyy.staywithme.client;

import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.playerengine.PlayerEngineCharacterProfiles;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@OnlyIn(Dist.CLIENT)
final class PlayerEngineCharacterClient {
    private PlayerEngineCharacterClient() {
    }

    static CompletableFuture<List<CompanionCharacterProfile>> loadCharacters(Player player) {
        return CompletableFuture.supplyAsync(() -> PlayerEngineCharacterProfiles.requestCharacters(player));
    }
}
