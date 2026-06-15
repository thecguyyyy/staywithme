package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlayerEngineThreatSafetyRunner {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Consumer<String> announcer;
    private final Function<BlockPos, String> positionFormatter;
    private final LocalThreatSafetyFallback fallback;

    PlayerEngineThreatSafetyRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Consumer<String> announcer,
            Function<BlockPos, String> positionFormatter,
            LocalThreatSafetyFallback fallback
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.announcer = announcer;
        this.positionFormatter = positionFormatter;
        this.fallback = fallback;
    }

    void retreatFromHostiles(FriendTask task, double speed) {
        int distance = Math.max(4, task == null || task.amount() <= 0 ? 16 : task.amount());
        Optional<LivingEntity> hostile = this.friend.getPerception().nearestHostile(distance);
        if (hostile.isEmpty()) {
            this.body.stop();
            this.resetTaskState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (this.taskState.active()
                && this.body.hasRetreatFromHostilesFinished(distance)) {
            this.body.stop();
            this.resetTaskState.run();
            Optional<LivingEntity> remainingHostile = this.friend.getPerception().nearestHostile(distance);
            if (remainingHostile.isEmpty()) {
                this.friend.getFriendBrain().completeTask();
            } else if (this.fallback.tryRetreatFromThreat(remainingHostile.get(), distance, speed)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.announcer.accept("PlayerEngine retreat ended with a hostile still nearby, so I am moving to a local safe point.");
            } else {
                this.friend.getFriendBrain().failTask("PlayerEngine retreat finished, but hostile mobs are still nearby.");
            }
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            if (this.fallback.tryRetreatFromThreat(hostile.get(), distance, speed)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.announcer.accept("Moving away from the hostile mob.");
                return;
            }
            this.friend.getFriendBrain().failTask("I cannot find a safe local retreat path away from hostile mobs.");
            return;
        }
        if (!this.body.retreatFromHostiles(distance)) {
            String status = PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 160);
            this.resetTaskState.run();
            if (this.fallback.tryRetreatFromThreat(hostile.get(), distance, speed)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.announcer.accept("PlayerEngine hostile retreat did not start (" + status + "), so I am using a local retreat fallback.");
                return;
            }
            this.friend.getFriendBrain().failTask("PlayerEngine hostile retreat did not start: " + status + ".");
            return;
        }
        this.taskState.startTask(
                "retreat_hostiles",
                distance,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to retreat from nearby hostile mobs."
        );
    }

    void retreatFromCreepers(FriendTask task, double speed) {
        int distance = Math.max(4, task == null || task.amount() <= 0 ? 10 : task.amount());
        Optional<? extends LivingEntity> creeper = this.friend.getPerception().nearestCreeper(distance);
        if (creeper.isEmpty()) {
            this.body.stop();
            this.resetTaskState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (this.taskState.active()
                && this.body.hasRetreatFromCreepersFinished(distance)) {
            this.body.stop();
            this.resetTaskState.run();
            if (this.friend.getPerception().nearestCreeper(distance).isEmpty()) {
                this.friend.getFriendBrain().completeTask();
            } else {
                this.friend.getFriendBrain().failTask("PlayerEngine creeper retreat finished, but a creeper is still nearby.");
            }
            return;
        }
        if (this.body.canUseHighLevelAcquisition() && this.body.retreatFromCreepers(distance)) {
            this.taskState.startTask(
                    "retreat_creepers",
                    distance,
                    this.friend,
                    FriendState.EXECUTING_TASK,
                    this.announcer,
                    "Using PlayerEngine to retreat from nearby creepers."
            );
            return;
        }
        if (this.fallback.tryRetreatFromThreat(creeper.get(), distance, speed)) {
            this.friend.setFriendState(FriendState.EXECUTING_TASK);
            this.announcer.accept("Moving away from the creeper.");
            return;
        }
        this.friend.getFriendBrain().failTask("I cannot find a safe local retreat path away from the creeper.");
    }

    void dodgeProjectiles(FriendTask task, double speed) {
        int horizontalDistance = Math.max(1, task == null || task.amount() <= 0 ? 4 : task.amount());
        int verticalDistance = 3;
        if (this.taskState.active()
                && this.body.hasDodgeProjectilesFinished(horizontalDistance, verticalDistance)) {
            this.body.stop();
            this.resetTaskState.run();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            this.tryLocalProjectileDodge(horizontalDistance, verticalDistance, speed, false, "");
            return;
        }
        if (!this.body.dodgeProjectiles(horizontalDistance, verticalDistance)) {
            String status = PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 160);
            this.resetTaskState.run();
            this.tryLocalProjectileDodge(horizontalDistance, verticalDistance, speed, true, status);
            return;
        }
        this.taskState.startTask(
                "dodge_projectiles",
                horizontalDistance,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to dodge incoming projectiles."
        );
    }

    void projectileProtectionWall(FriendTask task, double speed) {
        int range = Math.max(4, task == null || task.amount() <= 0 ? 16 : task.amount());
        Optional<Skeleton> threat = this.fallback.nearestSkeletonThreat(range);
        if (threat.isEmpty()) {
            this.body.stop();
            this.resetTaskState.run();
            this.fallback.resetProjectileWallTarget();
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (this.taskState.active()
                && this.body.hasProjectileProtectionWallFinished()) {
            this.body.stop();
            this.resetTaskState.run();
            Optional<Skeleton> remainingThreat = this.fallback.nearestSkeletonThreat(range);
            if (remainingThreat.isEmpty()) {
                this.fallback.resetProjectileWallTarget();
                this.friend.getFriendBrain().completeTask();
            } else if (this.fallback.tryProjectileWall(task, remainingThreat.get(), speed)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.announcer.accept("PlayerEngine projectile wall ended with a skeleton still aiming, so I am placing local cover.");
            } else {
                this.friend.getFriendBrain().failTask("PlayerEngine projectile wall finished, but a skeleton threat is still nearby.");
            }
            return;
        }
        if (!this.body.canUseHighLevelAcquisition()) {
            if (this.fallback.tryProjectileWall(task, threat.get(), speed)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.announcer.accept("Placing local cover against the skeleton.");
                return;
            }
            this.friend.getFriendBrain().failTask("Building a projectile protection wall needs PlayerEngine or a carried throwaway block with a reachable cover placement.");
            return;
        }
        if (!this.body.projectileProtectionWall()) {
            String status = PlayerEngineStatusText.shortStatus(this.body.highLevelAcquisitionStatus(), 160);
            this.resetTaskState.run();
            if (this.fallback.tryProjectileWall(task, threat.get(), speed)) {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.announcer.accept("PlayerEngine projectile wall did not start (" + status + "), so I am placing local cover.");
                return;
            }
            this.friend.getFriendBrain().failTask("PlayerEngine projectile protection wall did not start: " + status + ".");
            return;
        }
        this.taskState.startTask(
                "projectile_wall",
                range,
                this.friend,
                FriendState.EXECUTING_TASK,
                this.announcer,
                "Using PlayerEngine to build a projectile protection wall."
        );
    }

    private void tryLocalProjectileDodge(
            int horizontalDistance,
            int verticalDistance,
            double speed,
            boolean playerEngineFailedToStart,
            String status
    ) {
        Optional<BlockPos> dodgeTarget = this.fallback.findProjectileDodgeTarget(horizontalDistance, verticalDistance);
        if (dodgeTarget.isPresent()) {
            this.body.moveToNearby(dodgeTarget.get(), speed);
            this.friend.setFriendState(FriendState.EXECUTING_TASK);
            if (playerEngineFailedToStart) {
                this.announcer.accept("PlayerEngine projectile dodge did not start (" + status + "), so I am using a local dodge point.");
            } else {
                this.announcer.accept("Dodging to a local safe point at " + this.positionFormatter.apply(dodgeTarget.get()) + ".");
            }
            return;
        }
        if (!this.fallback.hasProjectileDodgeThreat(horizontalDistance, verticalDistance)) {
            this.body.stop();
            if (!playerEngineFailedToStart) {
                this.resetTaskState.run();
            }
            this.friend.getFriendBrain().completeTask();
            return;
        }
        if (playerEngineFailedToStart) {
            this.friend.getFriendBrain().failTask("PlayerEngine projectile dodge did not start: " + status + ".");
        } else {
            this.friend.getFriendBrain().failTask("Dodging projectiles needs PlayerEngine or a reachable local dodge point.");
        }
    }
}
