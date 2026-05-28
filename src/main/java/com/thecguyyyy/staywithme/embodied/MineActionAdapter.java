package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import com.thecguyyyy.staywithme.survival.SurvivalWorldInteractor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MineActionAdapter {
    private static final int PLAYERENGINE_WARMUP_TICKS = 20;
    private static final int PLAYERENGINE_IDLE_FALLBACK_TICKS = 60;

    private final FriendEntity friend;
    private final EmbodiedController body;
    private final FriendInteractionProvider interaction;
    private boolean playerEngineMining;
    private int inventoryCountAtStart;
    private int playerEngineMineTicks;
    private int playerEngineIdleTicks;
    private BlockPos playerEngineAttemptTarget;
    private int playerEngineRetryCooldownTicks;

    public MineActionAdapter(FriendEntity friend, EmbodiedController body, FriendInteractionProvider interaction) {
        this.friend = friend;
        this.body = body;
        this.interaction = interaction;
    }

    public MineResult mineOne(
            ServerLevel level,
            BlockPos target,
            Predicate<ItemStack> harvestedMatcher,
            Supplier<Optional<BlockPos>> approachTarget,
            double speed,
            Block... preferredBlocks
    ) {
        if (this.playerEngineRetryCooldownTicks > 0) {
            this.playerEngineRetryCooldownTicks--;
        }

        int currentCount = this.friend.countInventoryItems(harvestedMatcher);
        if (this.playerEngineMining) {
            MineResult result = this.tickPlayerEngineMining(currentCount);
            if (result != MineResult.FALLBACK_READY) {
                return result;
            }
        }

        if (!this.interaction.canReachBlock(target)) {
            boolean canTryPlayerEngine = preferredBlocks.length > 0
                    && (this.playerEngineRetryCooldownTicks <= 0 || !target.equals(this.playerEngineAttemptTarget));
            if (canTryPlayerEngine && this.body.mineBlocks(1, preferredBlocks)) {
                this.playerEngineMining = true;
                this.inventoryCountAtStart = currentCount;
                this.playerEngineMineTicks = 0;
                this.playerEngineIdleTicks = 0;
                this.playerEngineAttemptTarget = target.immutable();
                return MineResult.WORKING_PLAYERENGINE;
            }

            BlockPos approach = approachTarget.get().orElse(target);
            this.body.moveTo(approach, speed);
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

    public MineResult mineAny(
            ServerLevel level,
            Predicate<ItemStack> harvestedMatcher,
            int count,
            Block... preferredBlocks
    ) {
        if (this.playerEngineRetryCooldownTicks > 0) {
            this.playerEngineRetryCooldownTicks--;
        }

        int currentCount = this.friend.countInventoryItems(harvestedMatcher);
        if (this.playerEngineMining) {
            MineResult result = this.tickPlayerEngineMining(currentCount);
            if (result != MineResult.FALLBACK_READY) {
                return result;
            }
        }

        if (preferredBlocks.length > 0
                && this.playerEngineRetryCooldownTicks <= 0
                && this.body.mineBlocks(Math.max(1, count), preferredBlocks)) {
            this.playerEngineMining = true;
            this.inventoryCountAtStart = currentCount;
            this.playerEngineMineTicks = 0;
            this.playerEngineIdleTicks = 0;
            this.playerEngineAttemptTarget = null;
            return MineResult.WORKING_PLAYERENGINE;
        }

        return MineResult.FAILED;
    }

    public void reset() {
        this.playerEngineMining = false;
        this.inventoryCountAtStart = 0;
        this.playerEngineMineTicks = 0;
        this.playerEngineIdleTicks = 0;
        this.playerEngineAttemptTarget = null;
        this.playerEngineRetryCooldownTicks = 0;
        this.interaction.cancelBreakBlock();
    }

    public String status() {
        if (this.playerEngineMining) {
            return "mine=playerengine,ticks=" + this.playerEngineMineTicks + ",idle=" + this.playerEngineIdleTicks;
        }
        return "mine=fallback_ready";
    }

    private MineResult tickPlayerEngineMining(int currentCount) {
        if (currentCount > this.inventoryCountAtStart) {
            this.reset();
            return MineResult.BROKEN;
        }

        this.playerEngineMineTicks++;
        if (this.body.isMining()) {
            this.playerEngineIdleTicks = 0;
            return MineResult.WORKING_PLAYERENGINE;
        }

        if (this.playerEngineMineTicks < PLAYERENGINE_WARMUP_TICKS) {
            return MineResult.WORKING_PLAYERENGINE;
        }

        this.playerEngineIdleTicks++;
        if (this.playerEngineIdleTicks <= PLAYERENGINE_IDLE_FALLBACK_TICKS) {
            return MineResult.WORKING_PLAYERENGINE;
        }

        this.playerEngineMining = false;
        this.playerEngineMineTicks = 0;
        this.playerEngineIdleTicks = 0;
        this.playerEngineRetryCooldownTicks = 100;
        return MineResult.FALLBACK_READY;
    }

    public enum MineResult {
        WORKING_PLAYERENGINE,
        WORKING_FALLBACK,
        FALLBACK_READY,
        BROKEN,
        FAILED
    }
}
