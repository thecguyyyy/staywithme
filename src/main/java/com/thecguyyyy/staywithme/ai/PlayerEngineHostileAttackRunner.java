package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

final class PlayerEngineHostileAttackRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineStartOnlyTaskRunner startOnlyRunner;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Consumer<String> announcer;
    private final Consumer<String> speaker;

    PlayerEngineHostileAttackRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineStartOnlyTaskRunner startOnlyRunner,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Consumer<String> announcer,
            Consumer<String> speaker
    ) {
        this.body = body;
        this.friend = friend;
        this.startOnlyRunner = startOnlyRunner;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.announcer = announcer;
        this.speaker = speaker;
    }

    void protectPlayer() {
        this.startOnlyRunner.run(
                "protect_player",
                0,
                this.body::protectPlayer,
                "Protecting with continuous hostile cleanup needs PlayerEngine right now; use /staywithme attack for a single local fallback attack.",
                "PlayerEngine protect task did not start: ",
                "Using PlayerEngine to protect the nearby area until stopped."
        );
    }

    boolean tryRun(LivingEntity target) {
        if (target == null) {
            return false;
        }

        String signature = "attack:" + target.getUUID();
        if (this.taskState.active() && this.body.hasAttackTargetFinished(target)) {
            String status = this.body.highLevelAcquisitionStatus();
            this.body.stop();
            this.resetTaskState.run();
            if (!target.isAlive() || (status != null && status.contains("callback_finished("))) {
                this.speaker.accept("The hostile mob is down.");
                this.friend.getFriendBrain().completeTask();
            } else {
                this.friend.getFriendBrain().failTask("PlayerEngine attack stopped before the hostile was defeated: "
                        + PlayerEngineStatusText.shortStatus(status, 160)
                        + ".");
            }
            return true;
        }

        if (!this.body.canUseHighLevelAcquisition()) {
            return false;
        }

        if (!this.body.attackTarget(target)) {
            if (this.taskState.active(signature)) {
                this.resetTaskState.run();
            }
            return false;
        }

        this.taskState.startTask(
                signature,
                0,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to attack the nearby hostile."
        );
        return true;
    }
}
