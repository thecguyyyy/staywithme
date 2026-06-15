package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.Consumer;

final class PlayerEngineExploreRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Runnable resetLocalExploreTarget;
    private final Consumer<String> announcer;

    PlayerEngineExploreRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Runnable resetLocalExploreTarget,
            Consumer<String> announcer
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.resetLocalExploreTarget = resetLocalExploreTarget;
        this.announcer = announcer;
    }

    boolean tryRun(int distance) {
        if (this.taskState.active() && this.body.hasExploreFinished(distance)) {
            this.body.stop();
            this.resetTaskState.run();
            this.resetLocalExploreTarget.run();
            this.friend.getFriendBrain().completeTask();
            return true;
        }
        if (this.body.canUseHighLevelAcquisition() && this.body.explore(distance)) {
            this.taskState.startTask(
                    "explore",
                    distance,
                    this.friend,
                    FriendState.EXECUTING_TASK,
                    this.announcer,
                    "Using PlayerEngine to explore about " + distance + " blocks away."
            );
            return true;
        }
        return false;
    }
}
