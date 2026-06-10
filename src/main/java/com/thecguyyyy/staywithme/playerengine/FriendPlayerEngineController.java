package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.commands.DepositCommand;
import com.player2.playerengine.player2api.utils.CharacterUtils;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.construction.ClearLiquidTask;
import com.player2.playerengine.tasks.construction.PlaceBlockTask;
import com.player2.playerengine.tasks.construction.PlaceStructureBlockTask;
import com.player2.playerengine.tasks.construction.ProjectileProtectionWallTask;
import com.player2.playerengine.tasks.construction.PutOutFireTask;
import com.player2.playerengine.tasks.container.SmeltInFurnaceTask;
import com.player2.playerengine.tasks.container.StoreInAnyContainerTask;
import com.player2.playerengine.tasks.entity.GiveItemToPlayerTask;
import com.player2.playerengine.tasks.entity.HeroTask;
import com.player2.playerengine.tasks.entity.KillEntityTask;
import com.player2.playerengine.tasks.misc.EquipArmorTask;
import com.player2.playerengine.tasks.misc.FarmTask;
import com.player2.playerengine.tasks.misc.FishTask;
import com.player2.playerengine.tasks.misc.SleepThroughNightTask;
import com.player2.playerengine.tasks.movement.DodgeProjectilesTask;
import com.player2.playerengine.tasks.movement.EscapeFromLavaTask;
import com.player2.playerengine.tasks.movement.FollowPlayerTask;
import com.player2.playerengine.tasks.movement.GetOutOfWaterTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import com.player2.playerengine.tasks.movement.GetToYTask;
import com.player2.playerengine.tasks.movement.PickupDroppedItemTask;
import com.player2.playerengine.tasks.movement.RunAwayFromCreepersTask;
import com.player2.playerengine.tasks.movement.RunAwayFromHostilesTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.resources.CollectFoodTask;
import com.player2.playerengine.tasks.resources.CollectFuelTask;
import com.player2.playerengine.tasks.resources.CollectMeatTask;
import com.player2.playerengine.tasks.resources.GetBuildingMaterialsTask;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.SmeltTarget;
import com.thecguyyyy.staywithme.StayWithMeMod;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class FriendPlayerEngineController {
    private static final String PLAYER2_GAME_ID = "staywithme";

    private final FriendEntity friend;
    private IBaritone baritone;
    private PlayerEngineController controller;
    private boolean disabled;
    private String disabledReason = "";
    private BlockPos currentMoveGoal;
    private int moveGoalRefreshTicks;
    private UUID currentFollowTarget;
    private int followRefreshTicks;
    private String currentMineSignature;
    private int mineRefreshTicks;
    private String currentAcquisitionSignature;
    private boolean acquisitionFinished;
    private int acquisitionTicks;
    private String lastAcquisitionStatus = "idle";

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
            PlayerEngineProviderHost host = this.providerHost();
            if (host != null) {
                host.syncPlayerEngineStateFromFriend();
                if (this.friend.level() instanceof ServerLevel serverLevel) {
                    host.tickPlayerEngineManagers(serverLevel);
                }
            }

            PlayerEngineController highLevel = this.controller();
            if (highLevel != null) {
                this.friend.getOwnerPlayer().ifPresent(highLevel::setOwner);
                highLevel.serverTick();
                this.tickAcquisitionWatchdog(highLevel);
            } else {
                active.serverTick();
            }

            if (host != null) {
                host.syncPlayerEngineStateToFriend();
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
            if (this.controller != null) {
                this.controller.stop();
            }
            active.getFollowProcess().cancel();
            active.getMineProcess().cancel();
            active.getPathingBehavior().cancelEverything();
            active.getInputOverrideHandler().clearAllKeys();
            this.clearTransientRequests();
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
            this.cancelHighLevelTask();
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
            this.cancelHighLevelTask();
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

    public boolean followPlayer(String playerName, double followDistance) {
        if (playerName == null || playerName.isBlank()) {
            this.lastAcquisitionStatus = "invalid_follow_player";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.followPlayerSignature(playerName, followDistance);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new FollowPlayerTask(playerName.trim(), Math.max(1.0D, followDistance)), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "follow player failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean returnToEntity(Entity target, double closeEnoughDistance) {
        if (target == null || target.isRemoved()) {
            this.lastAcquisitionStatus = "invalid_return_target";
            return false;
        }
        double closeEnough = Math.max(1.0D, closeEnoughDistance);
        String signature = this.returnToEntitySignature(target, closeEnough);
        if (this.friend.distanceTo(target) <= closeEnough) {
            this.currentAcquisitionSignature = signature;
            this.acquisitionFinished = true;
            this.acquisitionTicks = 0;
            this.lastAcquisitionStatus = "already_satisfied(" + signature + ")";
            return true;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new GetToEntityTask(target, closeEnough), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "return to entity failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasReturnToEntityFinished(Entity target, double closeEnoughDistance) {
        if (target == null || target.isRemoved()) {
            return false;
        }
        double closeEnough = Math.max(1.0D, closeEnoughDistance);
        String signature = this.returnToEntitySignature(target, closeEnough);
        return this.friend.distanceTo(target) <= closeEnough
                || (this.acquisitionFinished
                && signature.equals(this.currentAcquisitionSignature)
                && (this.lastAcquisitionStatus.startsWith("callback_finished(")
                || this.lastAcquisitionStatus.startsWith("already_satisfied(")));
    }

    public boolean goToBlock(BlockPos target, double closeEnoughDistance) {
        if (target == null) {
            this.lastAcquisitionStatus = "invalid_goto_target";
            return false;
        }
        double closeEnough = Math.max(1.0D, closeEnoughDistance);
        String signature = this.goToBlockSignature(target, closeEnough);
        if (this.isCloseToBlock(target, closeEnough)) {
            this.currentAcquisitionSignature = signature;
            this.acquisitionFinished = true;
            this.acquisitionTicks = 0;
            this.lastAcquisitionStatus = "already_satisfied(" + signature + ")";
            return true;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new GetToBlockTask(target), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "go to block failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasGoToBlockFinished(BlockPos target, double closeEnoughDistance) {
        if (target == null) {
            return false;
        }
        double closeEnough = Math.max(1.0D, closeEnoughDistance);
        String signature = this.goToBlockSignature(target, closeEnough);
        return this.isCloseToBlock(target, closeEnough)
                || (this.acquisitionFinished
                && signature.equals(this.currentAcquisitionSignature)
                && (this.lastAcquisitionStatus.startsWith("callback_finished(")
                || this.lastAcquisitionStatus.startsWith("already_satisfied(")));
    }

    public boolean goToYLevel(int yLevel) {
        String signature = this.goToYLevelSignature(yLevel);
        if (this.friend.blockPosition().getY() == yLevel) {
            this.currentAcquisitionSignature = signature;
            this.acquisitionFinished = true;
            this.acquisitionTicks = 0;
            this.lastAcquisitionStatus = "already_satisfied(" + signature + ")";
            return true;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new GetToYTask(yLevel), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "go to y failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasGoToYLevelFinished(int yLevel) {
        String signature = this.goToYLevelSignature(yLevel);
        return this.friend.blockPosition().getY() == yLevel
                || (this.acquisitionFinished && signature.equals(this.currentAcquisitionSignature));
    }

    public boolean placeBlockAt(BlockPos target, String rawBlockTarget) {
        if (target == null) {
            this.lastAcquisitionStatus = "invalid_place_target";
            return false;
        }
        PlaceBlockRequest request = placeBlockRequest(rawBlockTarget);
        if (request == null) {
            this.lastAcquisitionStatus = "unsupported_place_block(" + rawBlockTarget + ")";
            return false;
        }
        String signature = this.placeBlockSignature(target, request.normalizedTarget());
        if (this.isPlaceBlockSatisfied(target, request)) {
            this.currentAcquisitionSignature = signature;
            this.acquisitionFinished = true;
            this.acquisitionTicks = 0;
            this.lastAcquisitionStatus = "already_satisfied(" + signature + ")";
            return true;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            if (request.throwaway()) {
                highLevel.runUserTask(new PlaceStructureBlockTask(target), () -> {
                    this.acquisitionFinished = true;
                    this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
                });
            } else {
                highLevel.runUserTask(new PlaceBlockTask(target, request.blocks()), () -> {
                    this.acquisitionFinished = true;
                    this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
                });
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "place block failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasPlaceBlockAtFinished(BlockPos target, String rawBlockTarget) {
        if (target == null) {
            return false;
        }
        PlaceBlockRequest request = placeBlockRequest(rawBlockTarget);
        if (request == null) {
            return false;
        }
        String signature = this.placeBlockSignature(target, request.normalizedTarget());
        return this.isPlaceBlockSatisfied(target, request)
                || (this.acquisitionFinished && signature.equals(this.currentAcquisitionSignature));
    }

    public boolean attack(Entity target) {
        if (target instanceof LivingEntity livingEntity) {
            this.friend.setTarget(livingEntity);
        }
        return false;
    }

    public boolean attackTarget(Entity target) {
        if (target == null || !target.isAlive()) {
            this.lastAcquisitionStatus = "invalid_attack_target";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.attackTargetSignature(target);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new KillEntityTask(target), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "attack target failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasAttackTargetFinished(Entity target) {
        if (target == null) {
            return false;
        }
        String signature = this.attackTargetSignature(target);
        return !target.isAlive()
                || (this.acquisitionFinished
                && signature.equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished("));
    }

    public boolean protectPlayer() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.protectPlayerSignature();
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new HeroTask(), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "protect player failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean retreatFromHostiles(double distance) {
        double retreatDistance = Math.max(4.0D, distance);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.retreatFromHostilesSignature(retreatDistance);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new RunAwayFromHostilesTask(retreatDistance, true), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "retreat from hostiles failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasRetreatFromHostilesFinished(double distance) {
        return this.acquisitionFinished
                && this.retreatFromHostilesSignature(Math.max(4.0D, distance)).equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean retreatFromCreepers(double distance) {
        double retreatDistance = Math.max(4.0D, distance);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.retreatFromCreepersSignature(retreatDistance);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new RunAwayFromCreepersTask(retreatDistance), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "retreat from creepers failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasRetreatFromCreepersFinished(double distance) {
        return this.acquisitionFinished
                && this.retreatFromCreepersSignature(Math.max(4.0D, distance)).equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean dodgeProjectiles(double horizontalDistance, double verticalDistance) {
        double horizontal = Math.max(1.0D, horizontalDistance);
        double vertical = Math.max(1.0D, verticalDistance);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.dodgeProjectilesSignature(horizontal, vertical);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new DodgeProjectilesTask(horizontal, vertical), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "dodge projectiles failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasDodgeProjectilesFinished(double horizontalDistance, double verticalDistance) {
        return this.acquisitionFinished
                && this.dodgeProjectilesSignature(
                        Math.max(1.0D, horizontalDistance),
                        Math.max(1.0D, verticalDistance)
                ).equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean projectileProtectionWall() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.projectileProtectionWallSignature();
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new ProjectileProtectionWallTask(highLevel), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "projectile protection wall failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasProjectileProtectionWallFinished() {
        return this.acquisitionFinished
                && this.projectileProtectionWallSignature().equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
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

            this.cancelHighLevelTask();
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

    public boolean acquireItem(String catalogueName, int count) {
        if (catalogueName == null || catalogueName.isBlank() || count <= 0) {
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        String normalized = resolveCatalogueName(catalogueName);
        if (normalized == null) {
            this.lastAcquisitionStatus = "missing_catalogue_task("
                    + catalogueName
                    + ", candidates="
                    + PlayerEngineCatalogueNames.candidates(catalogueName)
                    + ", closest="
                    + PlayerEngineCatalogueDiagnostics.closestMatches(catalogueName, 5)
                    + ")";
            return false;
        }
        try {
            String signature = this.acquisitionSignature(normalized, count);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            ResourceTask task = TaskCatalogue.getItemTask(normalized, count);
            if (task == null) {
                this.lastAcquisitionStatus = "catalogue_returned_null(" + normalized + ")";
                return false;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(task, () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "acquire failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
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

    public boolean collectBuildingMaterials(int count) {
        int targetCount = Math.max(1, count);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.buildingMaterialsSignature(targetCount);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new GetBuildingMaterialsTask(targetCount), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "building materials acquire failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasBuildingMaterialsCollectionFinished(int count) {
        return this.acquisitionFinished
                && this.buildingMaterialsSignature(Math.max(1, count))
                .equals(this.currentAcquisitionSignature);
    }

    public boolean pickupDroppedItem(String itemName, int count) {
        if (itemName == null || itemName.isBlank() || count <= 0) {
            this.lastAcquisitionStatus = "invalid_pickup_target(" + itemName + ")";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        PickupRequest request = pickupRequest(itemName, count);
        if (request == null) {
            this.lastAcquisitionStatus = "missing_pickup_target("
                    + itemName
                    + ", candidates="
                    + PlayerEngineCatalogueNames.candidates(itemName)
                    + ", closest="
                    + PlayerEngineCatalogueDiagnostics.closestMatches(itemName, 5)
                    + ")";
            return false;
        }

        try {
            String signature = this.pickupDroppedItemSignature(request.normalizedTarget(), count);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new PickupDroppedItemTask(request.target(), true), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "pickup dropped item failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasPickupDroppedItemFinished(String itemName, int count) {
        PickupRequest request = pickupRequest(itemName, count);
        String target = request == null
                ? PlayerEngineCatalogueNames.normalize(itemName)
                : request.normalizedTarget();
        return this.acquisitionFinished
                && this.pickupDroppedItemSignature(target, count)
                .equals(this.currentAcquisitionSignature);
    }

    public boolean giveItemToPlayer(String playerName, String catalogueName, int count) {
        if (playerName == null || playerName.isBlank()) {
            this.lastAcquisitionStatus = "invalid_player";
            return false;
        }
        if (catalogueName == null || catalogueName.isBlank() || count <= 0) {
            this.lastAcquisitionStatus = "invalid_give_target(" + catalogueName + ")";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        GiveRequest request = giveRequest(catalogueName, count);
        if (request == null) {
            this.lastAcquisitionStatus = "missing_give_target("
                    + catalogueName
                    + ", candidates="
                    + PlayerEngineCatalogueNames.candidates(catalogueName)
                    + ", closest="
                    + PlayerEngineCatalogueDiagnostics.closestMatches(catalogueName, 5)
                    + ")";
            return false;
        }

        try {
            String signature = this.giveItemSignature(playerName, request.normalizedTarget(), count);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new GiveItemToPlayerTask(playerName.trim(), request.target()), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "give item failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasGiveItemFinished(String playerName, String catalogueName, int count) {
        GiveRequest request = giveRequest(catalogueName, count);
        String target = request == null
                ? PlayerEngineCatalogueNames.normalize(catalogueName)
                : request.normalizedTarget();
        return this.acquisitionFinished
                && this.giveItemSignature(playerName, target, count)
                .equals(this.currentAcquisitionSignature);
    }

    public boolean depositInventory() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.depositInventorySignature();
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            ItemTarget[] targets = DepositCommand.getAllNonEquippedOrToolItemsAsTarget(highLevel);
            if (targets.length == 0) {
                this.currentAcquisitionSignature = signature;
                this.acquisitionFinished = true;
                this.acquisitionTicks = 0;
                this.lastAcquisitionStatus = "already_satisfied(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new StoreInAnyContainerTask(false, targets), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "deposit inventory failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasDepositInventoryFinished() {
        return this.acquisitionFinished
                && this.depositInventorySignature().equals(this.currentAcquisitionSignature);
    }

    public boolean isAcquiringItem() {
        PlayerEngineController highLevel = this.controller;
        return highLevel != null
                && this.currentAcquisitionSignature != null
                && !this.acquisitionFinished
                && highLevel.getUserTaskChain().isActive();
    }

    public boolean hasAcquisitionFinished(String catalogueName, int count) {
        String normalized = resolveCatalogueName(catalogueName);
        if (normalized == null) {
            normalized = PlayerEngineCatalogueNames.normalize(catalogueName);
        }
        return this.acquisitionFinished
                && this.acquisitionSignature(normalized, count)
                .equals(this.currentAcquisitionSignature);
    }

    public boolean collectFood(int foodUnits) {
        int units = Math.max(1, foodUnits);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.foodAcquisitionSignature(units);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new CollectFoodTask(units), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "food acquire failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasFoodCollectionFinished(int foodUnits) {
        return this.acquisitionFinished
                && this.foodAcquisitionSignature(Math.max(1, foodUnits))
                .equals(this.currentAcquisitionSignature);
    }

    public boolean collectMeat(int foodUnits) {
        int units = Math.max(1, foodUnits);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.meatAcquisitionSignature(units);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new CollectMeatTask(units), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "meat acquire failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasMeatCollectionFinished(int foodUnits) {
        return this.acquisitionFinished
                && this.meatAcquisitionSignature(Math.max(1, foodUnits))
                .equals(this.currentAcquisitionSignature);
    }

    public boolean collectFuel(int fuelItems) {
        int items = Math.max(1, fuelItems);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.fuelAcquisitionSignature(items);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new CollectFuelTask(items), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "fuel acquire failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasFuelCollectionFinished(int fuelItems) {
        return this.acquisitionFinished
                && this.fuelAcquisitionSignature(Math.max(1, fuelItems))
                .equals(this.currentAcquisitionSignature);
    }

    public boolean smeltItem(String rawTarget, int count) {
        SmeltRequest request = smeltRequest(rawTarget, count);
        if (request == null) {
            this.lastAcquisitionStatus = "unsupported_smelt_target(" + rawTarget + ")";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.smeltItemSignature(request.normalizedTarget(), count);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new SmeltInFurnaceTask(request.target()), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "smelt item failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasSmeltItemFinished(String rawTarget, int count) {
        SmeltRequest request = smeltRequest(rawTarget, count);
        if (request == null) {
            return false;
        }
        return this.acquisitionFinished
                && this.smeltItemSignature(request.normalizedTarget(), count)
                .equals(this.currentAcquisitionSignature);
    }

    public boolean fish() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = "fish";
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new FishTask(), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "fish failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean farm(int range) {
        int normalizedRange = Math.max(1, range);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = "farm:" + normalizedRange;
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new FarmTask(normalizedRange, this.friend.blockPosition().immutable()), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "farm failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean explore(double distance) {
        double exploreDistance = Math.max(8.0D, distance);
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.exploreSignature(exploreDistance);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new TimeoutWanderTask((float) exploreDistance, true), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "explore failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasExploreFinished(double distance) {
        return this.acquisitionFinished
                && this.exploreSignature(Math.max(8.0D, distance)).equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean sleepThroughNight() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.sleepThroughNightSignature();
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new SleepThroughNightTask(), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "sleep through night failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasSleepThroughNightFinished() {
        return this.acquisitionFinished
                && this.sleepThroughNightSignature().equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean getOutOfWater() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.getOutOfWaterSignature();
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new GetOutOfWaterTask(), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "get out of water failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasGetOutOfWaterFinished() {
        return this.acquisitionFinished
                && this.getOutOfWaterSignature().equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean escapeLava() {
        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.escapeLavaSignature();
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new EscapeFromLavaTask(highLevel), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "escape lava failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasEscapeLavaFinished() {
        return this.acquisitionFinished
                && this.escapeLavaSignature().equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean clearLiquid(BlockPos liquidPosition) {
        if (liquidPosition == null) {
            this.lastAcquisitionStatus = "invalid_liquid_position";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.clearLiquidSignature(liquidPosition);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new ClearLiquidTask(liquidPosition), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "clear liquid failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasClearLiquidFinished(BlockPos liquidPosition) {
        return liquidPosition != null
                && this.acquisitionFinished
                && this.clearLiquidSignature(liquidPosition).equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean putOutFire(BlockPos firePosition) {
        if (firePosition == null) {
            this.lastAcquisitionStatus = "invalid_fire_position";
            return false;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            String signature = this.putOutFireSignature(firePosition);
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new PutOutFireTask(firePosition), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "put out fire failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasPutOutFireFinished(BlockPos firePosition) {
        return firePosition != null
                && this.acquisitionFinished
                && this.putOutFireSignature(firePosition).equals(this.currentAcquisitionSignature)
                && this.lastAcquisitionStatus.startsWith("callback_finished(");
    }

    public boolean equipArmor(String target) {
        ArmorRequest request = armorRequest(target);
        if (request == null) {
            this.lastAcquisitionStatus = "invalid_armor_target(" + target + ")";
            return false;
        }
        String signature = this.armorEquipmentSignature(request.normalizedTarget());
        if (this.hasArmorEquipped(request)) {
            this.currentAcquisitionSignature = signature;
            this.acquisitionFinished = true;
            this.acquisitionTicks = 0;
            this.lastAcquisitionStatus = "already_satisfied(" + signature + ")";
            return true;
        }

        PlayerEngineController highLevel = this.controller();
        if (highLevel == null) {
            this.lastAcquisitionStatus = "task_controller_unavailable";
            return false;
        }

        try {
            if (signature.equals(this.currentAcquisitionSignature)
                    && !this.acquisitionFinished
                    && highLevel.getUserTaskChain().isActive()) {
                this.lastAcquisitionStatus = "running(" + signature + ")";
                return true;
            }

            this.prepareHighLevelTask(signature);
            highLevel.runUserTask(new EquipArmorTask(request.items()), () -> {
                this.acquisitionFinished = true;
                this.lastAcquisitionStatus = "callback_finished(" + signature + ")";
            });
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "equip armor failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
            return false;
        }
    }

    public boolean hasArmorEquipmentFinished(String target) {
        ArmorRequest request = armorRequest(target);
        if (request == null) {
            return false;
        }
        return this.hasArmorEquipped(request)
                || (this.acquisitionFinished
                && this.armorEquipmentSignature(request.normalizedTarget()).equals(this.currentAcquisitionSignature));
    }

    public boolean canUseHighLevelAcquisition() {
        return this.controller() != null;
    }

    public String acquisitionStatus() {
        return this.lastAcquisitionStatus;
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
        PlayerEngineProviderHost host = this.providerHost();
        String providerStatus = host == null ? "providers=missing_entity_interface" : host.playerEngineProviderStatus();
        String pathingStatus = this.baritone == null ? "pathing=not_initialized" : "pathing=active";
        String controllerStatus = this.controller == null ? "taskController=not_initialized" : "taskController=active";
        String mineStatus = this.currentMineSignature == null ? "mine=idle" : "mine=requested";
        String acquireStatus = this.currentAcquisitionSignature == null
                ? "highLevel=" + this.lastAcquisitionStatus
                : "highLevel=" + (this.acquisitionFinished ? "finished" : "running")
                + "("
                + this.currentAcquisitionSignature
                + ", ticks="
                + this.acquisitionTicks
                + ", "
                + this.lastAcquisitionStatus
                + ")";
        return "PlayerEngine first, "
                + pathingStatus
                + ", "
                + controllerStatus
                + ", "
                + this.taskRunnerStatus()
                + ", "
                + mineStatus
                + ", "
                + acquireStatus
                + ", "
                + providerStatus;
    }

    public String controllerName() {
        if (this.disabled) {
            return "playerengine_disabled_forge_fallback";
        }
        return this.canUseHighLevelAcquisition() ? "playerengine_taskcatalogue" : "playerengine_unavailable_forge_fallback";
    }

    private PlayerEngineController controller() {
        if (this.disabled) {
            return null;
        }
        if (this.controller != null) {
            return this.controller;
        }
        IBaritone active = this.baritone();
        if (active == null) {
            return null;
        }
        if (this.providerHost() == null) {
            return null;
        }

        try {
            this.controller = new PlayerEngineController(active, CharacterUtils.DEFAULT_CHARACTER, PLAYER2_GAME_ID);
            this.friend.getOwnerPlayer().ifPresent(this.controller::setOwner);
            StayWithMeMod.LOGGER.info(
                    "PlayerEngine TaskCatalogue controller initialized for companion {}",
                    this.friend.getUUID()
            );
            return this.controller;
        } catch (RuntimeException | LinkageError error) {
            this.disable(
                    "task controller init failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(),
                    error
            );
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
            this.baritone = BaritoneAPI.getProvider().getBaritone(this.friend);
            StayWithMeMod.LOGGER.info("PlayerEngine/Automatone baritone initialized for companion {}", this.friend.getUUID());
            return this.baritone;
        } catch (RuntimeException | LinkageError error) {
            this.disable("baritone init failed: " + error.getClass().getSimpleName() + ": " + error.getMessage(), error);
            return null;
        }
    }

    private PlayerEngineProviderHost providerHost() {
        if (this.friend instanceof PlayerEngineProviderHost host) {
            return host;
        }
        return null;
    }

    private void cancelHighLevelTask() {
        if (this.controller != null && this.currentAcquisitionSignature != null) {
            this.controller.cancelUserTask();
        }
        this.currentAcquisitionSignature = null;
        this.acquisitionFinished = false;
        this.acquisitionTicks = 0;
        this.lastAcquisitionStatus = "cancelled";
    }

    private void clearTransientRequests() {
        this.currentMoveGoal = null;
        this.moveGoalRefreshTicks = 0;
        this.currentFollowTarget = null;
        this.followRefreshTicks = 0;
        this.currentMineSignature = null;
        this.mineRefreshTicks = 0;
        this.currentAcquisitionSignature = null;
        this.acquisitionFinished = false;
        this.acquisitionTicks = 0;
        this.lastAcquisitionStatus = "idle";
    }

    private void disable(String reason, Throwable error) {
        this.disabled = true;
        this.disabledReason = reason;
        this.baritone = null;
        this.controller = null;
        this.clearTransientRequests();
        StayWithMeMod.LOGGER.warn("Disabling PlayerEngine compat for companion {}: {}", this.friend.getUUID(), reason, error);
    }

    private String mineSignature(int count, Block... blocks) {
        return count + ":" + Arrays.stream(blocks)
                .map(Block::getDescriptionId)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private String acquisitionSignature(String catalogueName, int count) {
        return PlayerEngineCatalogueNames.normalize(catalogueName) + ":" + Math.max(1, count);
    }

    private String foodAcquisitionSignature(int foodUnits) {
        return "food:" + Math.max(1, foodUnits);
    }

    private String meatAcquisitionSignature(int foodUnits) {
        return "meat:" + Math.max(1, foodUnits);
    }

    private String fuelAcquisitionSignature(int fuelItems) {
        return "fuel:" + Math.max(1, fuelItems);
    }

    private String smeltItemSignature(String target, int count) {
        return "smelt:" + PlayerEngineCatalogueNames.normalize(target) + ":" + Math.max(1, count);
    }

    private String exploreSignature(double distance) {
        return "explore:" + String.format(Locale.ROOT, "%.1f", Math.max(8.0D, distance));
    }

    private String buildingMaterialsSignature(int count) {
        return "building_materials:" + Math.max(1, count);
    }

    private String armorEquipmentSignature(String target) {
        return "equip_armor:" + target;
    }

    private String giveItemSignature(String playerName, String target, int count) {
        String normalizedPlayer = playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
        return "give:" + normalizedPlayer + ":" + PlayerEngineCatalogueNames.normalize(target) + ":" + Math.max(1, count);
    }

    private String pickupDroppedItemSignature(String target, int count) {
        return "pickup:" + PlayerEngineCatalogueNames.normalize(target) + ":" + Math.max(1, count);
    }

    private String depositInventorySignature() {
        return "deposit_inventory";
    }

    private String sleepThroughNightSignature() {
        return "sleep_through_night";
    }

    private String getOutOfWaterSignature() {
        return "get_out_of_water";
    }

    private String escapeLavaSignature() {
        return "escape_lava";
    }

    private String clearLiquidSignature(BlockPos liquidPosition) {
        return "clear_liquid:"
                + liquidPosition.getX()
                + ","
                + liquidPosition.getY()
                + ","
                + liquidPosition.getZ();
    }

    private String putOutFireSignature(BlockPos firePosition) {
        return "put_out_fire:"
                + firePosition.getX()
                + ","
                + firePosition.getY()
                + ","
                + firePosition.getZ();
    }

    private String followPlayerSignature(String playerName, double followDistance) {
        String normalizedPlayer = playerName == null ? "" : playerName.trim().toLowerCase(Locale.ROOT);
        return "follow:" + normalizedPlayer + ":" + String.format(Locale.ROOT, "%.1f", Math.max(1.0D, followDistance));
    }

    private String returnToEntitySignature(Entity target, double closeEnoughDistance) {
        UUID uuid = target == null ? new UUID(0L, 0L) : target.getUUID();
        return "return:" + uuid + ":" + String.format(Locale.ROOT, "%.1f", Math.max(1.0D, closeEnoughDistance));
    }

    private String goToBlockSignature(BlockPos target, double closeEnoughDistance) {
        return "goto:"
                + target.getX()
                + ","
                + target.getY()
                + ","
                + target.getZ()
                + ":"
                + String.format(Locale.ROOT, "%.1f", Math.max(1.0D, closeEnoughDistance));
    }

    private String goToYLevelSignature(int yLevel) {
        return "goto_y:" + yLevel;
    }

    private String placeBlockSignature(BlockPos target, String blockTarget) {
        return "place:"
                + PlayerEngineCatalogueNames.normalize(blockTarget)
                + ":"
                + target.getX()
                + ","
                + target.getY()
                + ","
                + target.getZ();
    }

    private boolean isCloseToBlock(BlockPos target, double closeEnoughDistance) {
        return this.friend.position()
                .closerThan(target.getCenter(), Math.max(1.0D, closeEnoughDistance));
    }

    private boolean isPlaceBlockSatisfied(BlockPos target, PlaceBlockRequest request) {
        if (target == null || request == null || !this.friend.level().hasChunkAt(target)) {
            return false;
        }
        var state = this.friend.level().getBlockState(target);
        if (request.throwaway()) {
            return !state.isAir() && state.isSolidRender(this.friend.level(), target);
        }
        for (Block block : request.blocks()) {
            if (state.is(block)) {
                return true;
            }
        }
        return false;
    }

    private String attackTargetSignature(Entity target) {
        UUID uuid = target == null ? new UUID(0L, 0L) : target.getUUID();
        return "attack:" + uuid;
    }

    private String protectPlayerSignature() {
        return "protect_player";
    }

    private String retreatFromHostilesSignature(double distance) {
        return "retreat_hostiles:" + String.format(Locale.ROOT, "%.1f", Math.max(4.0D, distance));
    }

    private String retreatFromCreepersSignature(double distance) {
        return "retreat_creepers:" + String.format(Locale.ROOT, "%.1f", Math.max(4.0D, distance));
    }

    private String dodgeProjectilesSignature(double horizontalDistance, double verticalDistance) {
        return "dodge_projectiles:"
                + String.format(Locale.ROOT, "%.1f", Math.max(1.0D, horizontalDistance))
                + ":"
                + String.format(Locale.ROOT, "%.1f", Math.max(1.0D, verticalDistance));
    }

    private String projectileProtectionWallSignature() {
        return "projectile_wall";
    }

    private void prepareHighLevelTask(String signature) {
        IBaritone active = this.baritone();
        if (active != null) {
            active.getFollowProcess().cancel();
            active.getMineProcess().cancel();
            active.getCustomGoalProcess().setGoal(null);
        }
        this.currentMoveGoal = null;
        this.moveGoalRefreshTicks = 0;
        this.currentFollowTarget = null;
        this.followRefreshTicks = 0;
        this.currentMineSignature = null;
        this.mineRefreshTicks = 0;
        this.currentAcquisitionSignature = signature;
        this.acquisitionFinished = false;
        this.acquisitionTicks = 0;
        this.lastAcquisitionStatus = "started(" + signature + ")";
    }

    private boolean hasArmorEquipped(ArmorRequest request) {
        for (Item item : request.items()) {
            if (!(item instanceof ArmorItem armorItem)) {
                return false;
            }
            EquipmentSlot slot = armorItem.getEquipmentSlot();
            if (!this.friend.getItemBySlot(slot).is(item)) {
                return false;
            }
        }
        return true;
    }

    private void tickAcquisitionWatchdog(PlayerEngineController highLevel) {
        if (this.currentAcquisitionSignature == null || this.acquisitionFinished) {
            return;
        }
        this.acquisitionTicks++;
        if (highLevel.getUserTaskChain().isActive()) {
            this.lastAcquisitionStatus = "running(" + this.currentAcquisitionSignature + ")";
            return;
        }
        if (this.acquisitionTicks > 40) {
            this.acquisitionFinished = true;
            this.lastAcquisitionStatus = "inactive_without_finish(" + this.currentAcquisitionSignature + ")";
        }
    }

    private String taskRunnerStatus() {
        PlayerEngineController highLevel = this.controller;
        if (highLevel == null) {
            return "runner=not_initialized";
        }
        try {
            String active = highLevel.getTaskRunner().isActive() ? "active" : "inactive";
            String chain = highLevel.getTaskRunner().getCurrentTaskChain() == null
                    ? "none"
                    : highLevel.getTaskRunner().getCurrentTaskChain().getName();
            List<?> tasks = highLevel.getTaskRunner().getCurrentTaskChain() == null
                    ? List.of()
                    : highLevel.getTaskRunner().getCurrentTaskChain().getTasks();
            String report = highLevel.getTaskRunner().statusReport;
            if (report == null || report.isBlank()) {
                report = "no_report";
            }
            report = report.length() > 80 ? report.substring(0, 80) + "..." : report;
            return "runner=" + active + ", chain=" + chain + ", chainTasks=" + tasks.size() + ", report=" + report;
        } catch (RuntimeException error) {
            return "runner=status_failed(" + error.getClass().getSimpleName() + ")";
        }
    }

    private static String resolveCatalogueName(String catalogueName) {
        for (String candidate : PlayerEngineCatalogueNames.candidates(catalogueName)) {
            if (TaskCatalogue.taskExists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static GiveRequest giveRequest(String rawTarget, int count) {
        if (rawTarget == null || rawTarget.isBlank() || count <= 0) {
            return null;
        }
        String catalogueName = resolveCatalogueName(rawTarget);
        if (catalogueName != null) {
            return new GiveRequest(
                    PlayerEngineCatalogueNames.normalize(catalogueName),
                    TaskCatalogue.getItemTarget(catalogueName, Math.max(1, count))
            );
        }
        Item item = obtainableItem(rawTarget);
        if (item == null) {
            return null;
        }
        return new GiveRequest(BuiltInRegistries.ITEM.getKey(item).toString(), new ItemTarget(item, Math.max(1, count)));
    }

    private static PickupRequest pickupRequest(String rawTarget, int count) {
        if (rawTarget == null || rawTarget.isBlank() || count <= 0) {
            return null;
        }
        String catalogueName = resolveCatalogueName(rawTarget);
        if (catalogueName != null) {
            return new PickupRequest(
                    PlayerEngineCatalogueNames.normalize(catalogueName),
                    TaskCatalogue.getItemTarget(catalogueName, Math.max(1, count))
            );
        }
        Item item = registeredItem(rawTarget);
        if (item == null) {
            return null;
        }
        return new PickupRequest(BuiltInRegistries.ITEM.getKey(item).toString(), new ItemTarget(item, Math.max(1, count)));
    }

    private static Item obtainableItem(String rawTarget) {
        Item item = registeredItem(rawTarget);
        if (item == null || !TaskCatalogue.taskExists(item)) {
            return null;
        }
        return item;
    }

    private static Item registeredItem(String rawTarget) {
        String normalized = rawTarget.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(normalized.contains(":")
                ? normalized
                : "minecraft:" + normalized);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return null;
        }
        return item;
    }

    private static PlaceBlockRequest placeBlockRequest(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return null;
        }
        String normalized = rawTarget.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return null;
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        if (isThrowawayBlockTarget(normalized)) {
            return new PlaceBlockRequest("throwaway", new Block[0], true);
        }
        ResourceLocation id = ResourceLocation.tryParse(normalized.contains(":")
                ? normalized
                : "minecraft:" + normalized);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR || block.asItem() == Items.AIR) {
            return null;
        }
        String normalizedTarget = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
        return new PlaceBlockRequest(normalizedTarget, new Block[]{block}, false);
    }

    private static boolean isThrowawayBlockTarget(String normalizedTarget) {
        return "throwaway".equals(normalizedTarget)
                || "throwaway_block".equals(normalizedTarget)
                || "route_block".equals(normalizedTarget)
                || "route_blocks".equals(normalizedTarget)
                || "bridge_block".equals(normalizedTarget)
                || "bridge_blocks".equals(normalizedTarget)
                || "scaffold".equals(normalizedTarget)
                || "scaffold_block".equals(normalizedTarget)
                || "building_materials".equals(normalizedTarget);
    }

    private static SmeltRequest smeltRequest(String rawTarget, int count) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return null;
        }
        int targetCount = Math.max(1, count);
        String normalized = rawTarget.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return null;
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return switch (normalized) {
            case "iron", "raw_iron", "iron_ingot" -> smeltRequest(
                    "iron_ingot",
                    Items.IRON_INGOT,
                    Items.RAW_IRON,
                    targetCount
            );
            case "gold", "raw_gold", "gold_ingot" -> smeltRequest(
                    "gold_ingot",
                    Items.GOLD_INGOT,
                    Items.RAW_GOLD,
                    targetCount
            );
            case "copper", "raw_copper", "copper_ingot" -> smeltRequest(
                    "copper_ingot",
                    Items.COPPER_INGOT,
                    Items.RAW_COPPER,
                    targetCount
            );
            case "charcoal" -> new SmeltRequest(
                    "charcoal",
                    new SmeltTarget(
                            new ItemTarget(Items.CHARCOAL, targetCount),
                            new ItemTarget(new Item[]{
                                    Items.OAK_LOG,
                                    Items.SPRUCE_LOG,
                                    Items.BIRCH_LOG,
                                    Items.JUNGLE_LOG,
                                    Items.ACACIA_LOG,
                                    Items.DARK_OAK_LOG,
                                    Items.MANGROVE_LOG,
                                    Items.CHERRY_LOG,
                                    Items.OAK_WOOD,
                                    Items.SPRUCE_WOOD,
                                    Items.BIRCH_WOOD,
                                    Items.JUNGLE_WOOD,
                                    Items.ACACIA_WOOD,
                                    Items.DARK_OAK_WOOD,
                                    Items.MANGROVE_WOOD,
                                    Items.CHERRY_WOOD,
                                    Items.STRIPPED_OAK_LOG,
                                    Items.STRIPPED_SPRUCE_LOG,
                                    Items.STRIPPED_BIRCH_LOG,
                                    Items.STRIPPED_JUNGLE_LOG,
                                    Items.STRIPPED_ACACIA_LOG,
                                    Items.STRIPPED_DARK_OAK_LOG,
                                    Items.STRIPPED_MANGROVE_LOG,
                                    Items.STRIPPED_CHERRY_LOG,
                                    Items.STRIPPED_OAK_WOOD,
                                    Items.STRIPPED_SPRUCE_WOOD,
                                    Items.STRIPPED_BIRCH_WOOD,
                                    Items.STRIPPED_JUNGLE_WOOD,
                                    Items.STRIPPED_ACACIA_WOOD,
                                    Items.STRIPPED_DARK_OAK_WOOD,
                                    Items.STRIPPED_MANGROVE_WOOD,
                                    Items.STRIPPED_CHERRY_WOOD
                            }, targetCount)
                    )
            );
            default -> null;
        };
    }

    private static SmeltRequest smeltRequest(String normalizedTarget, Item outputItem, Item materialItem, int targetCount) {
        return new SmeltRequest(
                normalizedTarget,
                new SmeltTarget(new ItemTarget(outputItem, targetCount), new ItemTarget(materialItem, targetCount))
        );
    }

    private static ArmorRequest armorRequest(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return null;
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return switch (normalized) {
            case "leather" -> new ArmorRequest("leather", new Item[]{
                    Items.LEATHER_HELMET,
                    Items.LEATHER_CHESTPLATE,
                    Items.LEATHER_LEGGINGS,
                    Items.LEATHER_BOOTS
            });
            case "iron" -> new ArmorRequest("iron", new Item[]{
                    Items.IRON_HELMET,
                    Items.IRON_CHESTPLATE,
                    Items.IRON_LEGGINGS,
                    Items.IRON_BOOTS
            });
            case "gold", "golden" -> new ArmorRequest("golden", new Item[]{
                    Items.GOLDEN_HELMET,
                    Items.GOLDEN_CHESTPLATE,
                    Items.GOLDEN_LEGGINGS,
                    Items.GOLDEN_BOOTS
            });
            case "diamond" -> new ArmorRequest("diamond", new Item[]{
                    Items.DIAMOND_HELMET,
                    Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_LEGGINGS,
                    Items.DIAMOND_BOOTS
            });
            case "netherite" -> new ArmorRequest("netherite", new Item[]{
                    Items.NETHERITE_HELMET,
                    Items.NETHERITE_CHESTPLATE,
                    Items.NETHERITE_LEGGINGS,
                    Items.NETHERITE_BOOTS
            });
            default -> armorItemRequest(normalized);
        };
    }

    private static ArmorRequest armorItemRequest(String normalizedTarget) {
        ResourceLocation id = ResourceLocation.tryParse(normalizedTarget.contains(":")
                ? normalizedTarget
                : "minecraft:" + normalizedTarget);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!(item instanceof ArmorItem)) {
            return null;
        }
        String normalized = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
        return new ArmorRequest(normalized, new Item[]{item});
    }

    private record ArmorRequest(String normalizedTarget, Item[] items) {
    }

    private record GiveRequest(String normalizedTarget, ItemTarget target) {
    }

    private record PickupRequest(String normalizedTarget, ItemTarget target) {
    }

    private record PlaceBlockRequest(String normalizedTarget, Block[] blocks, boolean throwaway) {
    }

    private record SmeltRequest(String normalizedTarget, SmeltTarget target) {
    }
}
