package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

final class PlayerEngineFuelRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Consumer<String> announcer;
    private final IntSupplier carriedFuelCount;
    private final BooleanSupplier fallbackActive;
    private final Consumer<Boolean> setFallbackActive;
    private final FuelFallback fallback;

    PlayerEngineFuelRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Consumer<String> announcer,
            IntSupplier carriedFuelCount,
            BooleanSupplier fallbackActive,
            Consumer<Boolean> setFallbackActive,
            FuelFallback fallback
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.announcer = announcer;
        this.carriedFuelCount = carriedFuelCount;
        this.fallbackActive = fallbackActive;
        this.setFallbackActive = setFallbackActive;
        this.fallback = fallback;
    }

    void run(FriendTask task, int requiredFuelItems) {
        if (this.carriedFuelCount.getAsInt() >= requiredFuelItems) {
            this.body.stop();
            this.resetTaskState.run();
            this.setFallbackActive.accept(false);
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (this.fallbackActive.getAsBoolean()) {
            this.fallback.run(task, requiredFuelItems, "continuing charcoal fallback");
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            this.fallback.run(task, requiredFuelItems, "PlayerEngine is unavailable");
            return;
        }
        if (this.taskState.active("fuel")
                && this.body.hasFuelCollectionFinished(requiredFuelItems)) {
            this.resetTaskState.run();
            if (this.carriedFuelCount.getAsInt() >= requiredFuelItems) {
                this.friend.getFriendBrain().completeTask();
            } else {
                this.fallback.run(
                        task,
                        requiredFuelItems,
                        "PlayerEngine fuel collection finished without enough coal or charcoal"
                );
            }
            return;
        }
        if (!this.body.collectFuel(requiredFuelItems)) {
            this.resetTaskState.run();
            this.fallback.run(
                    task,
                    requiredFuelItems,
                    "PlayerEngine fuel collection did not start: "
                            + PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 120)
            );
            return;
        }
        this.taskState.startTask(
                "fuel",
                requiredFuelItems,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to collect fuel items x" + requiredFuelItems + "."
        );
    }

    interface FuelFallback {
        void run(FriendTask task, int requiredFuelItems, String reason);
    }
}
