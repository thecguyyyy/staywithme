package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import com.thecguyyyy.staywithme.survival.SurvivalWorldInteractor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Supplier;

public class MineActionAdapter {
    private final EmbodiedController body;
    private final FriendInteractionProvider interaction;

    public MineActionAdapter(EmbodiedController body, FriendInteractionProvider interaction) {
        this.body = body;
        this.interaction = interaction;
    }

    public MineResult mineOne(
            ServerLevel level,
            BlockPos target,
            Supplier<Optional<BlockPos>> approachTarget,
            double speed
    ) {
        return this.mineOne(level, target, approachTarget, speed, false);
    }

    public MineResult mineOne(
            ServerLevel level,
            BlockPos target,
            Supplier<Optional<BlockPos>> approachTarget,
            double speed,
            boolean useNearbyApproach
    ) {
        if (!this.interaction.canReachBlock(target)) {
            Optional<BlockPos> approach = approachTarget.get();
            if (approach.isEmpty()) {
                return MineResult.FAILED;
            }
            if (useNearbyApproach) {
                this.body.moveToNearby(approach.get(), speed);
            } else {
                this.body.moveTo(approach.get(), speed);
            }
            return MineResult.WORKING_FALLBACK;
        }

        this.body.stop();
        SurvivalWorldInteractor.BreakResult result = this.interaction.tickBreakBlock(level, target);
        return switch (result) {
            case BROKEN -> MineResult.BROKEN;
            case FAILED -> MineResult.FAILED;
            case NOT_IN_REACH, WORKING -> MineResult.WORKING_FALLBACK;
        };
    }

    public void reset() {
        this.interaction.cancelBreakBlock();
    }

    public String status() {
        return "mine=survival_targeted";
    }

    public enum MineResult {
        WORKING_FALLBACK,
        BROKEN,
        FAILED
    }
}
