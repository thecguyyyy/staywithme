package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.ai.workflow.WorkStep;
import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.Objects;
import java.util.function.Consumer;

final class PlayerEngineYLevelRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Consumer<String> announcer;
    private String stepTarget;
    private int targetY = Integer.MIN_VALUE;
    private boolean attempted;

    PlayerEngineYLevelRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Consumer<String> announcer
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.announcer = announcer;
    }

    boolean tryReach(WorkStep step, int yLevel) {
        String currentStepTarget = step == null ? "" : step.target();
        if (!Objects.equals(this.stepTarget, currentStepTarget)
                || this.targetY != yLevel) {
            this.stepTarget = currentStepTarget;
            this.targetY = yLevel;
            this.attempted = false;
        }

        if (this.taskState.active("goto_y")
                && this.taskState.amount() == yLevel) {
            if (!this.body.hasGoToYLevelFinished(yLevel)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.markRunning(step, yLevel);
                return true;
            }
            this.resetTaskState.run();
            this.attempted = true;
            this.announcer.accept("PlayerEngine Y-layer move ended; falling back to local staircase if the layer is not reached.");
            return false;
        }

        if (this.attempted || !this.body.canUseHighLevelAcquisition()) {
            return false;
        }

        this.attempted = true;
        if (!this.body.goToYLevel(yLevel)) {
            this.announcer.accept("PlayerEngine Y-layer movement did not start ("
                    + PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 120)
                    + "). Digging a local staircase instead.");
            this.resetTaskState.run();
            return false;
        }

        this.taskState.startTask(
                "goto_y",
                yLevel,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to reach mining Y layer " + yLevel + " before local staircase fallback."
        );
        this.markRunning(step, yLevel);
        return true;
    }

    void reset() {
        this.stepTarget = null;
        this.targetY = Integer.MIN_VALUE;
        this.attempted = false;
    }

    private void markRunning(WorkStep step, int yLevel) {
        if (step != null) {
            step.running("PlayerEngine moving to Y " + yLevel);
        }
    }
}
