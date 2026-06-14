package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlayerEngineFallbackTaskRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;

    PlayerEngineFallbackTaskRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetState,
            Consumer<String> announcer
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetState = resetState;
        this.announcer = announcer;
    }

    void run(
            String stateName,
            int amount,
            BooleanSupplier satisfied,
            BooleanSupplier finished,
            BooleanSupplier start,
            BooleanSupplier fallback,
            String unavailableFallbackFailure,
            String finishedButUnsatisfiedFailure,
            String finishedFallbackMessage,
            Function<String, String> startFailure,
            Function<String, String> startFallbackMessage,
            String announcement
    ) {
        int count = Math.max(0, amount);
        if (satisfied.getAsBoolean()) {
            this.body.stop();
            this.resetState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            if (fallback.getAsBoolean()) {
                return;
            }
            this.friend.getFriendBrain().failTask(unavailableFallbackFailure);
            return;
        }
        if (this.taskState.active(stateName) && finished.getAsBoolean()) {
            this.resetState.run();
            if (satisfied.getAsBoolean()) {
                this.friend.getFriendBrain().completeTask();
            } else if (fallback.getAsBoolean()) {
                this.announceIfPresent(finishedFallbackMessage);
            } else {
                this.friend.getFriendBrain().failTask(finishedButUnsatisfiedFailure);
            }
            return;
        }
        if (!start.getAsBoolean()) {
            String status = PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 160);
            this.resetState.run();
            if (fallback.getAsBoolean()) {
                this.announceIfPresent(startFallbackMessage.apply(status));
                return;
            }
            this.friend.getFriendBrain().failTask(startFailure.apply(status));
            return;
        }
        this.taskState.startTask(
                stateName,
                count,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                announcement
        );
    }

    private void announceIfPresent(String message) {
        if (message != null && !message.isBlank()) {
            this.announcer.accept(message);
        }
    }
}
