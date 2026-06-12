package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PlayerEngineAcquisitionRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetState;
    private final Consumer<String> announcer;

    PlayerEngineAcquisitionRunner(
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

    boolean tryRun(
            String catalogueName,
            int amount,
            String label,
            BooleanSupplier satisfied,
            Runnable onSatisfied
    ) {
        int count = Math.max(1, amount);
        if (satisfied.getAsBoolean()) {
            this.body.stop();
            this.resetState.run();
            onSatisfied.run();
            return true;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            return false;
        }
        if (this.taskState.active()
                && this.body.hasAcquisitionFinished(catalogueName, count)) {
            this.resetState.run();
            return false;
        }
        if (!this.body.acquireItem(catalogueName, count)) {
            this.announcer.accept("PlayerEngine get did not start ("
                    + shortStatus(this.body.highLevelAcquisitionStatus(), 120)
                    + "). Trying Forge fallback.");
            this.resetState.run();
            return false;
        }
        this.taskState.startTask(
                catalogueName,
                count,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to get " + catalogueName + " x" + count + " for " + label + "."
        );
        return true;
    }

    private static String shortStatus(String status, int maxLength) {
        if (status == null || status.isBlank()) {
            return "none";
        }
        if (status.length() <= maxLength) {
            return status;
        }
        return status.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
