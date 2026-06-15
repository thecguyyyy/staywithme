package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

final class PlayerEngineConstructionMaterialRestockRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Consumer<String> announcer;
    private final IntSupplier carriedRouteBlockCount;
    private final Runnable invalidateConstructionPathPlan;
    private final int defaultTarget;
    private int target;
    private String label = "none";

    PlayerEngineConstructionMaterialRestockRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Consumer<String> announcer,
            IntSupplier carriedRouteBlockCount,
            Runnable invalidateConstructionPathPlan,
            int defaultTarget
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.announcer = announcer;
        this.carriedRouteBlockCount = carriedRouteBlockCount;
        this.invalidateConstructionPathPlan = invalidateConstructionPathPlan;
        this.defaultTarget = Math.max(1, defaultTarget);
    }

    boolean active() {
        return this.taskState.active("construction_building_materials");
    }

    boolean tick(String rawLabel) {
        if (this.taskState.active() && !this.active()) {
            return false;
        }
        if (!this.active() && this.carriedRouteBlockCount.getAsInt() > 0) {
            return false;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            return false;
        }

        int targetCount = this.target > 0 ? this.target : this.defaultTarget;
        if (this.active()
                && this.body.hasBuildingMaterialsCollectionFinished(targetCount)) {
            this.body.stop();
            this.resetTaskState.run();
            this.invalidateConstructionPathPlan.run();
            return false;
        }
        if (!this.body.collectBuildingMaterials(targetCount)) {
            this.resetTaskState.run();
            this.invalidateConstructionPathPlan.run();
            return false;
        }

        this.target = targetCount;
        this.label = rawLabel == null || rawLabel.isBlank() ? "construction route" : rawLabel;
        this.taskState.startTask(
                "construction_building_materials",
                targetCount,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "I need route blocks, so I am using PlayerEngine to collect building materials."
        );
        return true;
    }

    void reset() {
        this.target = 0;
        this.label = "none";
    }

    String summary() {
        return this.active() ? this.target + ":" + this.label : "none";
    }
}
