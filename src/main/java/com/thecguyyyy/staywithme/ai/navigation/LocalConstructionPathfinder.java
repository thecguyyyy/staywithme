package com.thecguyyyy.staywithme.ai.navigation;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class LocalConstructionPathfinder {
    private static final int HORIZONTAL_RADIUS = 10;
    private static final int VERTICAL_RADIUS = 6;
    private static final int MAX_EXPANSIONS = 3000;
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[]{
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };
    private static final Set<Block> RISKY_BLOCKS = Set.of(
            Blocks.POWDER_SNOW,
            Blocks.POINTED_DRIPSTONE,
            Blocks.MAGMA_BLOCK,
            Blocks.CACTUS,
            Blocks.SWEET_BERRY_BUSH,
            Blocks.WITHER_ROSE,
            Blocks.FIRE,
            Blocks.SOUL_FIRE,
            Blocks.CAMPFIRE,
            Blocks.SOUL_CAMPFIRE,
            Blocks.LAVA,
            Blocks.LAVA_CAULDRON,
            Blocks.COBWEB,
            Blocks.TNT,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK
    );

    private LocalConstructionPathfinder() {
    }

    public static Optional<ConstructionRoutePlan> plan(
            ServerLevel level,
            FriendEntity friend,
            BlockPos destination,
            int repairBlocks
    ) {
        BlockPos origin = friend.blockPosition().immutable();
        if (destination == null || !insideBounds(origin, destination)) {
            return Optional.empty();
        }
        if (origin.equals(destination)) {
            ConstructionRoutePlan empty = new ConstructionRoutePlan();
            empty.source = "local_voxel_astar";
            empty.reason = "Already at construction destination.";
            return Optional.of(empty);
        }

        State start = new State(origin, 0);
        PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::estimatedCost));
        Map<State, Double> costs = new HashMap<>();
        Map<State, State> previous = new HashMap<>();
        open.add(new SearchNode(start, heuristic(origin, destination)));
        costs.put(start, 0.0D);
        State reached = null;
        int expansions = 0;

        while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
            SearchNode currentNode = open.poll();
            State current = currentNode.state();
            double currentCost = costs.getOrDefault(current, Double.MAX_VALUE);
            if (current.pos().equals(destination)) {
                reached = current;
                break;
            }

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = current.pos().relative(direction).offset(0, dy, 0);
                    if (!insideBounds(origin, next)) {
                        continue;
                    }
                    Optional<Transition> transition = inspectTransition(
                            level,
                            current.pos(),
                            next,
                            repairBlocks - current.usedRepairBlocks()
                    );
                    if (transition.isEmpty()) {
                        continue;
                    }
                    int nextRepairBlocks = current.usedRepairBlocks() + (transition.get().floorToPlace() == null ? 0 : 1);
                    State nextState = new State(next.immutable(), nextRepairBlocks);
                    double nextCost = currentCost
                            + 1.0D
                            + transition.get().blockers().size() * 5.0D
                            + (transition.get().floorToPlace() == null ? 0.0D : 7.0D)
                            + Math.abs(dy) * 2.0D;
                    if (nextCost >= costs.getOrDefault(nextState, Double.MAX_VALUE)) {
                        continue;
                    }
                    costs.put(nextState, nextCost);
                    previous.put(nextState, current);
                    open.add(new SearchNode(nextState, nextCost + heuristic(next, destination)));
                }
            }
        }

        if (reached == null) {
            return Optional.empty();
        }
        List<BlockPos> reversed = new ArrayList<>();
        State cursor = reached;
        while (!cursor.equals(start)) {
            reversed.add(cursor.pos());
            cursor = previous.get(cursor);
            if (cursor == null) {
                return Optional.empty();
            }
        }

        ConstructionRoutePlan result = new ConstructionRoutePlan();
        result.source = "local_voxel_astar";
        result.reason = "Local voxel A* construction fallback.";
        for (int index = reversed.size() - 1; index >= 0; index--) {
            result.steps.add(ConstructionRoutePlan.relativeTo(origin, reversed.get(index)));
        }
        return Optional.of(result);
    }

    public static Optional<Transition> inspectTransition(
            ServerLevel level,
            BlockPos from,
            BlockPos nextFeet,
            int availableRepairBlocks
    ) {
        if (from == null || nextFeet == null
                || Math.abs(nextFeet.getX() - from.getX()) + Math.abs(nextFeet.getZ() - from.getZ()) != 1
                || Math.abs(nextFeet.getY() - from.getY()) > 1
                || !loaded(level, nextFeet)
                || !loaded(level, nextFeet.above())
                || !loaded(level, nextFeet.below())) {
            return Optional.empty();
        }

        BlockPos floorPos = nextFeet.below();
        BlockPos floorToPlace = null;
        if (!isSafeFloor(level, floorPos)) {
            if (availableRepairBlocks <= 0 || !canPlaceFloor(level, floorPos)) {
                return Optional.empty();
            }
            floorToPlace = floorPos.immutable();
        }

        List<BlockPos> blockers = new ArrayList<>();
        if (!collectBlocker(level, nextFeet, blockers)
                || !collectBlocker(level, nextFeet.above(), blockers)) {
            return Optional.empty();
        }
        if (nextFeet.getY() < from.getY()
                && !collectBlocker(level, nextFeet.above(2), blockers)) {
            return Optional.empty();
        }
        return Optional.of(new Transition(nextFeet.immutable(), floorToPlace, blockers));
    }

    private static boolean collectBlocker(ServerLevel level, BlockPos pos, List<BlockPos> blockers) {
        if (!loaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        if (state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0F
                || state.getBlock() instanceof FallingBlock
                || RISKY_BLOCKS.contains(state.getBlock())
                || hasNearbyFluid(level, pos)) {
            return false;
        }
        blockers.add(pos.immutable());
        return true;
    }

    private static boolean canPlaceFloor(ServerLevel level, BlockPos floorPos) {
        if (!loaded(level, floorPos) || !loaded(level, floorPos.below())) {
            return false;
        }
        BlockState floor = level.getBlockState(floorPos);
        return floor.canBeReplaced()
                && floor.getFluidState().isEmpty()
                && isSafeFloor(level, floorPos.below());
    }

    private static boolean isSafeFloor(ServerLevel level, BlockPos floorPos) {
        if (!loaded(level, floorPos)) {
            return false;
        }
        BlockState floor = level.getBlockState(floorPos);
        return !floor.isAir()
                && floor.getFluidState().isEmpty()
                && !RISKY_BLOCKS.contains(floor.getBlock())
                && floor.isFaceSturdy(level, floorPos, Direction.UP);
    }

    private static boolean hasNearbyFluid(ServerLevel level, BlockPos center) {
        for (Direction direction : Direction.values()) {
            BlockPos pos = center.relative(direction);
            if (!loaded(level, pos)) {
                return true;
            }
            if (!level.getFluidState(pos).isEmpty()
                    || level.getFluidState(pos).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos);
    }

    private static boolean insideBounds(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= HORIZONTAL_RADIUS
                && Math.abs(pos.getZ() - origin.getZ()) <= HORIZONTAL_RADIUS
                && Math.abs(pos.getY() - origin.getY()) <= VERTICAL_RADIUS;
    }

    private static double heuristic(BlockPos pos, BlockPos destination) {
        return Math.abs(pos.getX() - destination.getX())
                + Math.abs(pos.getZ() - destination.getZ())
                + Math.abs(pos.getY() - destination.getY()) * 2.0D;
    }

    private record State(BlockPos pos, int usedRepairBlocks) {
    }

    private record SearchNode(State state, double estimatedCost) {
    }

    public record Transition(BlockPos nextFeet, BlockPos floorToPlace, List<BlockPos> blockers) {
        public Transition {
            blockers = List.copyOf(blockers);
        }
    }
}
