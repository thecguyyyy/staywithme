package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PlayerEngineStartOnlyTaskRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;

    PlayerEngineStartOnlyTaskRunner(
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
            BooleanSupplier start,
            String unavailableFailure,
            String startFailurePrefix,
            String announcement
    ) {
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
}
