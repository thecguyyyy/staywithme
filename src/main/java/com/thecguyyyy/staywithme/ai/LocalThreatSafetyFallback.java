package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.embodied.PlaceActionAdapter;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.perception.FriendPerception;
import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

final class LocalThreatSafetyFallback {
    private final FriendEntity friend;
    private final EmbodiedController body;
    private final FriendInteractionProvider interaction;
    private final PlaceActionAdapter placeAdapter;
    private final BlockPlacementSupport blockPlacementSupport;
    private BlockPos projectileWallPlaceTarget;

    LocalThreatSafetyFallback(
            FriendEntity friend,
            EmbodiedController body,
            FriendInteractionProvider interaction,
            PlaceActionAdapter placeAdapter,
            BlockPlacementSupport blockPlacementSupport
    ) {
        this.friend = friend;
        this.body = body;
        this.interaction = interaction;
        this.placeAdapter = placeAdapter;
        this.blockPlacementSupport = blockPlacementSupport;
    }

    boolean tryRetreatFromThreat(LivingEntity threat, int distance, double speed) {
        if (!(this.friend.level() instanceof ServerLevel level) || threat == null || !threat.isAlive()) {
            return false;
        }
        Optional<BlockPos> retreatTarget = this.findThreatRetreatStandPos(level, threat, distance);
        if (retreatTarget.isEmpty()) {
            return false;
        }
        this.body.moveToNearby(retreatTarget.get(), speed);
        return true;
    }

    Optional<BlockPos> findThreatRetreatStandPos(ServerLevel level, LivingEntity threat, int distance) {
        Vec3 away = this.friend.position().subtract(threat.position());
        if (away.horizontalDistanceSqr() < 0.0001D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            away = new Vec3(away.x, 0.0D, away.z).normalize();
        }

        BlockPos origin = this.friend.blockPosition();
        int farthest = Math.max(6, distance);
        double requiredDistanceSqr = distance * distance;
        for (int step = farthest; step >= 4; step--) {
            int x = origin.getX() + (int) Math.round(away.x * step);
            int z = origin.getZ() + (int) Math.round(away.z * step);
            for (int dy = -2; dy <= 3; dy++) {
                BlockPos candidate = new BlockPos(x, origin.getY() + dy, z);
                if (!FriendPerception.canStandAt(level, candidate)
                        || threat.distanceToSqr(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D) < requiredDistanceSqr) {
                    continue;
                }
                Path path = this.friend.getNavigation().createPath(candidate, 0);
                if (path != null && path.canReach() && this.hasReversibleVerticalSteps(path)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    Optional<BlockPos> findProjectileDodgeTarget(int horizontalDistance, int verticalDistance) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }

        Optional<Projectile> projectile = this.nearestIncomingProjectileThreat(horizontalDistance, verticalDistance);
        if (projectile.isPresent()) {
            Optional<BlockPos> dodgeTarget = this.findProjectileDodgeStandPosition(level, projectile.get(), horizontalDistance, verticalDistance);
            if (dodgeTarget.isPresent()) {
                return dodgeTarget;
            }
        }

        int skeletonRange = Math.max(8, horizontalDistance * 4);
        Optional<Skeleton> skeleton = this.nearestSkeletonThreat(skeletonRange);
        return skeleton.flatMap(value -> this.findThreatRetreatStandPos(level, value, Math.max(6, horizontalDistance + 4)));
    }

    boolean hasProjectileDodgeThreat(int horizontalDistance, int verticalDistance) {
        return this.nearestIncomingProjectileThreat(horizontalDistance, verticalDistance).isPresent()
                || this.nearestSkeletonThreat(Math.max(8, horizontalDistance * 4)).isPresent();
    }

    Optional<Skeleton> nearestSkeletonThreat(int range) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        AABB searchBox = this.friend.getBoundingBox().inflate(Math.max(4, range));
        return level.getEntitiesOfClass(Skeleton.class, searchBox, skeleton -> skeleton.isAlive()
                        && skeleton.hasLineOfSight(this.friend)
                        && skeleton.distanceToSqr(this.friend) <= (double) range * (double) range)
                .stream()
                .min(Comparator.comparingDouble(skeleton -> skeleton.distanceToSqr(this.friend)));
    }

    Optional<BlockPos> findProjectileWallPlaceTarget(FriendTask task, ServerLevel level, Skeleton skeleton) {
        if (this.blockPlacementSupport.blockToPlace(task) == null) {
            return Optional.empty();
        }
        return this.findProjectileWallPlaceTarget(level, skeleton);
    }

    boolean tryProjectileWall(FriendTask task, Skeleton skeleton, double speed) {
        if (!(this.friend.level() instanceof ServerLevel level) || skeleton == null || !skeleton.isAlive()) {
            return false;
        }
        Block block = this.blockPlacementSupport.blockToPlace(task);
        if (block == null) {
            return false;
        }
        if (this.projectileWallPlaceTarget == null
                || !this.canUseProjectileWallPlaceTarget(level, this.projectileWallPlaceTarget, skeleton)) {
            this.projectileWallPlaceTarget = this.findProjectileWallPlaceTarget(level, skeleton).orElse(null);
        }
        if (this.projectileWallPlaceTarget == null) {
            return false;
        }

        BlockPos target = this.projectileWallPlaceTarget;
        PlaceActionAdapter.PlaceResult result = this.placeAdapter.placeBlock(
                level,
                target,
                block,
                stack -> this.blockPlacementSupport.isMatchingBlock(stack, block),
                () -> this.blockPlacementSupport.findStandPositionNearBlock(level, target),
                speed
        );
        if (result == PlaceActionAdapter.PlaceResult.WORKING) {
            return true;
        }
        if (result == PlaceActionAdapter.PlaceResult.PLACED) {
            this.projectileWallPlaceTarget = null;
            return true;
        }
        this.projectileWallPlaceTarget = null;
        return false;
    }

    void resetProjectileWallTarget() {
        this.projectileWallPlaceTarget = null;
    }

    private Optional<Projectile> nearestIncomingProjectileThreat(int horizontalDistance, int verticalDistance) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        int horizontalRange = Math.max(8, horizontalDistance * 4);
        int verticalRange = Math.max(4, verticalDistance + 2);
        AABB searchBox = this.friend.getBoundingBox().inflate(horizontalRange, verticalRange, horizontalRange);
        Vec3 friendCenter = this.friend.position().add(0.0D, this.friend.getBbHeight() * 0.5D, 0.0D);
        return level.getEntitiesOfClass(Projectile.class, searchBox, projectile ->
                        projectile.isAlive()
                                && projectile.getOwner() != this.friend
                                && this.isProjectileThreatAt(projectile, friendCenter, horizontalDistance, verticalDistance))
                .stream()
                .min(Comparator.comparingDouble(projectile -> projectile.distanceToSqr(this.friend)));
    }

    private Optional<BlockPos> findProjectileDodgeStandPosition(
            ServerLevel level,
            Projectile projectile,
            int horizontalDistance,
            int verticalDistance
    ) {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 lateral = new Vec3(-velocity.z, 0.0D, velocity.x);
        if (lateral.horizontalDistanceSqr() < 0.0001D) {
            lateral = this.friend.position().subtract(projectile.position());
            lateral = new Vec3(-lateral.z, 0.0D, lateral.x);
        }
        if (lateral.horizontalDistanceSqr() < 0.0001D) {
            lateral = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            lateral = lateral.normalize();
        }

        BlockPos origin = this.friend.blockPosition();
        int farthest = Math.max(2, horizontalDistance);
        for (int step = farthest; step >= 2; step--) {
            for (int sign : new int[]{1, -1}) {
                int x = origin.getX() + (int) Math.round(lateral.x * step * sign);
                int z = origin.getZ() + (int) Math.round(lateral.z * step * sign);
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = new BlockPos(x, origin.getY() + dy, z);
                    Vec3 candidateCenter = Vec3.atBottomCenterOf(candidate).add(0.0D, this.friend.getBbHeight() * 0.5D, 0.0D);
                    if (!FriendPerception.canStandAt(level, candidate)
                            || !this.canNavigateToStandPosition(candidate)
                            || this.isProjectileThreatAt(projectile, candidateCenter, Math.max(1, horizontalDistance - 1), verticalDistance)) {
                        continue;
                    }
                    return Optional.of(candidate.immutable());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isProjectileThreatAt(Projectile projectile, Vec3 targetCenter, int horizontalDistance, int verticalDistance) {
        Vec3 velocity = projectile.getDeltaMovement();
        double speedSqr = velocity.lengthSqr();
        if (speedSqr < 0.0001D) {
            return false;
        }
        Vec3 toTarget = targetCenter.subtract(projectile.position());
        double projection = toTarget.dot(velocity);
        if (projection <= 0.0D) {
            return false;
        }
        double ticksToClosestApproach = projection / speedSqr;
        if (ticksToClosestApproach > 20.0D) {
            return false;
        }
        Vec3 closest = projectile.position().add(velocity.scale(ticksToClosestApproach));
        double dx = closest.x - targetCenter.x;
        double dz = closest.z - targetCenter.z;
        double horizontalDistanceSqr = dx * dx + dz * dz;
        double verticalDelta = Math.abs(closest.y - targetCenter.y);
        return horizontalDistanceSqr <= (double) horizontalDistance * (double) horizontalDistance
                && verticalDelta <= Math.max(1, verticalDistance);
    }

    private Optional<BlockPos> findProjectileWallPlaceTarget(ServerLevel level, Skeleton skeleton) {
        Vec3 away = this.friend.position().subtract(skeleton.position());
        if (away.horizontalDistanceSqr() < 0.0001D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            away = new Vec3(away.x, 0.0D, away.z).normalize();
        }

        BlockPos origin = this.friend.blockPosition();
        int[] stepsTowardThreat = new int[]{2, 1, 3};
        for (int step : stepsTowardThreat) {
            int x = origin.getX() - (int) Math.round(away.x * step);
            int z = origin.getZ() - (int) Math.round(away.z * step);
            for (int yOffset = 0; yOffset <= 2; yOffset++) {
                BlockPos candidate = new BlockPos(x, origin.getY() + yOffset, z);
                if (this.canUseProjectileWallPlaceTarget(level, candidate, skeleton)) {
                    return Optional.of(candidate.immutable());
                }
            }
        }
        return Optional.empty();
    }

    private boolean canUseProjectileWallPlaceTarget(ServerLevel level, BlockPos target, Skeleton skeleton) {
        if (target == null
                || skeleton == null
                || !skeleton.isAlive()
                || !this.blockPlacementSupport.canPlaceBlockAt(level, target)) {
            return false;
        }
        AABB blockBox = new AABB(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getX() + 1.0D,
                target.getY() + 1.0D,
                target.getZ() + 1.0D
        );
        if (blockBox.intersects(this.friend.getBoundingBox()) || blockBox.intersects(skeleton.getBoundingBox())) {
            return false;
        }
        return this.interaction.canReachBlock(target)
                || this.blockPlacementSupport.findStandPositionNearBlock(level, target).isPresent();
    }

    private boolean canNavigateToStandPosition(BlockPos candidate) {
        if (candidate == null) {
            return false;
        }
        if (this.friend.blockPosition().distSqr(candidate) <= 2.25D) {
            return true;
        }
        Path path = this.friend.getNavigation().createPath(candidate, 0);
        return path != null && path.canReach() && this.hasReversibleVerticalSteps(path);
    }

    private boolean hasReversibleVerticalSteps(Path path) {
        int previousY = this.friend.blockPosition().getY();
        for (int index = 0; index < path.getNodeCount(); index++) {
            int nextY = path.getNode(index).y;
            if (Math.abs(nextY - previousY) > 1) {
                return false;
            }
            previousY = nextY;
        }
        return true;
    }

    interface BlockPlacementSupport {
        Block blockToPlace(FriendTask task);

        boolean isMatchingBlock(ItemStack stack, Block block);

        boolean canPlaceBlockAt(ServerLevel level, BlockPos pos);

        Optional<BlockPos> findStandPositionNearBlock(ServerLevel level, BlockPos target);
    }
}
