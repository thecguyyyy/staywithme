package com.thecguyyyy.staywithme.crafting;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public record RecipeExecutionContext(ServerLevel level, FriendEntity friend, BlockPos stationPos) {
    public Optional<BlockPos> station() {
        return Optional.ofNullable(this.stationPos);
    }
}
