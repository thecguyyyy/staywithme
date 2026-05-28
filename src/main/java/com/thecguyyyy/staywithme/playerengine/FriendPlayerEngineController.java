package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.thecguyyyy.staywithme.StayWithMeMod;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class FriendPlayerEngineController {
    private final FriendEntity friend;
    private FriendAutomatoneBridge bridge;
    private IBaritone baritone;
    private boolean disabled;
    private String disabledReason = "";
    private BlockPos currentMoveGoal;
    private int moveGoalRefreshTicks;
    private UUID currentFollowTarget;
    private int followRefreshTicks;
    private String currentMineSignature;
    private int mineRefreshTicks;

    public FriendPlayerEngineController(FriendEntity friend) {
        this.friend = friend;
    }

    public void tick() {
        if (this.moveGoalRefreshTicks > 0) {
            this.moveGoalRefreshTicks--;
        }
        if (this.followRefreshTicks > 0) {
            this.followRefreshTicks--;
        }
        if (this.mineRefreshTicks > 0) {
            this.mineRefreshTicks--;
        }

        IBaritone active = this.baritone();
        if (active == null) {
            return;
        }

        try {
            FriendAutomatoneBridge activeBridge = this.bridge();
            if (activeBridge != null) {
                activeBridge.syncFromFriend();
                activeBridge.serverTick();
            }
            active.serverTick();
            if (activeBridge != null) {
                activeBridge.syncToFriend();
            }
        } catch (RuntimeException error) {
            this.disable("tick failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
        }
    }

    public boolean stop() {
        IBaritone active = this.baritone();
        if (active == null) {
            return false;
        }

        try {
            active.getFollowProcess().cancel();
            active.getMineProcess().cancel();
            active.getPathingBehavior().cancelEverything();
            active.getInputOverrideHandler().clearAllKeys();
            this.currentMoveGoal = null;
            this.moveGoalRefreshTicks = 0;
            this.currentFollowTarget = null;
            this.followRefreshTicks = 0;
            this.currentMineSignature = null;
            this.mineRefreshTicks = 0;
            return true;
        } catch (RuntimeException error) {
            this.disable("stop failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return false;
        }
    }

    public boolean moveTo(double x, double y, double z) {
        IBaritone active = this.baritone();
        if (active == null) {
            return false;
        }

        try {
            BlockPos target = BlockPos.containing(x, y, z);
            if (target.equals(this.currentMoveGoal) && this.moveGoalRefreshTicks > 0) {
                return true;
            }
            active.getFollowProcess().cancel();
            active.getMineProcess().cancel();
            active.getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
            this.currentMoveGoal = target.immutable();
            this.moveGoalRefreshTicks = 40;
            this.currentFollowTarget = null;
            this.followRefreshTicks = 0;
            this.currentMineSignature = null;
            this.mineRefreshTicks = 0;
            return true;
        } catch (RuntimeException error) {
            this.disable("move failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return false;
        }
    }

    public boolean follow(Entity target) {
        IBaritone active = this.baritone();
        if (active == null) {
            return false;
        }

        try {
            UUID targetUuid = target.getUUID();
            if (targetUuid.equals(this.currentFollowTarget) && this.followRefreshTicks > 0) {
                return true;
            }
            active.getCustomGoalProcess().setGoal(null);
            active.getMineProcess().cancel();
            active.getFollowProcess().follow(entity -> entity.getUUID().equals(targetUuid));
            this.currentFollowTarget = targetUuid;
            this.followRefreshTicks = 40;
            this.currentMoveGoal = null;
            this.moveGoalRefreshTicks = 0;
            this.currentMineSignature = null;
            this.mineRefreshTicks = 0;
            return true;
        } catch (RuntimeException error) {
            this.disable("follow failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return false;
        }
    }

    public boolean attack(Entity target) {
        if (target instanceof LivingEntity livingEntity) {
            this.friend.setTarget(livingEntity);
        }
        return false;
    }

    public boolean mineBlocks(int count, Block... blocks) {
        if (count <= 0 || blocks == null || blocks.length == 0) {
            return false;
        }

        IBaritone active = this.baritone();
        if (active == null) {
            return false;
        }

        try {
            String signature = this.mineSignature(count, blocks);
            if (signature.equals(this.currentMineSignature)
                    && this.mineRefreshTicks > 0
                    && active.getMineProcess().isActive()) {
                return true;
            }

            active.getFollowProcess().cancel();
            active.getCustomGoalProcess().setGoal(null);
            active.getMineProcess().mine(count, blocks);
            this.currentMineSignature = signature;
            this.mineRefreshTicks = 80;
            this.currentMoveGoal = null;
            this.moveGoalRefreshTicks = 0;
            this.currentFollowTarget = null;
            this.followRefreshTicks = 0;
            return true;
        } catch (RuntimeException error) {
            this.disable("mine failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return false;
        }
    }

    public boolean isMining() {
        IBaritone active = this.baritone();
        if (active == null) {
            return false;
        }

        try {
            return active.getMineProcess().isActive();
        } catch (RuntimeException error) {
            this.disable("mine status failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return false;
        }
    }

    public boolean isPathingAvailable() {
        return this.baritone() != null;
    }

    public String status() {
        if (this.disabled) {
            return "PlayerEngine disabled: " + this.disabledReason;
        }
        if (!PlayerEngineCompat.isAvailable()) {
            return "PlayerEngine missing; fallback active";
        }
        String bridgeStatus = this.bridge == null ? "bridge=not_initialized" : this.bridge.status();
        String pathingStatus = this.baritone == null ? "pathing=not_initialized" : "pathing=active";
        String mineStatus = this.currentMineSignature == null ? "mine=idle" : "mine=requested";
        return "PlayerEngine compat active, " + pathingStatus + ", " + mineStatus + ", " + bridgeStatus + ", fullController=not_enabled(provider_bridge_not_entity_bound)";
    }

    public String controllerName() {
        if (this.disabled) {
            return "playerengine_disabled_forge_fallback";
        }
        return this.isPathingAvailable() ? "playerengine_automaton" : "playerengine_unavailable_forge_fallback";
    }

    private FriendAutomatoneBridge bridge() {
        if (this.disabled) {
            return null;
        }
        if (this.bridge != null) {
            return this.bridge;
        }

        try {
            this.bridge = new FriendAutomatoneBridge(this.friend);
            return this.bridge;
        } catch (RuntimeException | LinkageError error) {
            this.disable("bridge init failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return null;
        }
    }

    private IBaritone baritone() {
        if (this.disabled) {
            return null;
        }
        if (this.baritone != null) {
            return this.baritone;
        }
        if (!PlayerEngineCompat.isAvailable()) {
            this.disabled = true;
            this.disabledReason = "PlayerEngine is not loaded.";
            return null;
        }

        try {
            this.bridge();
            this.baritone = BaritoneAPI.getProvider().getBaritone(this.friend);
            StayWithMeMod.LOGGER.info("PlayerEngine/Automatone baritone initialized for companion {}", this.friend.getUUID());
            return this.baritone;
        } catch (RuntimeException | LinkageError error) {
            this.disable("baritone init failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return null;
        }
    }

    private void disable(String reason, Throwable error) {
        this.disabled = true;
        this.disabledReason = reason;
        this.baritone = null;
        this.currentMoveGoal = null;
        this.currentFollowTarget = null;
        this.moveGoalRefreshTicks = 0;
        this.followRefreshTicks = 0;
        this.currentMineSignature = null;
        this.mineRefreshTicks = 0;
        StayWithMeMod.LOGGER.warn("Disabling PlayerEngine compat for companion {}: {}", this.friend.getUUID(), reason, error);
    }

    private String mineSignature(int count, Block... blocks) {
        return count + ":" + Arrays.stream(blocks)
                .map(Block::getDescriptionId)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
