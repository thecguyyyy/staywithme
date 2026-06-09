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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class LocalConstructionPathfinder {
    private static final int HORIZONTAL_RADIUS = 100;
    private static final int VERTICAL_RADIUS = 30;
    private static final int MAX_SAFE_FALL_HEIGHT = FriendEntity.MAX_SAFE_FALL_DISTANCE;
    private static final int MAX_EXPANSIONS = 120_000;
    private static final long MAX_ADVANCE_NANOS = 10_000_000L;
    private static final double HEURISTIC_WEIGHT = 3.0D;
    private static final int MAX_DIRECT_SEGMENT_STEPS = 16;
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

    public static Optional<SearchSession> begin(
            FriendEntity friend,
            BlockPos destination,
            int repairBlocks
    ) {
        BlockPos origin = friend.blockPosition().immutable();
        if (destination == null || !insideBounds(origin, destination)) {
            return Optional.empty();
        }
        return Optional.of(new SearchSession(origin, destination.immutable(), repairBlocks));
    }

    public static int horizontalRadius() {
        return HORIZONTAL_RADIUS;
    }

    public static int verticalRadius() {
        return VERTICAL_RADIUS;
    }

    public static int maxSafeFallHeight() {
        return MAX_SAFE_FALL_HEIGHT;
    }

    public static Optional<ConstructionRoutePlan> planDirectSegment(
            ServerLevel level,
            FriendEntity friend,
            BlockPos destination,
            int repairBlocks
    ) {
        if (level == null || friend == null || destination == null) {
            return Optional.empty();
        }

        BlockPos origin = friend.blockPosition().immutable();
        BlockPos current = origin;
        int remainingRepairBlocks = Math.max(0, repairBlocks);
        Set<BlockPos> visited = new HashSet<>();
        visited.add(origin);

        ConstructionRoutePlan plan = new ConstructionRoutePlan();
        plan.source = "direct_remote_construction";
        plan.reason = "Deterministic short direct segment using horizontal tunneling, stairs, shafts, pillars, and bridges.";

        for (int stepIndex = 0;
             stepIndex < MAX_DIRECT_SEGMENT_STEPS && !current.equals(destination);
             stepIndex++) {
            DirectChoice choice = chooseDirectTransition(
                    level,
                    current,
                    destination,
                    remainingRepairBlocks,
                    visited
            ).orElse(null);
            if (choice == null) {
                break;
            }
            current = choice.nextFeet();
            visited.add(current);
            if (choice.transition().floorToPlace() != null) {
                remainingRepairBlocks--;
            }
            plan.steps.add(ConstructionRoutePlan.relativeTo(origin, current));
        }

        if (plan.steps.isEmpty()) {
            return Optional.empty();
        }
        plan.reason += " Segment endpoint=" + current.toShortString() + ".";
        return Optional.of(plan);
    }

    public static Optional<Transition> inspectTransition(
            ServerLevel level,
            BlockPos from,
            BlockPos nextFeet,
            int availableRepairBlocks
    ) {
        if (from == null || nextFeet == null
                || !loaded(level, nextFeet)
                || !loaded(level, nextFeet.above())
                || !loaded(level, nextFeet.below())) {
            return Optional.empty();
        }

        int horizontalDelta = Math.abs(nextFeet.getX() - from.getX()) + Math.abs(nextFeet.getZ() - from.getZ());
        int verticalDelta = nextFeet.getY() - from.getY();
        if (horizontalDelta == 0 && verticalDelta == 1) {
            return inspectPillarUpTransition(level, from, nextFeet, availableRepairBlocks);
        }
        if (horizontalDelta == 0 && verticalDelta == -1) {
            return inspectShaftDownTransition(level, from, nextFeet);
        }
        if (horizontalDelta == 1 && verticalDelta < -1) {
            return inspectSafeFallTransition(level, from, nextFeet);
        }
        if (horizontalDelta != 1 || verticalDelta < -1 || verticalDelta > 1) {
            return Optional.empty();
        }

        BlockPos floorPos = nextFeet.below();
        BlockPos floorToPlace = null;
        boolean bridgeFloor = false;
        if (!isSafeFloor(level, floorPos)) {
            if (availableRepairBlocks <= 0) {
                return Optional.empty();
            }
            if (canPlaceFloor(level, floorPos)) {
                floorToPlace = floorPos.immutable();
            } else if (verticalDelta == 0 && canPlanHorizontalBridge(level, from, nextFeet, floorPos)) {
                floorToPlace = floorPos.immutable();
                bridgeFloor = true;
            } else {
                return Optional.empty();
            }
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
        return Optional.of(new Transition(nextFeet.immutable(), floorToPlace, bridgeFloor, blockers));
    }

    private static Optional<Transition> inspectPillarUpTransition(
            ServerLevel level,
            BlockPos from,
            BlockPos nextFeet,
            int availableRepairBlocks
    ) {
        if (availableRepairBlocks <= 0
                || !isReplaceableDrySpace(level, from)
                || !loaded(level, nextFeet.above())) {
            return Optional.empty();
        }
        List<BlockPos> blockers = new ArrayList<>();
        if (!collectBlocker(level, nextFeet, blockers)
                || !collectBlocker(level, nextFeet.above(), blockers)) {
            return Optional.empty();
        }
        return Optional.of(new Transition(nextFeet.immutable(), from.immutable(), false, blockers));
    }

    private static Optional<Transition> inspectShaftDownTransition(
            ServerLevel level,
            BlockPos from,
            BlockPos nextFeet
    ) {
        if (!isSafeFloor(level, nextFeet.below())
                || hasNearbyFluid(level, nextFeet)
                || hasNearbyFluid(level, nextFeet.below())) {
            return Optional.empty();
        }
        BlockState shaftBlock = level.getBlockState(nextFeet);
        if (shaftBlock.getCollisionShape(level, nextFeet).isEmpty()
                || shaftBlock.hasBlockEntity()
                || shaftBlock.getDestroySpeed(level, nextFeet) < 0.0F
                || shaftBlock.getBlock() instanceof FallingBlock
                || RISKY_BLOCKS.contains(shaftBlock.getBlock())
                || !shaftBlock.getFluidState().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Transition(nextFeet.immutable(), null, false, List.of(nextFeet.immutable())));
    }

    private static Optional<Transition> inspectSafeFallTransition(
            ServerLevel level,
            BlockPos from,
            BlockPos nextFeet
    ) {
        int fallHeight = from.getY() - nextFeet.getY();
        if (fallHeight < 2
                || fallHeight > MAX_SAFE_FALL_HEIGHT
                || !isSafeFloor(level, nextFeet.below())
                || hasNearbyFluid(level, nextFeet)
                || hasNearbyFluid(level, nextFeet.below())) {
            return Optional.empty();
        }

        for (int y = nextFeet.getY(); y <= from.getY() + 1; y++) {
            BlockPos fallSpace = new BlockPos(nextFeet.getX(), y, nextFeet.getZ());
            if (!isSafeOpenSpace(level, fallSpace) || hasNearbyFluid(level, fallSpace)) {
                return Optional.empty();
            }
        }
        return Optional.of(new Transition(nextFeet.immutable(), null, false, List.of()));
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

    private static boolean canPlanHorizontalBridge(
            ServerLevel level,
            BlockPos from,
            BlockPos nextFeet,
            BlockPos floorPos
    ) {
        if (!loaded(level, floorPos)
                || from == null
                || nextFeet == null
                || nextFeet.getY() != from.getY()
                || !floorPos.equals(nextFeet.below())) {
            return false;
        }
        int horizontalDelta = Math.abs(nextFeet.getX() - from.getX())
                + Math.abs(nextFeet.getZ() - from.getZ());
        BlockState floor = level.getBlockState(floorPos);
        return horizontalDelta == 1
                && floor.canBeReplaced()
                && !floor.hasBlockEntity()
                && !floor.getFluidState().is(FluidTags.LAVA)
                && !RISKY_BLOCKS.contains(floor.getBlock());
    }

    private static boolean isReplaceableDrySpace(ServerLevel level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static boolean isSafeOpenSpace(ServerLevel level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty()
                && !RISKY_BLOCKS.contains(state.getBlock());
    }

    private static boolean isSafeFloor(ServerLevel level, BlockPos floorPos) {
        if (!loaded(level, floorPos)) {
            return false;
        }
        BlockState floor = level.getBlockState(floorPos);
        return !floor.isAir()
                && floor.getFluidState().isEmpty()
                && !(floor.getBlock() instanceof FallingBlock)
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

    private record SearchNode(State state, double pathCost, double estimatedCost) {
    }

    private static Optional<DirectChoice> chooseDirectTransition(
            ServerLevel level,
            BlockPos current,
            BlockPos destination,
            int repairBlocks,
            Set<BlockPos> visited
    ) {
        List<BlockPos> candidates = directCandidates(current, destination);
        DirectChoice best = null;
        double currentHeuristic = heuristic(current, destination);
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (visited.contains(candidate)) {
                continue;
            }
            loadDirectNeighborhood(level, candidate);
            Optional<Transition> inspected = inspectTransition(level, current, candidate, repairBlocks);
            if (inspected.isEmpty()) {
                continue;
            }
            Transition transition = inspected.get();
            boolean pureVertical = candidate.getX() == current.getX() && candidate.getZ() == current.getZ();
            double nextHeuristic = heuristic(candidate, destination);
            double score = nextHeuristic
                    + transition.blockers().size() * 4.0D
                    + (transition.floorToPlace() == null ? 0.0D : transition.bridgeFloor() ? 12.0D : 7.0D)
                    + (pureVertical ? 8.0D : 0.0D)
                    + (nextHeuristic >= currentHeuristic ? 20.0D : 0.0D);
            if (score < bestScore) {
                bestScore = score;
                best = new DirectChoice(candidate.immutable(), transition);
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<BlockPos> directCandidates(BlockPos current, BlockPos destination) {
        List<Direction> directions = new ArrayList<>(List.of(HORIZONTAL_DIRECTIONS));
        directions.sort(Comparator.comparingInt(direction ->
                Math.abs(destination.getX() - current.relative(direction).getX())
                        + Math.abs(destination.getZ() - current.relative(direction).getZ())));

        List<BlockPos> candidates = new ArrayList<>();
        int verticalStep = Integer.compare(destination.getY(), current.getY());
        for (Direction direction : directions) {
            if (verticalStep != 0) {
                candidates.add(current.relative(direction).offset(0, verticalStep, 0).immutable());
            }
            candidates.add(current.relative(direction).immutable());
        }
        if (verticalStep > 0) {
            candidates.add(current.above().immutable());
        } else if (verticalStep < 0) {
            candidates.add(current.below().immutable());
        }
        return candidates;
    }

    private static void loadDirectNeighborhood(ServerLevel level, BlockPos center) {
        level.getChunkAt(center);
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            level.getChunkAt(center.relative(direction));
        }
    }

    private record DirectChoice(BlockPos nextFeet, Transition transition) {
    }

    public record Transition(BlockPos nextFeet, BlockPos floorToPlace, boolean bridgeFloor, List<BlockPos> blockers) {
        public Transition {
            blockers = List.copyOf(blockers);
        }
    }

    public record SearchProgress(
            Optional<ConstructionRoutePlan> plan,
            boolean exhausted,
            int expansions,
            int openNodes
    ) {
    }

    public static final class SearchSession {
        private final BlockPos origin;
        private final BlockPos destination;
        private final int repairBlocks;
        private final State start;
        private final PriorityQueue<SearchNode> open =
                new PriorityQueue<>(Comparator.comparingDouble(SearchNode::estimatedCost));
        private final Map<State, Double> costs = new HashMap<>();
        private final Map<State, State> previous = new HashMap<>();
        private ConstructionRoutePlan plan;
        private boolean exhausted;
        private int expansions;

        private SearchSession(BlockPos origin, BlockPos destination, int repairBlocks) {
            this.origin = origin.immutable();
            this.destination = destination.immutable();
            this.repairBlocks = Math.max(0, repairBlocks);
            this.start = new State(this.origin, 0);
            this.open.add(new SearchNode(
                    this.start,
                    0.0D,
                    heuristic(this.origin, this.destination) * HEURISTIC_WEIGHT
            ));
            this.costs.put(this.start, 0.0D);
        }

        public SearchProgress advance(ServerLevel level, int expansionBudget) {
            if (this.plan != null || this.exhausted) {
                return this.progress();
            }

            int remainingBudget = Math.max(1, expansionBudget);
            long advanceStarted = System.nanoTime();
            while (!this.open.isEmpty()
                    && this.expansions < MAX_EXPANSIONS
                    && remainingBudget > 0
                    && System.nanoTime() - advanceStarted < MAX_ADVANCE_NANOS) {
                SearchNode currentNode = this.open.poll();
                State current = currentNode.state();
                double currentCost = this.costs.getOrDefault(current, Double.MAX_VALUE);
                if (currentNode.pathCost() > currentCost) {
                    continue;
                }

                this.expansions++;
                remainingBudget--;
                if (current.pos().equals(this.destination)) {
                    this.plan = this.buildPlan(current);
                    this.exhausted = this.plan == null;
                    break;
                }

                for (Direction direction : HORIZONTAL_DIRECTIONS) {
                    for (int dy = -MAX_SAFE_FALL_HEIGHT; dy <= 1; dy++) {
                        BlockPos next = current.pos().relative(direction).offset(0, dy, 0);
                        this.considerTransition(level, current, next, currentCost);
                    }
                }
                this.considerTransition(level, current, current.pos().above(), currentCost);
                this.considerTransition(level, current, current.pos().below(), currentCost);
            }

            if (this.plan == null && (this.open.isEmpty() || this.expansions >= MAX_EXPANSIONS)) {
                this.exhausted = true;
            }
            return this.progress();
        }

        public String status() {
            return "local_voxel_astar_incremental:expansions="
                    + this.expansions
                    + ",open="
                    + this.open.size()
                    + ",range="
                    + HORIZONTAL_RADIUS
                    + "x"
                    + VERTICAL_RADIUS
                    + ",safeFall="
                    + MAX_SAFE_FALL_HEIGHT;
        }

        private SearchProgress progress() {
            return new SearchProgress(
                    Optional.ofNullable(this.plan),
                    this.exhausted,
                    this.expansions,
                    this.open.size()
            );
        }

        private void considerTransition(ServerLevel level, State current, BlockPos next, double currentCost) {
            if (!insideBounds(this.origin, next)) {
                return;
            }
            Optional<Transition> transition = inspectTransition(
                    level,
                    current.pos(),
                    next,
                    this.repairBlocks - current.usedRepairBlocks()
            );
            if (transition.isEmpty()) {
                return;
            }
            int nextRepairBlocks = current.usedRepairBlocks()
                    + (transition.get().floorToPlace() == null ? 0 : 1);
            State nextState = new State(next.immutable(), nextRepairBlocks);
            int verticalDelta = Math.abs(next.getY() - current.pos().getY());
            boolean pureVertical = next.getX() == current.pos().getX() && next.getZ() == current.pos().getZ();
            double nextCost = currentCost
                    + 1.0D
                    + transition.get().blockers().size() * 5.0D
                    + (transition.get().floorToPlace() == null
                    ? 0.0D
                    : transition.get().bridgeFloor() ? 16.0D : 7.0D)
                    + verticalDelta * 2.0D
                    + (pureVertical ? 8.0D : 0.0D);
            if (nextCost >= this.costs.getOrDefault(nextState, Double.MAX_VALUE)) {
                return;
            }
            this.costs.put(nextState, nextCost);
            this.previous.put(nextState, current);
            this.open.add(new SearchNode(
                    nextState,
                    nextCost,
                    nextCost + heuristic(next, this.destination) * HEURISTIC_WEIGHT
            ));
        }

        private ConstructionRoutePlan buildPlan(State reached) {
            List<BlockPos> reversed = new ArrayList<>();
            State cursor = reached;
            while (!cursor.equals(this.start)) {
                reversed.add(cursor.pos());
                if (reversed.size() > ConstructionRoutePlan.MAX_STEPS) {
                    return null;
                }
                cursor = this.previous.get(cursor);
                if (cursor == null) {
                    return null;
                }
            }

            ConstructionRoutePlan result = new ConstructionRoutePlan();
            result.source = "local_voxel_astar_incremental";
            result.reason = "Incremental local voxel A* construction route within horizontal "
                    + HORIZONTAL_RADIUS
                    + " and vertical "
                    + VERTICAL_RADIUS
                    + " blocks.";
            for (int index = reversed.size() - 1; index >= 0; index--) {
                result.steps.add(ConstructionRoutePlan.relativeTo(this.origin, reversed.get(index)));
            }
            return result;
        }
    }
}
