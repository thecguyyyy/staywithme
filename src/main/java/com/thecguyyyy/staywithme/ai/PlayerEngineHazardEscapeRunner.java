package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlayerEngineHazardEscapeRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineFallbackTaskRunner fallbackTaskRunner;
    private final LocalHazardEscapeFallback hazardFallback;
    private final Function<BlockPos, String> positionFormatter;
    private final Consumer<String> announcer;
    private final double speed;

    PlayerEngineHazardEscapeRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineFallbackTaskRunner fallbackTaskRunner,
            LocalHazardEscapeFallback hazardFallback,
            Function<BlockPos, String> positionFormatter,
            Consumer<String> announcer,
            double speed
    ) {
        this.body = body;
        this.friend = friend;
        this.fallbackTaskRunner = fallbackTaskRunner;
        this.hazardFallback = hazardFallback;
        this.positionFormatter = positionFormatter;
        this.announcer = announcer;
        this.speed = speed;
    }

    void getOutOfWater(ServerLevel level) {
        this.fallbackTaskRunner.run(
                "get_out_of_water",
                0,
                () -> !this.friend.isInWater() && this.friend.onGround(),
                this.body::hasGetOutOfWaterFinished,
                this.body::getOutOfWater,
                () -> this.tryForgeGetOutOfWater(level),
                "I cannot find a dry reachable stand position nearby.",
                "PlayerEngine water escape finished, but I am still not safely on dry ground.",
                null,
                status -> "PlayerEngine water escape did not start: " + status + ".",
                status -> "PlayerEngine water escape did not start (" + status + "), so I am using a local dry-ground fallback.",
                "Using PlayerEngine to get out of water."
        );
    }

    void escapeLava(ServerLevel level) {
        this.fallbackTaskRunner.run(
                "escape_lava",
                0,
                () -> !this.friend.isInLava() && !this.friend.isOnFire(),
                this.body::hasEscapeLavaFinished,
                this.body::escapeLava,
                () -> this.tryForgeEscapeLava(level),
                "I cannot find a reachable lava-safe stand position nearby.",
                "PlayerEngine lava escape finished, but I am still in danger.",
                "PlayerEngine lava escape ended with danger remaining, so I am moving to local safe ground.",
                status -> "PlayerEngine lava escape did not start: " + status + ".",
                status -> "PlayerEngine lava escape did not start (" + status + "), so I am using a local safe-ground fallback.",
                "Using PlayerEngine to escape lava."
        );
    }

    private boolean tryForgeGetOutOfWater(ServerLevel level) {
        Optional<BlockPos> target = this.hazardFallback.findDryStandPosition(level, 12);
        if (target.isEmpty()) {
            return false;
        }
        this.body.moveToNearby(target.get(), this.speed);
        this.friend.setFriendState(FriendState.EXECUTING_TASK);
        this.announcer.accept("Moving to dry ground at " + this.positionFormatter.apply(target.get()) + ".");
        return true;
    }

    private boolean tryForgeEscapeLava(ServerLevel level) {
        Optional<BlockPos> target = this.hazardFallback.findLavaSafeStandPosition(level, 12);
        if (target.isEmpty()) {
            return false;
        }
        this.body.moveToNearby(target.get(), this.speed);
        this.friend.setFriendState(FriendState.EXECUTING_TASK);
        this.announcer.accept("Moving away from lava to " + this.positionFormatter.apply(target.get()) + ".");
        return true;
    }
}
