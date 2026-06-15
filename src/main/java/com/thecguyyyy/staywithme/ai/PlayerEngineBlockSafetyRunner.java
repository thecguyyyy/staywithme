package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlayerEngineBlockSafetyRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;
    private final Function<BlockPos, String> positionFormatter;
    private final LocalBlockSafetyFallback fallback;

    PlayerEngineBlockSafetyRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetState,
            Consumer<String> announcer,
            Function<BlockPos, String> positionFormatter,
            LocalBlockSafetyFallback fallback
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetState = resetState;
        this.announcer = announcer;
        this.positionFormatter = positionFormatter;
        this.fallback = fallback;
    }

    void clearLiquid(ServerLevel level, BlockPos liquidPos, FriendTask task, double speed) {
        if (LocalBlockSafetyFallback.isLiquidCleared(level, liquidPos)) {
            this.body.stop();
            this.resetState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }

        if (this.taskState.active("clear_liquid")) {
            if (this.body.hasClearLiquidFinished(liquidPos)) {
                this.body.stop();
                this.resetState.run();
                if (LocalBlockSafetyFallback.isLiquidCleared(level, liquidPos)) {
                    this.friend.getFriendBrain().completeTask();
                } else {
                    this.friend.getFriendBrain().failTask("PlayerEngine clear-liquid task finished, but liquid remains at "
                            + this.positionFormatter.apply(liquidPos)
                            + ".");
                }
                return;
            }
            this.friend.setFriendState(FriendState.EXECUTING_TASK);
            return;
        }

        if (this.body.canUseHighLevelAcquisition() && this.body.clearLiquid(liquidPos)) {
            this.taskState.startTask(
                    "clear_liquid",
                    1,
                    this.friend,
                    FriendState.EXECUTING_TASK,
                    this.announcer,
                    "Using PlayerEngine to clear liquid at " + this.positionFormatter.apply(liquidPos) + "."
            );
            return;
        }

        this.applyFallbackResult(this.fallback.tickClearLiquid(level, liquidPos, task, speed));
    }

    PassageClearResult tryClearPassageLiquid(ServerLevel level, BlockPos liquidPos, String label) {
        if (LocalBlockSafetyFallback.isLiquidCleared(level, liquidPos)) {
            this.body.stop();
            this.resetState.run();
            return PassageClearResult.CLEARED;
        }

        String stateName = "passage_clear_liquid:" + liquidPos.toShortString();
        if (this.taskState.active(stateName)) {
            if (this.body.hasClearLiquidFinished(liquidPos)) {
                this.body.stop();
                this.resetState.run();
                return LocalBlockSafetyFallback.isLiquidCleared(level, liquidPos)
                        ? PassageClearResult.CLEARED
                        : PassageClearResult.UNAVAILABLE_OR_FAILED;
            }
            this.friend.setFriendState(FriendState.EXECUTING_TASK);
            return PassageClearResult.WORKING;
        }

        if (!this.body.canUseHighLevelAcquisition()) {
            return PassageClearResult.UNAVAILABLE_OR_FAILED;
        }

        if (!this.body.clearLiquid(liquidPos)) {
            if (this.taskState.active(stateName)) {
                this.resetState.run();
            }
            return PassageClearResult.UNAVAILABLE_OR_FAILED;
        }

        this.taskState.startTask(
                stateName,
                1,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to clear liquid at "
                        + this.positionFormatter.apply(liquidPos)
                        + " before digging "
                        + this.safeLabel(label)
                        + "."
        );
        return PassageClearResult.WORKING;
    }

    void putOutFire(ServerLevel level, int range, double speed) {
        LocalBlockSafetyFallback.FireTargetSelection selection = this.fallback.selectFireTarget(level, range);
        if (selection.previousTargetCleared()
                && this.taskState.active("put_out_fire")) {
            this.body.stop();
            this.resetState.run();
        }

        Optional<BlockPos> fireTarget = selection.target();
        if (fireTarget.isEmpty()) {
            this.body.stop();
            this.resetState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }

        if (this.taskState.active("put_out_fire")) {
            if (this.body.hasPutOutFireFinished(fireTarget.get())) {
                this.body.stop();
                this.resetState.run();
                this.fallback.resetFireTarget();
            } else {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                return;
            }
        }

        fireTarget = this.fallback.selectFireTarget(level, range).target();
        if (fireTarget.isEmpty()) {
            this.friend.getFriendBrain().completeTask();
            return;
        }

        if (this.body.canUseHighLevelAcquisition() && this.body.putOutFire(fireTarget.get())) {
            this.taskState.startTask(
                    "put_out_fire",
                    range,
                    this.friend,
                    FriendState.EXECUTING_TASK,
                    this.announcer,
                    "Using PlayerEngine to put out nearby fire."
            );
            return;
        }

        this.applyFallbackResult(this.fallback.tickPutOutFire(level, range, speed));
    }

    private void applyFallbackResult(LocalBlockSafetyFallback.ActionResult result) {
        if (result.status() == LocalBlockSafetyFallback.Status.DONE) {
            this.body.stop();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (result.status() == LocalBlockSafetyFallback.Status.WORKING) {
            this.friend.setFriendState(FriendState.EXECUTING_TASK);
            if (result.message() != null && !result.message().isBlank()) {
                this.announcer.accept(result.message());
            }
            return;
        }
        this.friend.getFriendBrain().failTask(result.message() == null || result.message().isBlank()
                ? "Local block safety fallback failed."
                : result.message());
    }

    private String safeLabel(String label) {
        return label == null || label.isBlank() ? "the passage" : label;
    }

    enum PassageClearResult {
        CLEARED,
        WORKING,
        UNAVAILABLE_OR_FAILED
    }
}
