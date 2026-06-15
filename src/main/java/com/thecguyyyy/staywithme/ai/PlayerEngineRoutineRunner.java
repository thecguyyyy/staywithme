package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import net.minecraft.server.level.ServerLevel;

final class PlayerEngineRoutineRunner {
    private final EmbodiedController body;
    private final PlayerEngineStartOnlyTaskRunner startOnlyRunner;
    private final PlayerEngineCountedTaskRunner countedRunner;

    PlayerEngineRoutineRunner(
            EmbodiedController body,
            PlayerEngineStartOnlyTaskRunner startOnlyRunner,
            PlayerEngineCountedTaskRunner countedRunner
    ) {
        this.body = body;
        this.startOnlyRunner = startOnlyRunner;
        this.countedRunner = countedRunner;
    }

    void fish() {
        this.startOnlyRunner.run(
                "fish",
                1,
                this.body::fish,
                "Fishing needs PlayerEngine right now; Forge fallback does not implement fishing.",
                "PlayerEngine fishing did not start: ",
                "Using PlayerEngine to fish. Stop me when you have enough."
        );
    }

    void farm(FriendTask task) {
        int range = Math.max(1, task == null || task.amount() <= 0 ? 10 : task.amount());
        this.startOnlyRunner.run(
                "farm",
                range,
                () -> this.body.farm(range),
                "Farming needs PlayerEngine right now; Forge fallback does not implement crop farming.",
                "PlayerEngine farming did not start: ",
                "Using PlayerEngine to farm nearby crops within range " + range + ". Stop me when you are done."
        );
    }

    void sleepThroughNight(ServerLevel level) {
        this.countedRunner.run(
                "sleep_through_night",
                1,
                () -> isDaytime(level),
                this.body::hasSleepThroughNightFinished,
                this.body::sleepThroughNight,
                "Sleeping through night needs PlayerEngine right now; Forge fallback does not implement bed placement/sleeping.",
                "PlayerEngine sleep finished, but it is still night.",
                "PlayerEngine sleep did not start: ",
                "Using PlayerEngine to sleep through the night."
        );
    }

    private static boolean isDaytime(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return time >= 0L && time < 13000L;
    }
}
