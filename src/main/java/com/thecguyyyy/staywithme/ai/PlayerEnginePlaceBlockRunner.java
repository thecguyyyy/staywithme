package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlayerEnginePlaceBlockRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;
    private final Function<BlockPos, String> positionFormatter;

    PlayerEnginePlaceBlockRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetState,
            Consumer<String> announcer,
            Function<BlockPos, String> positionFormatter
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetState = resetState;
        this.announcer = announcer;
        this.positionFormatter = positionFormatter;
    }

    boolean tick(BlockPos position, String blockTarget, BooleanSupplier satisfied) {
        if (satisfied.getAsBoolean()) {
            this.body.stop();
            this.resetState.run();
            this.friend.getFriendBrain().completeTask();
            return true;
        }
        String stateName = "place:" + blockTarget;
        if (this.taskState.active(stateName)
                && this.body.hasPlaceBlockAtFinished(position, blockTarget)) {
            this.body.stop();
            this.resetState.run();
            if (satisfied.getAsBoolean()) {
                this.friend.getFriendBrain().completeTask();
            } else {
                this.friend.getFriendBrain().failTask("PlayerEngine place-block task finished, but the target block was not placed.");
            }
            return true;
        }
        if (this.body.canUseHighLevelAcquisition()
                && this.body.placeBlockAt(position, blockTarget)) {
            this.taskState.startTask(
                    stateName,
                    1,
                    this.friend,
                    FriendState.EXECUTING_TASK,
                    this.announcer,
                    "Using PlayerEngine to place " + blockTarget + " at " + this.positionFormatter.apply(position) + "."
            );
            return true;
        }
        return false;
    }
}
