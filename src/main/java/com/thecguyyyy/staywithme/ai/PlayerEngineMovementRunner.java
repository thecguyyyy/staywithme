package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

final class PlayerEngineMovementRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;

    PlayerEngineMovementRunner(EmbodiedController body, FriendEntity friend) {
        this.body = body;
        this.friend = friend;
    }

    boolean followPlayer(String playerName, double followDistance) {
        if (!this.body.canUseHighLevelAcquisition()
                || !this.body.followPlayer(playerName, followDistance)) {
            return false;
        }
        this.friend.setFriendState(FriendState.FOLLOWING);
        return true;
    }

    boolean returnToEntity(Entity target, double closeEnoughDistance) {
        if (!this.body.canUseHighLevelAcquisition()
                || !this.body.returnToEntity(target, closeEnoughDistance)) {
            return false;
        }
        this.friend.setFriendState(FriendState.RETURNING);
        return true;
    }

    boolean goToBlock(BlockPos pos, double closeEnoughDistance) {
        if (!this.body.canUseHighLevelAcquisition()
                || !this.body.goToBlock(pos, closeEnoughDistance)) {
            return false;
        }
        this.friend.setFriendState(FriendState.EXECUTING_TASK);
        return true;
    }
}
