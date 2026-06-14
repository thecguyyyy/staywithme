package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class PlayerEngineConfirmedTaskRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;

    PlayerEngineConfirmedTaskRunner(
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
            BooleanSupplier finished,
            BooleanSupplier start,
            Predicate<String> completionConfirmed,
            String unavailableFailure,
            String unconfirmedFinishPrefix,
            String startFailurePrefix,
            String announcement
    ) {
        if (this.taskState.active(stateName) && finished.getAsBoolean()) {
            String status = this.body.highLevelAcquisitionStatus();
            this.body.stop();
            this.resetState.run();
            if (completionConfirmed.test(status)) {
                this.friend.getFriendBrain().completeTask();
            } else {
                this.friend.getFriendBrain().failTask(unconfirmedFinishPrefix
                        + PlayerEngineStatusText.shortStatus(status, 160)
                        + ".");
            }
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            this.friend.getFriendBrain().failTask(unavailableFailure);
            return;
        }
        if (!start.getAsBoolean()) {
            this.friend.getFriendBrain().failTask(startFailurePrefix
                    + PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 160)
                    + ".");
            this.resetState.run();
            return;
        }
        this.taskState.startTask(
                stateName,
                amount,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                announcement
        );
    }

    static boolean callbackFinished(String status) {
        return status != null && status.contains("callback_finished(");
    }

    static boolean callbackFinishedOrAlreadySatisfied(String status) {
        return callbackFinished(status)
                || (status != null && status.contains("already_satisfied("));
    }
}
