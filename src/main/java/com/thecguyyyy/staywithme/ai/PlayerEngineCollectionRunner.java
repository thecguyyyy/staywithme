package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;

import java.util.function.IntSupplier;

final class PlayerEngineCollectionRunner {
    private final EmbodiedController body;
    private final PlayerEngineCountedTaskRunner countedRunner;
    private final IntSupplier carriedRouteBlockCount;
    private final IntSupplier carriedFoodUnits;
    private final IntSupplier carriedMeatFoodUnits;

    PlayerEngineCollectionRunner(
            EmbodiedController body,
            PlayerEngineCountedTaskRunner countedRunner,
            IntSupplier carriedRouteBlockCount,
            IntSupplier carriedFoodUnits,
            IntSupplier carriedMeatFoodUnits
    ) {
        this.body = body;
        this.countedRunner = countedRunner;
        this.carriedRouteBlockCount = carriedRouteBlockCount;
        this.carriedFoodUnits = carriedFoodUnits;
        this.carriedMeatFoodUnits = carriedMeatFoodUnits;
    }

    void collectBuildingMaterials(FriendTask task) {
        int requiredBlocks = this.requiredBuildingBlocks(task);
        this.countedRunner.run(
                "building_materials",
                requiredBlocks,
                () -> this.carriedRouteBlockCount.getAsInt() >= requiredBlocks,
                () -> this.body.hasBuildingMaterialsCollectionFinished(requiredBlocks),
                () -> this.body.collectBuildingMaterials(requiredBlocks),
                "Building-material collection needs PlayerEngine right now; Forge fallback does not implement generic throwaway-block gathering.",
                "PlayerEngine building-material collection finished, but I still do not have enough placeable route blocks.",
                "PlayerEngine building-material collection did not start: ",
                "Using PlayerEngine to collect route building materials x" + requiredBlocks + "."
        );
    }

    void collectFood(FriendTask task) {
        int requiredFoodUnits = this.requiredFoodUnits(task);
        this.countedRunner.run(
                "food",
                requiredFoodUnits,
                () -> this.carriedFoodUnits.getAsInt() >= requiredFoodUnits,
                () -> this.body.hasFoodCollectionFinished(requiredFoodUnits),
                () -> this.body.collectFood(requiredFoodUnits),
                "Food collection needs PlayerEngine right now. Ask for a specific food item if you want the local get/craft fallback.",
                "PlayerEngine food collection finished, but I still do not have enough food.",
                "PlayerEngine food collection did not start: ",
                "Using PlayerEngine to collect food units x" + requiredFoodUnits + "."
        );
    }

    void collectMeat(FriendTask task) {
        int requiredFoodUnits = this.requiredFoodUnits(task);
        this.countedRunner.run(
                "meat",
                requiredFoodUnits,
                () -> this.carriedMeatFoodUnits.getAsInt() >= requiredFoodUnits,
                () -> this.body.hasMeatCollectionFinished(requiredFoodUnits),
                () -> this.body.collectMeat(requiredFoodUnits),
                "Meat collection needs PlayerEngine right now; Forge fallback does not implement hunting.",
                "PlayerEngine meat collection finished, but I still do not have enough meat.",
                "PlayerEngine meat collection did not start: ",
                "Using PlayerEngine to collect meat food units x" + requiredFoodUnits + "."
        );
    }

    private int requiredBuildingBlocks(FriendTask task) {
        return Math.max(1, task == null || task.amount() <= 0 ? 32 : task.amount());
    }

    private int requiredFoodUnits(FriendTask task) {
        return Math.max(1, task == null || task.amount() <= 0 ? 10 : task.amount());
    }
}
