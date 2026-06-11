package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.perception.FriendPerception;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.function.Predicate;

final class LocalHazardEscapeFallback {
    private final FriendEntity friend;
    private final StandNavigation navigation;

    LocalHazardEscapeFallback(FriendEntity friend, StandNavigation navigation) {
        this.friend = friend;
        this.navigation = navigation;
    }

    Optional<BlockPos> findDryStandPosition(ServerLevel level, int radius) {
        return this.findNearestReachableStandPosition(level, radius, -4, 6, pos -> true);
    }

    Optional<BlockPos> findLavaSafeStandPosition(ServerLevel level, int radius) {
        return this.findNearestReachableStandPosition(level, radius, -3, 6,
                pos -> this.isLavaSafeStandPosition(level, pos));
    }

    private Optional<BlockPos> findNearestReachableStandPosition(
            ServerLevel level,
            int radius,
            int minYOffset,
            int maxYOffset,
            Predicate<BlockPos> extraCheck
    ) {
        BlockPos origin = this.friend.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int searchRadius = Math.max(1, radius);
        for (int y = minYOffset; y <= maxYOffset; y++) {
            for (int x = -searchRadius; x <= searchRadius; x++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!FriendPerception.canStandAt(level, candidate)
                            || !this.navigation.canNavigateTo(candidate)
                            || (extraCheck != null && !extraCheck.test(candidate))) {
                        continue;
                    }
                    double distance = origin.distSqr(candidate);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isLavaSafeStandPosition(ServerLevel level, BlockPos pos) {
        if (pos == null
                || !level.hasChunkAt(pos)
                || !level.hasChunkAt(pos.above())
                || !level.hasChunkAt(pos.below())
                || !FriendPerception.canStandAt(level, pos)
                || this.hasImmediateLavaHazard(level, pos)
                || this.hasNearbyBurningHazard(level, pos)
                || this.hasNearbyBurningHazard(level, pos.above())) {
            return false;
        }
        return !this.isBurningHazardBlock(level.getBlockState(pos.below()))
                && !this.isBurningHazardBlock(level.getBlockState(pos))
                && !this.isBurningHazardBlock(level.getBlockState(pos.above()));
    }

    private boolean hasImmediateLavaHazard(ServerLevel level, BlockPos feetPos) {
        return this.hasNearbyLavaHazard(level, feetPos)
                || this.hasNearbyLavaHazard(level, feetPos.above());
    }

    private boolean hasNearbyLavaHazard(ServerLevel level, BlockPos center) {
        if (center == null || !level.hasChunkAt(center)) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            cursor.setWithOffset(center, direction);
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            if (level.getFluidState(cursor).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return level.getFluidState(center).is(FluidTags.LAVA);
    }

    private boolean hasNearbyBurningHazard(ServerLevel level, BlockPos center) {
        if (center == null || !level.hasChunkAt(center)) {
            return false;
        }
        if (this.isBurningHazardBlock(level.getBlockState(center))) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = center.relative(direction);
            if (level.hasChunkAt(adjacent) && this.isBurningHazardBlock(level.getBlockState(adjacent))) {
                return true;
            }
        }
        return false;
    }

    private boolean isBurningHazardBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.LAVA
                || block == Blocks.LAVA_CAULDRON
                || state.getFluidState().is(FluidTags.LAVA);
    }

    @FunctionalInterface
    interface StandNavigation {
        boolean canNavigateTo(BlockPos candidate);
    }
}
