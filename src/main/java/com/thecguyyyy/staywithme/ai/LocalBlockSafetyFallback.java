package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.perception.FriendPerception;
import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

final class LocalBlockSafetyFallback {
    private final FriendEntity friend;
    private final EmbodiedController body;
    private final FriendInteractionProvider interaction;
    private final BlockPlacementSupport blockPlacementSupport;
    private BlockPos fireExtinguishTarget;

    LocalBlockSafetyFallback(
            FriendEntity friend,
            EmbodiedController body,
            FriendInteractionProvider interaction,
            BlockPlacementSupport blockPlacementSupport
    ) {
        this.friend = friend;
        this.body = body;
        this.interaction = interaction;
        this.blockPlacementSupport = blockPlacementSupport;
    }

    ActionResult tickClearLiquid(ServerLevel level, BlockPos liquidPos, FriendTask task, double speed) {
        Block block = this.blockPlacementSupport.blockToPlace(task);
        if (block == null) {
            return ActionResult.failed("Clearing liquid needs PlayerEngine, a bucket, or a carried throwaway block.");
        }
        if (!this.interaction.canReachBlock(liquidPos)) {
            Optional<BlockPos> standPos = this.blockPlacementSupport.findStandPositionNearBlock(level, liquidPos);
            if (standPos.isEmpty()) {
                return ActionResult.failed("I cannot find a reachable place to stand before clearing that liquid.");
            }
            this.body.moveToNearby(standPos.get(), speed);
            return ActionResult.working("Moving close enough to clear liquid at " + formatPos(liquidPos) + ".");
        }

        boolean placed = this.interaction.placeBlockReplacingLiquid(
                level,
                liquidPos,
                block,
                stack -> this.blockPlacementSupport.isMatchingBlock(stack, block)
        );
        if (placed || isLiquidCleared(level, liquidPos)) {
            this.body.stop();
            return ActionResult.done();
        }

        return ActionResult.failed("I reached the liquid, but Forge fallback could not place a block into it.");
    }

    FireTargetSelection selectFireTarget(ServerLevel level, int range) {
        boolean clearedPrevious = false;
        if (this.fireExtinguishTarget != null
                && (!isFireBlock(level.getBlockState(this.fireExtinguishTarget))
                || this.friend.blockPosition().distSqr(this.fireExtinguishTarget) > (range + 2) * (range + 2))) {
            this.fireExtinguishTarget = null;
            clearedPrevious = true;
        }
        if (this.fireExtinguishTarget == null) {
            this.fireExtinguishTarget = this.findNearestFire(level, range).orElse(null);
        }
        return new FireTargetSelection(Optional.ofNullable(this.fireExtinguishTarget), clearedPrevious);
    }

    void resetFireTarget() {
        this.fireExtinguishTarget = null;
    }

    ActionResult tickPutOutFire(ServerLevel level, int range, double speed) {
        if (this.fireExtinguishTarget == null || !isFireBlock(level.getBlockState(this.fireExtinguishTarget))) {
            this.fireExtinguishTarget = this.findNearestFire(level, range).orElse(null);
        }
        if (this.fireExtinguishTarget == null) {
            this.body.stop();
            return ActionResult.done();
        }

        double reachDistanceSqr = 4.5D * 4.5D;
        if (this.friend.distanceToSqr(
                this.fireExtinguishTarget.getX() + 0.5D,
                this.fireExtinguishTarget.getY() + 0.5D,
                this.fireExtinguishTarget.getZ() + 0.5D
        ) > reachDistanceSqr) {
            Optional<BlockPos> standPos = this.findFireExtinguishStandPos(level, this.fireExtinguishTarget);
            if (standPos.isEmpty()) {
                return ActionResult.failed("I cannot find a safe place to stand near that fire.");
            }
            this.body.moveToNearby(standPos.get(), speed);
            return ActionResult.working("Moving close enough to put out nearby fire.");
        }

        if (this.body.breakBlock(this.fireExtinguishTarget)) {
            this.fireExtinguishTarget = null;
            if (this.findNearestFire(level, range).isEmpty()) {
                this.body.stop();
                return ActionResult.done();
            }
            return ActionResult.working(null);
        }

        return ActionResult.failed("I reached the fire, but I could not put it out.");
    }

    private Optional<BlockPos> findNearestFire(ServerLevel level, int range) {
        BlockPos origin = this.friend.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    double distance = origin.distSqr(candidate);
                    if (distance >= bestDistance) {
                        continue;
                    }
                    if (isFireBlock(level.getBlockState(candidate))) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> findFireExtinguishStandPos(ServerLevel level, BlockPos firePosition) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = firePosition.relative(direction);
            if (!FriendPerception.canStandAt(level, candidate)) {
                continue;
            }
            double distance = this.friend.blockPosition().distSqr(candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    static boolean isFireBlock(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }

    static boolean isLiquidCleared(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos) && level.getFluidState(pos).isEmpty();
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "unknown";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    record ActionResult(Status status, String message) {
        static ActionResult working(String message) {
            return new ActionResult(Status.WORKING, message);
        }

        static ActionResult done() {
            return new ActionResult(Status.DONE, null);
        }

        static ActionResult failed(String message) {
            return new ActionResult(Status.FAILED, message);
        }
    }

    enum Status {
        WORKING,
        DONE,
        FAILED
    }

    record FireTargetSelection(Optional<BlockPos> target, boolean previousTargetCleared) {
    }

    interface BlockPlacementSupport {
        Block blockToPlace(FriendTask task);

        boolean isMatchingBlock(ItemStack stack, Block block);

        Optional<BlockPos> findStandPositionNearBlock(ServerLevel level, BlockPos target);
    }
}
