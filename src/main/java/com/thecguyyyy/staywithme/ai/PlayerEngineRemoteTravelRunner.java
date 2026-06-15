package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

final class PlayerEngineRemoteTravelRunner {
    private static final double CLOSE_ENOUGH = 3.0D;

    private final EmbodiedController body;
    private final FriendEntity friend;
    private final PlayerEngineTaskState taskState;
    private final Runnable resetTaskState;
    private final Runnable resetConstructionPathRecovery;
    private final Consumer<String> announcer;
    private final Function<BlockPos, String> positionFormatter;
    private BlockPos target;
    private String label = "none";
    private boolean attempted;

    PlayerEngineRemoteTravelRunner(
            EmbodiedController body,
            FriendEntity friend,
            PlayerEngineTaskState taskState,
            Runnable resetTaskState,
            Runnable resetConstructionPathRecovery,
            Consumer<String> announcer,
            Function<BlockPos, String> positionFormatter
    ) {
        this.body = body;
        this.friend = friend;
        this.taskState = taskState;
        this.resetTaskState = resetTaskState;
        this.resetConstructionPathRecovery = resetConstructionPathRecovery;
        this.announcer = announcer;
        this.positionFormatter = positionFormatter;
    }

    Optional<ConstructionTravelResult> tick(
            BlockPos destination,
            String rawLabel,
            boolean moveStalled,
            boolean constructionMaterialRestockActive
    ) {
        if (!this.body.canUseHighLevelAcquisition()) {
            if (this.taskState.active("remote_goto")) {
                this.resetTaskState.run();
                this.attempted = true;
            }
            return Optional.empty();
        }
        if (constructionMaterialRestockActive
                || (this.taskState.active() && !this.taskState.active("remote_goto"))) {
            return Optional.empty();
        }

        String normalizedLabel = this.normalizedLabel(rawLabel);
        if (!this.isSameTravel(destination, normalizedLabel)) {
            if (this.taskState.active("remote_goto")) {
                this.body.stop();
                this.resetTaskState.run();
            }
            this.reset();
            this.target = destination.immutable();
            this.label = normalizedLabel;
        }

        if (this.taskState.active("remote_goto")) {
            return this.tickActiveTravel(destination, normalizedLabel, moveStalled);
        }

        if (this.attempted) {
            return Optional.empty();
        }

        if (!this.body.goToBlock(destination, CLOSE_ENOUGH)) {
            this.attempted = true;
            this.announcer.accept("PlayerEngine could not start goto for "
                    + normalizedLabel
                    + "; falling back to route construction.");
            return Optional.empty();
        }

        this.taskState.startTask(
                "remote_goto",
                0,
                this.friend,
                FriendState.RETURNING,
                this.announcer,
                "Using PlayerEngine goto for "
                        + normalizedLabel
                        + " before constructing a route."
        );
        return Optional.of(ConstructionTravelResult.WORKING);
    }

    void reset() {
        this.target = null;
        this.label = "none";
        this.attempted = false;
    }

    String summary() {
        if (this.target == null
                && !this.attempted
                && !this.taskState.active("remote_goto")) {
            return "none";
        }
        return "target="
                + this.positionFormatter.apply(this.target)
                + ",label="
                + this.label
                + ",attempted="
                + this.attempted
                + ",active="
                + this.taskState.active("remote_goto");
    }

    private Optional<ConstructionTravelResult> tickActiveTravel(BlockPos destination, String normalizedLabel, boolean moveStalled) {
        String status = this.body.highLevelAcquisitionStatus();
        if (this.body.hasGoToBlockFinished(destination, CLOSE_ENOUGH)
                || this.isFinished(destination, status)) {
            this.body.stop();
            this.resetTaskState.run();
            if (this.friend.blockPosition().distSqr(destination) <= 9.0D) {
                this.reset();
                this.resetConstructionPathRecovery.run();
                return Optional.of(ConstructionTravelResult.COMPLETE);
            }
            this.attempted = true;
            this.announcer.accept(
                    "PlayerEngine goto finished away from "
                            + normalizedLabel
                            + "; falling back to route construction. status="
                            + PlayerEngineStatusText.shortStatus(status, 120)
                            + "."
            );
            return Optional.empty();
        }

        if (moveStalled) {
            this.resetTaskState.run();
            this.attempted = true;
            this.announcer.accept(
                    "PlayerEngine goto to "
                            + normalizedLabel
                            + " stopped making progress; falling back to route construction. status="
                            + PlayerEngineStatusText.shortStatus(status, 120)
                            + "."
            );
            return Optional.empty();
        }

        if (this.isInactive(destination, status) || !this.isActive(destination, status)) {
            this.resetTaskState.run();
            this.attempted = true;
            this.announcer.accept(
                    "PlayerEngine could not keep a goto route to "
                            + normalizedLabel
                            + "; falling back to route construction. status="
                            + PlayerEngineStatusText.shortStatus(status, 120)
                            + "."
            );
            return Optional.empty();
        }
        this.friend.setFriendState(FriendState.RETURNING);
        return Optional.of(ConstructionTravelResult.WORKING);
    }

    private boolean isSameTravel(BlockPos destination, String normalizedLabel) {
        return this.target != null
                && this.target.equals(destination)
                && Objects.equals(this.label, normalizedLabel);
    }

    private String normalizedLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return "destination";
        }
        return rawLabel.trim();
    }

    private boolean isActive(BlockPos destination, String status) {
        if (status == null || !status.contains(this.needle(destination))) {
            return false;
        }
        return status.startsWith("started(") || status.startsWith("running(");
    }

    private boolean isFinished(BlockPos destination, String status) {
        if (status == null || !status.contains(this.needle(destination))) {
            return false;
        }
        return status.startsWith("callback_finished(") || status.startsWith("already_satisfied(");
    }

    private boolean isInactive(BlockPos destination, String status) {
        return status != null
                && status.startsWith("inactive_without_finish(")
                && status.contains(this.needle(destination));
    }

    private String needle(BlockPos destination) {
        return "goto:"
                + destination.getX()
                + ","
                + destination.getY()
                + ","
                + destination.getZ()
                + ":";
    }
}
