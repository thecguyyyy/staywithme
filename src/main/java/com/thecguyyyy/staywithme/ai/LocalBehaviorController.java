package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.CraftingActionAdapter;
import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.embodied.MineActionAdapter;
import com.thecguyyyy.staywithme.embodied.PlaceActionAdapter;
import com.thecguyyyy.staywithme.embodied.SmeltingActionAdapter;
import com.thecguyyyy.staywithme.ai.workflow.LongTaskWorkflow;
import com.thecguyyyy.staywithme.ai.workflow.WorkStep;
import com.thecguyyyy.staywithme.ai.workflow.WorkStepType;
import com.thecguyyyy.staywithme.ai.workflow.WorkflowFactory;
import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.ai.navigation.ConstructionPathSnapshot;
import com.thecguyyyy.staywithme.ai.navigation.ConstructionRoutePlan;
import com.thecguyyyy.staywithme.ai.navigation.LocalConstructionPathfinder;
import com.thecguyyyy.staywithme.crafting.VanillaCraftingPlanner;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.llm.ConstructionPathLlmPlanner;
import com.thecguyyyy.staywithme.llm.MiningExpeditionPlan;
import com.thecguyyyy.staywithme.memory.ExpeditionMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.perception.FriendPerception;
import com.thecguyyyy.staywithme.perception.PerceptionSnapshot;
import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import com.thecguyyyy.staywithme.survival.SurvivalWorldInteractor;
import com.thecguyyyy.staywithme.util.JsonUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Optional;
import java.util.Objects;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

public class LocalBehaviorController {
    private static final double FOLLOW_SPEED = 1.1D;
    private static final double TASK_SPEED = 1.0D;
    private static final int SEARCH_RADIUS = 16;
    private static final int WOOD_SEARCH_RADIUS = 50;
    private static final int WOOD_SEARCH_DOWN = 8;
    private static final int WOOD_SEARCH_UP = 16;
    private static final int WOOD_SEARCH_INTERVAL_TICKS = 10;
    private static final int RESOURCE_SEARCH_INTERVAL_TICKS = 10;
    private static final int RESOURCE_TARGET_REJECT_TICKS = 20 * 15;
    private static final int RESOURCE_TARGET_APPROACH_STALL_TICKS = 20 * 8;
    private static final int WORKFLOW_TIMEOUT_TICKS = 20 * 600;
    private static final int BRANCH_MAIN_SEGMENT_LENGTH = 12;
    private static final int BRANCH_SIDE_LENGTH = 5;
    private static final int TORCH_INTERVAL_STEPS = 8;
    private static final int EXPEDITION_LOW_TORCH_THRESHOLD = 2;
    private static final int EXPEDITION_RESTOCK_TORCH_TARGET = WorkflowFactory.EXPEDITION_TORCH_TARGET;
    private static final int EXPEDITION_MIN_PICKAXE_DURABILITY = 8;
    private static final int EXPEDITION_LOW_FOOD_THRESHOLD = 8;
    private static final int EXPEDITION_RESTOCK_FOOD_ITEMS = 3;
    private static final float EXPEDITION_RECOVERED_HEALTH_RATIO = 0.75F;
    private static final int EXPEDITION_RECOVERY_TIMEOUT_TICKS = 20 * 180;
    private static final int EXPEDITION_THREAT_RETREAT_TIMEOUT_TICKS = 20 * 120;
    private static final int EXPEDITION_MOVE_STALL_TICKS = 20 * 45;
    private static final double EXPEDITION_HAZARD_ROUTE_AVOIDANCE_RADIUS = 6.0D;
    private static final double EXPEDITION_RESOURCE_HIT_ROUTE_RADIUS = 24.0D;
    private static final double EXPEDITION_RESOURCE_DIRECTION_SCORE_MARGIN = 4.0D;
    private static final int EXPEDITION_RESOURCE_HIT_SCORE_AMOUNT_CAP = 16;
    private static final int EXPEDITION_KNOWN_HAZARD_CACHE_SIZE = 24;
    private static final int WOOD_EXPLORE_STEP = 28;
    private static final int WOOD_EXPLORE_ATTEMPTS_PER_TICK = 64;
    private static final int WOOD_EXPLORE_VERTICAL_DOWN = 16;
    private static final int WOOD_EXPLORE_VERTICAL_UP = 16;
    private static final int WOOD_EXPLORE_STALL_TICKS = 20 * 20;
    private static final int RESOURCE_EXPLORE_MOVE_STALL_TICKS = 20 * 20;
    private static final int RESOURCE_EXPLORE_BREADCRUMB_LIMIT = 512;
    private static final int RESOURCE_EXPLORE_SURFACE_DESCENT_BLOCKS = 8;
    private static final int CONSTRUCTION_MOVE_STALL_TICKS = 20 * 8;
    private static final Direction[] HORIZONTAL_EXPEDITION_DIRECTIONS = new Direction[]{
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };
    private static final Set<Block> EXPEDITION_RISKY_BLOCKS = Set.of(
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
            Blocks.SCULK_SHRIEKER,
            Blocks.SCULK_SENSOR,
            Blocks.CALIBRATED_SCULK_SENSOR,
            Blocks.TNT,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK
    );

    private final FriendEntity friend;
    private final EmbodiedController body;
    private final FriendInteractionProvider interaction;
    private final MineActionAdapter mineAdapter;
    private final PlaceActionAdapter placeAdapter;
    private final CraftingActionAdapter craftingAdapter;
    private final SmeltingActionAdapter smeltingAdapter;
    private final ConstructionPathLlmPlanner constructionPathLlmPlanner;
    private String pendingRestoredWorkflowId;
    private int pendingRestoredWorkflowIndex = -1;
    private int pendingRestoredWorkflowStepCount = -1;
    private String pendingRestoredTaskType;
    private String pendingRestoredTaskTarget;
    private int pendingRestoredTaskAmount = -1;
    private CompoundTag pendingRestoredTransientState;
    private int taskTicks;
    private int attackCooldownTicks;
    private int workMessageCooldownTicks;
    private BlockPos woodTarget;
    private BlockPos woodLeafTarget;
    private int woodSearchCooldownTicks;
    private BlockPos woodExploreOrigin;
    private BlockPos woodExploreTarget;
    private BlockPos woodExploreLastPos;
    private int woodExploreStallTicks;
    private int woodExploreCursor;
    private int woodExploreRotation;
    private BlockPos resourceTarget;
    private String resourceTargetKind;
    private int resourceSearchCooldownTicks;
    private BlockPos resourceRejectedTarget;
    private int resourceRejectedTargetTicks;
    private String resourceExploreKind;
    private BlockPos resourceExploreDigTarget;
    private Direction resourceExploreDirection;
    private BlockPos resourceExploreLastStepPos;
    private int resourceExploreTargetY;
    private int resourceExploreBaseSegmentLength;
    private int resourceExploreStepsRemaining;
    private int resourceExploreTurns;
    private BlockPos resourceExploreMoveWatchTarget;
    private BlockPos resourceExploreMoveWatchLastPos;
    private int resourceExploreMoveWatchTicks;
    private List<BlockPos> resourceExploreBreadcrumbs = new ArrayList<>();
    private BlockPos expeditionDigTarget;
    private Direction expeditionDirection;
    private BlockPos expeditionSupplyPoint;
    private BlockPos expeditionMineEntrance;
    private BlockPos expeditionSupplyChest;
    private boolean expeditionMineEntranceFromMemory;
    private boolean expeditionReachedRememberedMineEntrance;
    private BlockPos expeditionRouteResumeTarget;
    private BlockPos expeditionRouteResumeAnchor;
    private String expeditionRouteResumeType = "none";
    private int expeditionRouteResumeGraphDepth;
    private List<BlockPos> expeditionRouteResumeWaypoints = new ArrayList<>();
    private int expeditionRouteResumeWaypointIndex;
    private boolean expeditionRouteResumeFromMemory;
    private boolean expeditionReachedRememberedRouteTarget;
    private boolean expeditionResupplyActive;
    private boolean expeditionRecoveryActive;
    private int expeditionRecoveryTicks;
    private float expeditionRecoveryLastHealth;
    private boolean expeditionThreatRetreatActive;
    private int expeditionThreatRetreatTicks;
    private boolean expeditionLavaRerouteActive;
    private BlockPos expeditionLavaRerouteOrigin;
    private boolean expeditionTorchRestockUnavailable;
    private boolean expeditionToolRestockUnavailable;
    private boolean expeditionFoodRestockUnavailable;
    private List<BlockPos> expeditionKnownHazards = new ArrayList<>();
    private String expeditionSupplyStatus = "idle";
    private BlockPos expeditionLastBranchStepPos;
    private BlockPos expeditionMainBranchStart;
    private BlockPos expeditionSideBranchAnchor;
    private BlockPos expeditionSideBranchEnd;
    private Direction expeditionMainDirection;
    private Direction expeditionSideDirection;
    private int expeditionBranchMainSteps;
    private int expeditionSideStepsRemaining;
    private int expeditionSideBranchCount;
    private boolean expeditionReturningFromSideBranch;
    private BlockPos expeditionLastTunnelStepPos;
    private BlockPos expeditionLastTorchPos;
    private int expeditionTunnelStepsSinceTorch;
    private BlockPos expeditionMoveWatchTarget;
    private BlockPos expeditionMoveWatchLastPos;
    private String expeditionMoveWatchLabel = "none";
    private int expeditionMoveWatchTicks;
    private BlockPos placeTarget;
    private BlockPos craftingStationTarget;
    private BlockPos furnaceStationTarget;
    private CompletableFuture<Optional<ConstructionRoutePlan>> constructionPathFuture;
    private ConstructionRoutePlan constructionPathPlan;
    private BlockPos constructionPathOrigin;
    private BlockPos constructionPathTarget;
    private BlockPos constructionPathLastMovePos;
    private String constructionPathLabel = "none";
    private int constructionPathStepIndex;
    private int constructionPathMoveStallTicks;
    private LivingEntity combatTarget;
    private LongTaskWorkflow workflow;
    private static final Block[] VANILLA_COBBLESTONE_SOURCES = new Block[]{
            Blocks.STONE,
            Blocks.COBBLESTONE
    };
    private static final Block[] VANILLA_COAL_ORES = new Block[]{
            Blocks.COAL_ORE,
            Blocks.DEEPSLATE_COAL_ORE
    };
    private static final Block[] VANILLA_IRON_ORES = new Block[]{
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE
    };

    public LocalBehaviorController(FriendEntity friend, EmbodiedController body) {
        this.friend = friend;
        this.body = body;
        this.interaction = friend.getInteractionProvider();
        this.mineAdapter = new MineActionAdapter(body, this.interaction);
        this.placeAdapter = new PlaceActionAdapter(body, this.interaction);
        this.craftingAdapter = new CraftingActionAdapter(friend);
        this.smeltingAdapter = new SmeltingActionAdapter(friend);
        this.constructionPathLlmPlanner = new ConstructionPathLlmPlanner();
    }

    public void onTaskStarted(FriendTask task) {
        this.taskTicks = 0;
        this.attackCooldownTicks = 0;
        this.workMessageCooldownTicks = 0;
        this.woodTarget = null;
        this.woodLeafTarget = null;
        this.woodSearchCooldownTicks = 0;
        this.resetWoodExploreTarget();
        this.resourceTarget = null;
        this.resourceTargetKind = null;
        this.resourceSearchCooldownTicks = 0;
        this.resourceRejectedTarget = null;
        this.resourceRejectedTargetTicks = 0;
        this.resetResourceExplore();
        this.expeditionDigTarget = null;
        this.expeditionDirection = null;
        this.expeditionSupplyPoint = task.type() == FriendTaskType.MINING_EXPEDITION ? this.friend.blockPosition().immutable() : null;
        this.expeditionMineEntrance = task.type() == FriendTaskType.MINING_EXPEDITION ? this.friend.blockPosition().immutable() : null;
        this.expeditionSupplyChest = null;
        this.expeditionMineEntranceFromMemory = false;
        this.expeditionReachedRememberedMineEntrance = true;
        this.expeditionRouteResumeTarget = null;
        this.expeditionRouteResumeAnchor = null;
        this.expeditionRouteResumeType = "none";
        this.expeditionRouteResumeGraphDepth = 0;
        this.expeditionRouteResumeWaypoints = new ArrayList<>();
        this.expeditionRouteResumeWaypointIndex = 0;
        this.expeditionRouteResumeFromMemory = false;
        this.expeditionReachedRememberedRouteTarget = true;
        this.expeditionResupplyActive = false;
        this.expeditionRecoveryActive = false;
        this.expeditionRecoveryTicks = 0;
        this.expeditionRecoveryLastHealth = this.friend.getHealth();
        this.expeditionThreatRetreatActive = false;
        this.expeditionThreatRetreatTicks = 0;
        this.expeditionLavaRerouteActive = false;
        this.expeditionLavaRerouteOrigin = null;
        this.expeditionTorchRestockUnavailable = false;
        this.expeditionToolRestockUnavailable = false;
        this.expeditionFoodRestockUnavailable = false;
        this.expeditionKnownHazards = new ArrayList<>();
        this.expeditionSupplyStatus = "idle";
        this.resetExpeditionMoveWatch();
        this.resetConstructionPathRecovery();
        this.resetBranchPattern();
        this.resetTorchPattern();
        this.placeTarget = null;
        this.craftingStationTarget = null;
        this.furnaceStationTarget = null;
        this.combatTarget = null;
        this.workflow = this.createWorkflowFor(task);
        this.applyPendingWorkflowState(task);
        this.mineAdapter.reset();
        if (task.type() == FriendTaskType.MINING_EXPEDITION) {
            this.applyRememberedExpedition(task);
            this.rememberExpeditionStarted(task);
            this.rememberPortableNote(task, "expedition started target="
                    + task.target()
                    + " amount="
                    + Math.max(1, task.amount())
                    + " supply="
                    + this.formatPos(this.expeditionSupplyPoint)
                    + " entrance="
                    + this.formatPos(this.expeditionMineEntrance)
                    + " reason="
                    + task.reason());
        }
        this.applyPendingTransientState(task);
        this.clearPendingControllerState();

        switch (task.type()) {
            case FOLLOW_PLAYER -> this.friend.setFriendState(FriendState.FOLLOWING);
            case STOP -> this.friend.getFriendBrain().stopTask();
            case SAY -> {
                this.friend.setFriendState(FriendState.EXECUTING_TASK);
                this.say(task.message() == null || task.message().isBlank() ? "I'm here." : task.message());
                this.friend.getFriendBrain().completeTask();
            }
            case RETURN_TO_PLAYER -> this.friend.setFriendState(FriendState.RETURNING);
            case UNKNOWN -> {
                this.friend.setFriendState(FriendState.ERROR);
                this.say(task.message() == null || task.message().isBlank() ? "I do not understand that task yet." : task.message());
                this.friend.getFriendBrain().completeTask();
            }
            default -> this.friend.setFriendState(FriendState.EXECUTING_TASK);
        }
    }

    public void tick() {
        this.body.tick();

        FriendTask task = this.friend.getCurrentTask();
        if (task == null) {
            return;
        }

        this.taskTicks++;
        if (this.attackCooldownTicks > 0) {
            this.attackCooldownTicks--;
        }
        if (this.workMessageCooldownTicks > 0) {
            this.workMessageCooldownTicks--;
        }
        if (this.woodSearchCooldownTicks > 0) {
            this.woodSearchCooldownTicks--;
        }
        if (this.resourceSearchCooldownTicks > 0) {
            this.resourceSearchCooldownTicks--;
        }
        if (this.resourceRejectedTargetTicks > 0) {
            this.resourceRejectedTargetTicks--;
            if (this.resourceRejectedTargetTicks <= 0) {
                this.resourceRejectedTarget = null;
            }
        }

        switch (task.type()) {
            case FOLLOW_PLAYER -> this.followPlayer(task);
            case RETURN_TO_PLAYER -> this.returnToPlayer(task);
            case COLLECT_WOOD -> this.collectWood();
            case CRAFT_ITEM, MAKE_CRAFTING_TABLE, MAKE_STICKS, MAKE_CHEST, MAKE_WOODEN_AXE, MAKE_WOODEN_PICKAXE,
                 MAKE_STONE_PICKAXE, MAKE_FURNACE, MAKE_IRON_INGOT, MAKE_IRON_PICKAXE, MINE_RESOURCE,
                 MINING_EXPEDITION -> this.executeWorkflowTask(task);
            case ATTACK_NEARBY_HOSTILE -> this.attackNearbyHostile();
            case STOP -> this.friend.getFriendBrain().stopTask();
            case SAY, UNKNOWN -> {
            }
        }
    }

    public void stop() {
        this.body.stop();
        this.stopTransientTargets();
    }

    public void stopTransientTargets() {
        this.woodTarget = null;
        this.woodLeafTarget = null;
        this.woodSearchCooldownTicks = 0;
        this.resetWoodExploreTarget();
        this.resourceTarget = null;
        this.resourceTargetKind = null;
        this.resourceSearchCooldownTicks = 0;
        this.resourceRejectedTarget = null;
        this.resourceRejectedTargetTicks = 0;
        this.resetResourceExplore();
        this.expeditionDigTarget = null;
        this.expeditionDirection = null;
        this.expeditionSupplyPoint = null;
        this.expeditionMineEntrance = null;
        this.expeditionSupplyChest = null;
        this.expeditionMineEntranceFromMemory = false;
        this.expeditionReachedRememberedMineEntrance = true;
        this.expeditionRouteResumeTarget = null;
        this.expeditionRouteResumeAnchor = null;
        this.expeditionRouteResumeType = "none";
        this.expeditionRouteResumeGraphDepth = 0;
        this.expeditionRouteResumeWaypoints = new ArrayList<>();
        this.expeditionRouteResumeWaypointIndex = 0;
        this.expeditionRouteResumeFromMemory = false;
        this.expeditionReachedRememberedRouteTarget = true;
        this.expeditionResupplyActive = false;
        this.expeditionRecoveryActive = false;
        this.expeditionRecoveryTicks = 0;
        this.expeditionRecoveryLastHealth = this.friend.getHealth();
        this.expeditionThreatRetreatActive = false;
        this.expeditionThreatRetreatTicks = 0;
        this.expeditionLavaRerouteActive = false;
        this.expeditionLavaRerouteOrigin = null;
        this.expeditionTorchRestockUnavailable = false;
        this.expeditionToolRestockUnavailable = false;
        this.expeditionFoodRestockUnavailable = false;
        this.expeditionKnownHazards = new ArrayList<>();
        this.expeditionSupplyStatus = "idle";
        this.resetExpeditionMoveWatch();
        this.resetConstructionPathRecovery();
        this.resetBranchPattern();
        this.resetTorchPattern();
        this.placeTarget = null;
        this.craftingStationTarget = null;
        this.furnaceStationTarget = null;
        this.combatTarget = null;
        this.workflow = null;
        this.clearPendingControllerState();
        this.attackCooldownTicks = 0;
        this.workMessageCooldownTicks = 0;
        this.friend.setTarget(null);
        this.mineAdapter.reset();
    }

    public void saveControllerState(CompoundTag tag) {
        FriendTask task = this.friend.getCurrentTask();
        if (task != null) {
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString("Type", task.type().name());
            if (task.target() != null && !task.target().isBlank()) {
                taskTag.putString("Target", task.target());
            }
            taskTag.putInt("Amount", task.amount());
            tag.put("Task", taskTag);
        } else if (this.pendingRestoredTaskType != null) {
            CompoundTag taskTag = new CompoundTag();
            taskTag.putString("Type", this.pendingRestoredTaskType);
            if (this.pendingRestoredTaskTarget != null && !this.pendingRestoredTaskTarget.isBlank()) {
                taskTag.putString("Target", this.pendingRestoredTaskTarget);
            }
            if (this.pendingRestoredTaskAmount >= 0) {
                taskTag.putInt("Amount", this.pendingRestoredTaskAmount);
            }
            tag.put("Task", taskTag);
        }
        this.saveTransientControllerState(tag, task);
        CompoundTag workflowTag = new CompoundTag();
        if (this.workflow != null) {
            workflowTag.putString("Id", this.workflow.id());
            workflowTag.putInt("CurrentIndex", this.workflow.currentIndex());
            workflowTag.putInt("StepCount", this.workflow.stepCount());
        } else if (this.pendingRestoredWorkflowId != null) {
            workflowTag.putString("Id", this.pendingRestoredWorkflowId);
            workflowTag.putInt("CurrentIndex", this.pendingRestoredWorkflowIndex);
        } else {
            return;
        }
        tag.put("Workflow", workflowTag);
    }

    public void loadControllerState(CompoundTag tag) {
        this.clearPendingControllerState();
        if (tag == null || !tag.contains("Workflow", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag workflowTag = tag.getCompound("Workflow");
        if (!workflowTag.contains("Id", Tag.TAG_STRING) || !workflowTag.contains("CurrentIndex", Tag.TAG_INT)) {
            return;
        }
        this.pendingRestoredWorkflowId = workflowTag.getString("Id");
        this.pendingRestoredWorkflowIndex = workflowTag.getInt("CurrentIndex");
        this.pendingRestoredWorkflowStepCount = workflowTag.contains("StepCount", Tag.TAG_INT)
                ? workflowTag.getInt("StepCount")
                : -1;
        if (tag.contains("Task", Tag.TAG_COMPOUND)) {
            CompoundTag taskTag = tag.getCompound("Task");
            this.pendingRestoredTaskType = taskTag.contains("Type", Tag.TAG_STRING) ? taskTag.getString("Type") : null;
            this.pendingRestoredTaskTarget = taskTag.contains("Target", Tag.TAG_STRING) ? taskTag.getString("Target") : null;
            this.pendingRestoredTaskAmount = taskTag.contains("Amount", Tag.TAG_INT) ? taskTag.getInt("Amount") : -1;
        }
        if (tag.contains("Transient", Tag.TAG_COMPOUND)) {
            this.pendingRestoredTransientState = tag.getCompound("Transient").copy();
        }
    }

    private void saveTransientControllerState(CompoundTag tag, FriendTask task) {
        if (task == null && this.pendingRestoredTransientState != null) {
            tag.put("Transient", this.pendingRestoredTransientState.copy());
            return;
        }

        CompoundTag transientTag = new CompoundTag();
        boolean saved = false;
        saved |= this.putBlockPos(transientTag, "CraftingStation", this.craftingStationTarget);
        saved |= this.putBlockPos(transientTag, "FurnaceStation", this.furnaceStationTarget);

        boolean hasExpeditionState = task != null && task.type() == FriendTaskType.MINING_EXPEDITION
                || this.expeditionSupplyPoint != null
                || this.expeditionMineEntrance != null
                || this.expeditionSupplyChest != null
                || this.expeditionDigTarget != null;
        if (hasExpeditionState) {
            saved |= this.putBlockPos(transientTag, "ExpeditionSupplyPoint", this.expeditionSupplyPoint);
            saved |= this.putBlockPos(transientTag, "ExpeditionMineEntrance", this.expeditionMineEntrance);
            saved |= this.putBlockPos(transientTag, "ExpeditionSupplyChest", this.expeditionSupplyChest);
            saved |= this.putBlockPos(transientTag, "ExpeditionDigTarget", this.expeditionDigTarget);
            saved |= this.putBlockPos(transientTag, "ExpeditionRouteTarget", this.expeditionRouteResumeTarget);
            saved |= this.putBlockPos(transientTag, "ExpeditionRouteAnchor", this.expeditionRouteResumeAnchor);
            saved |= this.putBlockPosList(transientTag, "ExpeditionRouteWaypoints", this.expeditionRouteResumeWaypoints);
            saved |= this.putBlockPos(transientTag, "ExpeditionLastBranchStep", this.expeditionLastBranchStepPos);
            saved |= this.putBlockPos(transientTag, "ExpeditionMainBranchStart", this.expeditionMainBranchStart);
            saved |= this.putBlockPos(transientTag, "ExpeditionSideBranchAnchor", this.expeditionSideBranchAnchor);
            saved |= this.putBlockPos(transientTag, "ExpeditionSideBranchEnd", this.expeditionSideBranchEnd);
            saved |= this.putBlockPos(transientTag, "ExpeditionLastTunnelStep", this.expeditionLastTunnelStepPos);
            saved |= this.putBlockPos(transientTag, "ExpeditionLastTorch", this.expeditionLastTorchPos);
            saved |= this.putBlockPos(transientTag, "ExpeditionLavaRerouteOrigin", this.expeditionLavaRerouteOrigin);
            saved |= this.putBlockPosList(transientTag, "ExpeditionKnownHazards", this.expeditionKnownHazards);
            saved |= this.putDirection(transientTag, "ExpeditionDirection", this.expeditionDirection);
            saved |= this.putDirection(transientTag, "ExpeditionMainDirection", this.expeditionMainDirection);
            saved |= this.putDirection(transientTag, "ExpeditionSideDirection", this.expeditionSideDirection);
            transientTag.putBoolean("ExpeditionMineEntranceFromMemory", this.expeditionMineEntranceFromMemory);
            transientTag.putString("ExpeditionRouteType", this.expeditionRouteResumeType);
            transientTag.putInt("ExpeditionRouteGraphDepth", this.expeditionRouteResumeGraphDepth);
            transientTag.putInt("ExpeditionRouteWaypointIndex", this.expeditionRouteResumeWaypointIndex);
            transientTag.putBoolean("ExpeditionReachedMineEntrance", this.expeditionReachedRememberedMineEntrance);
            transientTag.putBoolean("ExpeditionRouteFromMemory", this.expeditionRouteResumeFromMemory);
            transientTag.putBoolean("ExpeditionReachedRouteTarget", this.expeditionReachedRememberedRouteTarget);
            transientTag.putBoolean("ExpeditionResupplyActive", this.expeditionResupplyActive);
            transientTag.putBoolean("ExpeditionRecoveryActive", this.expeditionRecoveryActive);
            transientTag.putInt("ExpeditionRecoveryTicks", this.expeditionRecoveryTicks);
            transientTag.putFloat("ExpeditionRecoveryLastHealth", this.expeditionRecoveryLastHealth);
            transientTag.putBoolean("ExpeditionThreatRetreatActive", this.expeditionThreatRetreatActive);
            transientTag.putInt("ExpeditionThreatRetreatTicks", this.expeditionThreatRetreatTicks);
            transientTag.putBoolean("ExpeditionLavaRerouteActive", this.expeditionLavaRerouteActive);
            transientTag.putBoolean("ExpeditionTorchRestockUnavailable", this.expeditionTorchRestockUnavailable);
            transientTag.putBoolean("ExpeditionToolRestockUnavailable", this.expeditionToolRestockUnavailable);
            transientTag.putBoolean("ExpeditionFoodRestockUnavailable", this.expeditionFoodRestockUnavailable);
            transientTag.putString("ExpeditionSupplyStatus", this.expeditionSupplyStatus);
            transientTag.putBoolean("ExpeditionReturningSide", this.expeditionReturningFromSideBranch);
            transientTag.putInt("ExpeditionMainSteps", this.expeditionBranchMainSteps);
            transientTag.putInt("ExpeditionSideStepsLeft", this.expeditionSideStepsRemaining);
            transientTag.putInt("ExpeditionSideBranchCount", this.expeditionSideBranchCount);
            transientTag.putInt("ExpeditionTorchSteps", this.expeditionTunnelStepsSinceTorch);
            saved = true;
        }

        if (saved) {
            transientTag.putString("Dimension", this.currentDimension());
            tag.put("Transient", transientTag);
        }
    }

    private boolean putBlockPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        CompoundTag posTag = new CompoundTag();
        posTag.putInt("X", pos.getX());
        posTag.putInt("Y", pos.getY());
        posTag.putInt("Z", pos.getZ());
        tag.put(key, posTag);
        return true;
    }

    private boolean putBlockPosList(CompoundTag tag, String key, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            list.add(posTag);
        }
        if (list.isEmpty()) {
            return false;
        }
        tag.put(key, list);
        return true;
    }

    private Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag posTag = tag.getCompound(key);
        if (!posTag.contains("X", Tag.TAG_INT)
                || !posTag.contains("Y", Tag.TAG_INT)
                || !posTag.contains("Z", Tag.TAG_INT)) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
    }

    private List<BlockPos> readBlockPosList(CompoundTag tag, String key) {
        List<BlockPos> positions = new ArrayList<>();
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) {
            return positions;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag posTag = list.getCompound(index);
            if (!posTag.contains("X", Tag.TAG_INT)
                    || !posTag.contains("Y", Tag.TAG_INT)
                    || !posTag.contains("Z", Tag.TAG_INT)) {
                continue;
            }
            positions.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
        }
        return positions;
    }

    private boolean putDirection(CompoundTag tag, String key, Direction direction) {
        if (direction == null || direction.getAxis().isVertical()) {
            return false;
        }
        tag.putString(key, direction.getName());
        return true;
    }

    private Optional<Direction> readDirection(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        return this.parseHorizontalDirection(tag.getString(key));
    }

    private boolean isLoaded(ServerLevel level, BlockPos pos) {
        return pos != null && level.hasChunkAt(pos);
    }

    public Optional<String> validateRecoveredTask(FriendTask task) {
        if (task == null) {
            return Optional.of("the saved task data is missing");
        }
        if (task.type() == FriendTaskType.MINE_RESOURCE || task.type() == FriendTaskType.MINING_EXPEDITION) {
            if (MiningTargetRegistry.find(task.target()).isEmpty()) {
                return Optional.of("the saved mining target is not supported anymore: " + task.target());
            }
        }
        if (task.type() == FriendTaskType.MINING_EXPEDITION) {
            Optional<String> dimensionProblem = this.validateRecoveredExpeditionDimension(task);
            if (dimensionProblem.isPresent()) {
                return dimensionProblem;
            }
        }
        if (this.isWorkflowTask(task.type()) && this.createWorkflowFor(task) == null) {
            return Optional.of("I cannot rebuild the saved workflow: " + task.summary());
        }
        return Optional.empty();
    }

    public void say(String message) {
        this.body.say(message);
    }

    public String getControllerName() {
        return this.body.name();
    }

    public String getControllerStatus() {
        return this.body.status()
                + ", " + this.interaction.status()
                + ", " + this.mineAdapter.status()
                + ", " + this.craftingAdapter.status()
                + ", " + this.smeltingAdapter.status()
                + ", station=" + this.craftingStationSummary()
                + ", furnace=" + this.furnaceStationSummary()
                + ", construction=" + this.constructionPathSummary()
                + ", resourceExplore=" + this.resourceExploreSummary()
                + ", expedition=" + this.expeditionSummary()
                + ", workflow=" + this.workflowSummary();
    }

    public String getExpeditionStatus() {
        return this.expeditionSummary() + ", workflow=" + this.workflowSummary();
    }

    private void followPlayer(FriendTask task) {
        Optional<ServerPlayer> owner = this.findTaskPlayer(task);
        if (owner.isEmpty()) {
            this.friend.getFriendBrain().failTask("I cannot find the player to follow.");
            return;
        }

        ServerPlayer player = owner.get();
        double distance = this.friend.distanceTo(player);
        if (this.friend.level() != player.level()) {
            this.friend.getFriendBrain().failTask("I cannot follow across dimensions yet.");
            return;
        }
        if (distance > 4.0D) {
            this.body.moveTo(player, FOLLOW_SPEED);
        } else if (distance < 2.25D) {
            this.body.stop();
        }
        this.friend.setFriendState(FriendState.FOLLOWING);
    }

    private void returnToPlayer(FriendTask task) {
        Optional<ServerPlayer> owner = this.findTaskPlayer(task);
        if (owner.isEmpty()) {
            this.friend.getFriendBrain().failTask("I cannot find the player to return to.");
            return;
        }

        ServerPlayer player = owner.get();
        if (this.friend.level() != player.level()) {
            this.friend.getFriendBrain().failTask("I cannot return across dimensions yet.");
            return;
        }
        if (this.friend.distanceTo(player) <= 3.0D) {
            this.friend.getFriendBrain().completeTask();
            return;
        }
        this.body.moveTo(player, FOLLOW_SPEED);
        this.friend.setFriendState(FriendState.RETURNING);
    }

    private void collectWood() {
        if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        this.friend.getPerception().currentOrRefresh();

        if (this.collectOneLogIntoInventory(serverLevel)) {
            this.friend.getFriendBrain().completeTask();
        }
    }

    private void executeWorkflowTask(FriendTask task) {
        if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        PerceptionSnapshot snapshot = this.friend.getPerception().currentOrRefresh();
        if ((task.type() == FriendTaskType.MINE_RESOURCE || task.type() == FriendTaskType.MINING_EXPEDITION)
                && this.handleMiningExpeditionSafety(serverLevel, task, snapshot)) {
            return;
        }
        if (task.type() == FriendTaskType.MINING_EXPEDITION
                && this.collectSupplyFurnaceOutputIfNearby(serverLevel, task)) {
            return;
        }
        this.ensureWorkflow(task);

        if (this.taskTicks > WORKFLOW_TIMEOUT_TICKS) {
            this.friend.getFriendBrain().failTask("I could not finish the workflow in time.");
            return;
        }

        this.executeWorkflow(serverLevel, task);
    }

    private boolean handleMiningExpeditionSafety(ServerLevel serverLevel, FriendTask task, PerceptionSnapshot snapshot) {
        if (this.isTaskTargetSatisfied(task)) {
            return false;
        }
        if (task.type() == FriendTaskType.MINING_EXPEDITION && this.eatCarriedFoodIfNeeded(task)) {
            return true;
        }
        if (task.type() == FriendTaskType.MINING_EXPEDITION
                && this.expeditionRecoveryActive
                && this.hasRecoveredFromExpeditionLowHealth()) {
            this.expeditionRecoveryActive = false;
            this.expeditionRecoveryTicks = 0;
            this.expeditionRecoveryLastHealth = this.friend.getHealth();
            this.setExpeditionSupplyStatus("recovered");
            this.sayThrottled("Recovered enough to continue the expedition.");
            return false;
        }
        boolean hostileThreat = this.isHostileExpeditionThreat(snapshot);
        if (task.type() == FriendTaskType.MINING_EXPEDITION
                && this.expeditionThreatRetreatActive
                && !hostileThreat) {
            this.expeditionThreatRetreatActive = false;
            this.expeditionThreatRetreatTicks = 0;
            this.setExpeditionSupplyStatus("threat cleared");
            this.sayThrottled("The nearby hostile threat cleared. Continuing the expedition.");
            return false;
        }

        String reason = null;
        boolean inventoryResupply = false;
        boolean healthRecovery = false;
        boolean threatRetreat = false;
        boolean lavaReroute = false;
        boolean activeResupply = task.type() == FriendTaskType.MINING_EXPEDITION && this.expeditionResupplyActive;
        boolean activeRecovery = task.type() == FriendTaskType.MINING_EXPEDITION && this.expeditionRecoveryActive;
        boolean activeThreatRetreat = task.type() == FriendTaskType.MINING_EXPEDITION && this.expeditionThreatRetreatActive;
        boolean activeLavaReroute = task.type() == FriendTaskType.MINING_EXPEDITION && this.expeditionLavaRerouteActive;
        if (this.isExpeditionLowHealth()) {
            reason = "low health";
            healthRecovery = task.type() == FriendTaskType.MINING_EXPEDITION;
        } else if (this.emptyInventorySlots() <= 1) {
            reason = "inventory is nearly full";
            inventoryResupply = true;
        } else if (this.hasImmediateLavaHazard(serverLevel, this.friend.blockPosition())) {
            reason = "nearby lava hazard";
            lavaReroute = task.type() == FriendTaskType.MINING_EXPEDITION;
        } else if (hostileThreat) {
            reason = "nearby hostile threat";
            threatRetreat = task.type() == FriendTaskType.MINING_EXPEDITION;
        } else if (this.needsExpeditionToolRestock(task)) {
            reason = "low pickaxe durability";
            inventoryResupply = true;
        } else if (this.needsExpeditionTorchRestock(task)) {
            reason = "low torches";
            inventoryResupply = true;
        } else if (this.needsExpeditionFoodRestock(task)) {
            reason = "low food";
            inventoryResupply = true;
        }

        if (reason == null && !activeResupply && !activeRecovery && !activeThreatRetreat && !activeLavaReroute) {
            return false;
        }
        if (reason == null) {
            if (activeRecovery) {
                reason = "health recovery";
                healthRecovery = true;
            } else if (activeThreatRetreat) {
                reason = "hostile threat retreat";
                threatRetreat = true;
            } else if (activeLavaReroute) {
                reason = "lava hazard reroute";
                lavaReroute = true;
            } else {
                reason = "active supply processing";
                inventoryResupply = true;
            }
        }
        if (healthRecovery && task.type() == FriendTaskType.MINING_EXPEDITION) {
            if (!this.expeditionRecoveryActive) {
                this.expeditionRecoveryTicks = 0;
                this.expeditionRecoveryLastHealth = this.friend.getHealth();
            }
            this.expeditionRecoveryActive = true;
            this.setExpeditionSupplyStatus("returning: " + reason);
        }
        if (threatRetreat && task.type() == FriendTaskType.MINING_EXPEDITION) {
            if (!this.expeditionThreatRetreatActive) {
                this.expeditionThreatRetreatTicks = 0;
            }
            this.expeditionThreatRetreatActive = true;
            this.setExpeditionSupplyStatus("returning: " + reason);
        }
        if (lavaReroute && task.type() == FriendTaskType.MINING_EXPEDITION) {
            if (!this.expeditionLavaRerouteActive) {
                this.expeditionLavaRerouteOrigin = this.friend.blockPosition().immutable();
            }
            this.expeditionLavaRerouteActive = true;
            this.setExpeditionSupplyStatus("returning: " + reason);
        }
        if (inventoryResupply && task.type() == FriendTaskType.MINING_EXPEDITION) {
            this.expeditionResupplyActive = true;
            this.setExpeditionSupplyStatus("returning: " + reason);
        }

        Optional<ServerPlayer> owner = this.findTaskPlayer(task);
        if (owner.isEmpty() || owner.get().level() != serverLevel) {
            this.setExpeditionSupplyStatus("blocked: owner unavailable");
            this.friend.getFriendBrain().failTask("I paused mining because of " + reason + ", and I cannot find you to return.");
            return true;
        }

        ServerPlayer player = owner.get();
        BlockPos safeReturnTarget = this.expeditionReturnTarget(task, player);
        this.friend.setFriendState(FriendState.RETURNING);
        this.sayThrottled("Returning for safety/resupply: " + reason + ".");
        if (this.friend.blockPosition().distSqr(safeReturnTarget) > 9.0D) {
            if (this.isExpeditionMoveStalled(safeReturnTarget, "returning")) {
                this.body.stop();
                this.rememberExpeditionInterrupted(task, "paused", "return path stalled while " + reason);
                this.setExpeditionSupplyStatus("paused: return path stalled");
                this.friend.getFriendBrain().failTask("I could not reach the expedition return point for " + reason + ".");
                return true;
            }
            if (inventoryResupply && task.type() == FriendTaskType.MINING_EXPEDITION) {
                this.setExpeditionSupplyStatus("returning: " + reason);
            }
            this.body.moveTo(safeReturnTarget, TASK_SPEED);
            return true;
        }

        this.body.stop();
        this.resetExpeditionMoveWatch();
        if (healthRecovery && task.type() == FriendTaskType.MINING_EXPEDITION) {
            return this.handleExpeditionHealthRecovery(serverLevel, task);
        }
        if (threatRetreat && task.type() == FriendTaskType.MINING_EXPEDITION) {
            return this.handleExpeditionThreatRetreat(task, snapshot);
        }
        if (lavaReroute && task.type() == FriendTaskType.MINING_EXPEDITION) {
            return this.handleExpeditionLavaReroute(serverLevel, task);
        }
        if (inventoryResupply && task.type() == FriendTaskType.MINING_EXPEDITION) {
            this.setExpeditionSupplyStatus("processing supply station");
            ResupplyResult result = this.resupplyAtSupplyChest(serverLevel, task);
            if (result == ResupplyResult.WORKING) {
                this.expeditionResupplyActive = true;
                return true;
            }
            if (result == ResupplyResult.COMPLETE) {
                this.expeditionResupplyActive = false;
                this.setExpeditionSupplyStatus("complete");
                this.say("I handled supply storage and will continue the expedition.");
                return true;
            }
            this.expeditionResupplyActive = false;
            this.setExpeditionSupplyStatus("blocked: supply station failed");
            this.rememberExpeditionInterrupted(task, "blocked", "could not use the supply station");
            this.friend.getFriendBrain().failTask("I returned for resupply, but I could not use the supply station.");
            return true;
        }

        this.rememberExpeditionInterrupted(task, "paused", reason);
        this.setExpeditionSupplyStatus("paused: " + reason);
        this.say("I returned to the supply point for safety/resupply: " + reason + ".");
        this.friend.getFriendBrain().completeTask();
        return true;
    }

    private boolean handleExpeditionHealthRecovery(ServerLevel level, FriendTask task) {
        if (this.hasRecoveredFromExpeditionLowHealth()) {
            this.expeditionRecoveryActive = false;
            this.expeditionRecoveryTicks = 0;
            this.expeditionRecoveryLastHealth = this.friend.getHealth();
            this.setExpeditionSupplyStatus("recovered");
            this.say("Recovered enough to continue the expedition.");
            return true;
        }

        this.expeditionRecoveryActive = true;
        if (this.friend.getHungerProvider().getFoodLevel() < 18 && this.eatCarriedFoodIfBelow(task, 17)) {
            this.expeditionRecoveryTicks = 0;
            this.expeditionRecoveryLastHealth = this.friend.getHealth();
            return true;
        }

        if (this.friend.getHungerProvider().getFoodLevel() <= EXPEDITION_LOW_FOOD_THRESHOLD
                && !this.expeditionFoodRestockUnavailable) {
            this.setExpeditionSupplyStatus("processing recovery supplies");
            ResupplyResult result = this.resupplyAtSupplyChest(level, task);
            if (result == ResupplyResult.WORKING || result == ResupplyResult.COMPLETE) {
                return true;
            }
        }

        if (level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                && this.friend.getHungerProvider().getFoodLevel() >= 18) {
            if (this.friend.getHealth() > this.expeditionRecoveryLastHealth + 0.01F) {
                this.expeditionRecoveryTicks = 0;
                this.expeditionRecoveryLastHealth = this.friend.getHealth();
            } else {
                this.expeditionRecoveryTicks++;
            }
            if (this.expeditionRecoveryTicks >= EXPEDITION_RECOVERY_TIMEOUT_TICKS) {
                this.expeditionRecoveryActive = false;
                this.expeditionRecoveryTicks = 0;
                this.rememberExpeditionInterrupted(task, "paused", "health recovery made no progress");
                this.setExpeditionSupplyStatus("paused: health recovery stalled");
                this.say("I waited at the supply point, but my health is not recovering enough.");
                this.friend.getFriendBrain().completeTask();
                return true;
            }
            this.setExpeditionSupplyStatus("recovering health");
            this.sayThrottled("Recovering at the supply point before I continue.");
            return true;
        }

        this.expeditionRecoveryActive = false;
        this.expeditionRecoveryTicks = 0;
        this.rememberExpeditionInterrupted(task, "paused", "low health recovery unavailable");
        this.setExpeditionSupplyStatus("paused: low health recovery unavailable");
        this.say("I returned to the supply point, but I cannot recover enough health right now.");
        this.friend.getFriendBrain().completeTask();
        return true;
    }

    private boolean handleExpeditionThreatRetreat(FriendTask task, PerceptionSnapshot snapshot) {
        if (!this.isHostileExpeditionThreat(snapshot)) {
            this.expeditionThreatRetreatActive = false;
            this.expeditionThreatRetreatTicks = 0;
            this.setExpeditionSupplyStatus("threat cleared");
            this.say("The nearby hostile threat cleared. Continuing the expedition.");
            return true;
        }

        this.expeditionThreatRetreatActive = true;
        this.expeditionThreatRetreatTicks++;
        if (this.expeditionThreatRetreatTicks >= EXPEDITION_THREAT_RETREAT_TIMEOUT_TICKS) {
            this.expeditionThreatRetreatActive = false;
            this.expeditionThreatRetreatTicks = 0;
            this.rememberExpeditionInterrupted(task, "paused", "hostile threat did not clear");
            this.setExpeditionSupplyStatus("paused: hostile threat did not clear");
            this.say("I waited at the supply point, but the nearby hostile threat did not clear.");
            this.friend.getFriendBrain().completeTask();
            return true;
        }
        this.setExpeditionSupplyStatus("waiting: nearby hostile threat");
        this.sayThrottled("Waiting at the supply point until the nearby hostile threat clears.");
        return true;
    }

    private boolean handleExpeditionLavaReroute(ServerLevel level, FriendTask task) {
        if (this.hasImmediateLavaHazard(level, this.friend.blockPosition())) {
            this.expeditionLavaRerouteActive = false;
            this.expeditionLavaRerouteOrigin = null;
            this.rememberExpeditionInterrupted(task, "paused", "supply return point is still near lava");
            this.setExpeditionSupplyStatus("paused: supply point near lava");
            this.say("I returned for the lava hazard, but the return point is still unsafe.");
            this.friend.getFriendBrain().completeTask();
            return true;
        }

        BlockPos hazardOrigin = this.expeditionLavaRerouteOrigin == null
                ? this.friend.blockPosition().immutable()
                : this.expeditionLavaRerouteOrigin;
        this.expeditionLavaRerouteActive = false;
        this.expeditionLavaRerouteOrigin = null;
        this.clearRememberedBranchRouteResume();
        this.rerouteExpeditionAfterLava(hazardOrigin);
        this.setExpeditionSupplyStatus("rerouted: nearby lava hazard");
        this.say("I moved away from the lava hazard and will try a different tunnel direction.");
        return true;
    }

    private void rerouteExpeditionAfterLava(BlockPos hazardOrigin) {
        boolean branchStep = this.workflow != null
                && this.workflow.currentStep()
                .map(step -> step.type() == WorkStepType.BRANCH_MINE_RESOURCE)
                .orElse(false);
        if (branchStep || this.expeditionMainBranchStart != null || this.expeditionSideBranchAnchor != null) {
            this.rotateBranchDirection(hazardOrigin, "lava hazard");
        } else {
            this.rotateExpeditionDirection();
            this.rememberExpeditionBranchNote("rotated expedition direction to "
                    + this.expeditionDirection().getName()
                    + " after lava near "
                    + this.formatPos(hazardOrigin));
        }
        this.expeditionDigTarget = null;
        this.rememberExpeditionHazardAvoided("lava", hazardOrigin, "nearby lava hazard");
    }

    private boolean isExpeditionLowHealth() {
        return this.friend.getHealth() <= Math.max(4.0F, this.friend.getMaxHealth() * 0.35F);
    }

    private boolean hasRecoveredFromExpeditionLowHealth() {
        return this.friend.getHealth() >= Math.max(6.0F, this.friend.getMaxHealth() * EXPEDITION_RECOVERED_HEALTH_RATIO);
    }

    private boolean isHostileExpeditionThreat(PerceptionSnapshot snapshot) {
        return snapshot.nearbyHostileCount() >= 3
                || (snapshot.nearestHostileDistance() >= 0.0D && snapshot.nearestHostileDistance() <= 5.0D);
    }

    private BlockPos expeditionReturnTarget(FriendTask task, ServerPlayer player) {
        if (task.type() == FriendTaskType.MINING_EXPEDITION && this.expeditionSupplyPoint != null) {
            return this.expeditionSupplyPoint;
        }
        return player.blockPosition();
    }

    private boolean isTaskTargetSatisfied(FriendTask task) {
        if (task == null || task.target() == null || task.target().isBlank()) {
            return false;
        }
        if (task.type() == FriendTaskType.MINE_RESOURCE || task.type() == FriendTaskType.MINING_EXPEDITION) {
            Optional<MiningTargetRegistry.MiningTarget> target = MiningTargetRegistry.find(task.target());
            return target.isPresent()
                    && this.friend.countInventoryItems(target.get().inventoryMatcher()) >= Math.max(1, task.amount());
        }
        return false;
    }

    private boolean needsExpeditionTorchRestock(FriendTask task) {
        if (this.countTorches() > EXPEDITION_LOW_TORCH_THRESHOLD) {
            this.expeditionTorchRestockUnavailable = false;
            return false;
        }
        return task != null
                && task.type() == FriendTaskType.MINING_EXPEDITION
                && this.isActiveExpeditionMiningStep()
                && !this.expeditionTorchRestockUnavailable
                && this.expeditionSupplyPoint != null;
    }

    private boolean needsExpeditionToolRestock(FriendTask task) {
        if (this.hasUsableExpeditionPickaxe(task)) {
            this.expeditionToolRestockUnavailable = false;
            return false;
        }
        return task != null
                && task.type() == FriendTaskType.MINING_EXPEDITION
                && this.isActiveExpeditionMiningStep()
                && !this.expeditionToolRestockUnavailable
                && this.expeditionSupplyPoint != null;
    }

    private boolean needsExpeditionFoodRestock(FriendTask task) {
        if (this.friend.getHungerProvider().getFoodLevel() > EXPEDITION_LOW_FOOD_THRESHOLD
                || this.carriedFoodItems() > 0) {
            this.expeditionFoodRestockUnavailable = false;
            return false;
        }
        return task != null
                && task.type() == FriendTaskType.MINING_EXPEDITION
                && this.isActiveExpeditionMiningStep()
                && !this.expeditionFoodRestockUnavailable
                && this.expeditionSupplyPoint != null;
    }

    private boolean isActiveExpeditionMiningStep() {
        return this.workflow != null
                && this.workflow.currentStep()
                .map(step -> step.type() == WorkStepType.DESCEND_TO_LAYER
                        || step.type() == WorkStepType.BRANCH_MINE_RESOURCE)
                .orElse(false);
    }

    private LongTaskWorkflow createWorkflowFor(FriendTask task) {
        if (task.type() == FriendTaskType.CRAFT_ITEM) {
            if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            return this.parseItemId(task.target())
                    .flatMap(target -> VanillaCraftingPlanner.plan(serverLevel, target, Math.max(1, task.amount())))
                    .orElse(null);
        }
        if (task.type() == FriendTaskType.MINE_RESOURCE) {
            return MiningTargetRegistry.find(task.target())
                    .map(target -> this.createMineResourceWorkflow(target, Math.max(1, task.amount())))
                    .orElse(null);
        }
        if (task.type() == FriendTaskType.MINING_EXPEDITION) {
            return MiningTargetRegistry.find(task.target())
                    .map(target -> this.createMiningExpeditionWorkflow(task, target, Math.max(1, task.amount())))
                    .orElse(null);
        }

        return switch (task.type()) {
            case MAKE_CRAFTING_TABLE -> WorkflowFactory.craftingTable();
            case MAKE_STICKS -> WorkflowFactory.sticks();
            case MAKE_CHEST -> WorkflowFactory.chest();
            case MAKE_WOODEN_AXE -> WorkflowFactory.woodenAxe();
            case MAKE_WOODEN_PICKAXE -> WorkflowFactory.woodenPickaxe();
            case MAKE_STONE_PICKAXE -> WorkflowFactory.stonePickaxe();
            case MAKE_FURNACE -> WorkflowFactory.furnace();
            case MAKE_IRON_INGOT -> WorkflowFactory.ironIngot();
            case MAKE_IRON_PICKAXE -> WorkflowFactory.ironPickaxe();
            default -> null;
        };
    }

    private LongTaskWorkflow createMineResourceWorkflow(MiningTargetRegistry.MiningTarget target, int amount) {
        if (this.hasToolForAnySource(target.sourceBlocks())) {
            return WorkflowFactory.mineDirect(target.resourceId(), amount);
        }
        return switch (target.toolRequirement()) {
            case NONE -> WorkflowFactory.mineDirect(target.resourceId(), amount);
            case WOODEN_PICKAXE -> WorkflowFactory.mineWithWoodenPickaxe(target.resourceId(), amount);
            case STONE_PICKAXE -> WorkflowFactory.mineWithStonePickaxe(target.resourceId(), amount);
            case IRON_PICKAXE -> WorkflowFactory.mineWithIronPickaxe(target.resourceId(), amount);
        };
    }

    private LongTaskWorkflow createMiningExpeditionWorkflow(FriendTask task, MiningTargetRegistry.MiningTarget target, int amount) {
        MiningExpeditionPlan plan = this.parseMiningExpeditionPlan(task)
                .orElseGet(() -> MiningExpeditionPlan.fallback(target.resourceId(), amount, "Missing or invalid expedition plan JSON."));
        String layerTarget = plan.targetDimension + "@" + plan.preferredYMin + ".." + plan.preferredYMax;
        if (this.hasToolForAnySource(target.sourceBlocks())) {
            return WorkflowFactory.miningExpeditionDirect(target.resourceId(), amount, layerTarget);
        }
        return switch (target.toolRequirement()) {
            case NONE -> WorkflowFactory.miningExpeditionDirect(target.resourceId(), amount, layerTarget);
            case WOODEN_PICKAXE -> WorkflowFactory.miningExpeditionWithWoodenPickaxe(target.resourceId(), amount, layerTarget);
            case STONE_PICKAXE -> WorkflowFactory.miningExpeditionWithStonePickaxe(target.resourceId(), amount, layerTarget);
            case IRON_PICKAXE -> WorkflowFactory.miningExpeditionWithIronPickaxe(target.resourceId(), amount, layerTarget);
        };
    }

    private Optional<String> validateRecoveredExpeditionDimension(FriendTask task) {
        if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
            return Optional.of("the server world is not available yet");
        }
        MiningExpeditionPlan plan = this.parseMiningExpeditionPlan(task)
                .orElseGet(() -> MiningExpeditionPlan.fallback(task.target(), Math.max(1, task.amount()), "Recovered task validation fallback."));
        String targetDimension = plan.targetDimension == null || plan.targetDimension.isBlank()
                ? "minecraft:overworld"
                : plan.targetDimension;
        String currentDimension = serverLevel.dimension().location().toString();
        if (!targetDimension.equals(currentDimension)) {
            return Optional.of("the saved expedition is for " + targetDimension + ", but I am in " + currentDimension);
        }
        return Optional.empty();
    }

    private boolean isWorkflowTask(FriendTaskType type) {
        return switch (type) {
            case CRAFT_ITEM, MAKE_CRAFTING_TABLE, MAKE_STICKS, MAKE_CHEST, MAKE_WOODEN_AXE, MAKE_WOODEN_PICKAXE,
                 MAKE_STONE_PICKAXE, MAKE_FURNACE, MAKE_IRON_INGOT, MAKE_IRON_PICKAXE, MINE_RESOURCE,
                 MINING_EXPEDITION -> true;
            default -> false;
        };
    }

    private void ensureWorkflow(FriendTask task) {
        LongTaskWorkflow expected = this.createWorkflowFor(task);
        if (expected == null) {
            return;
        }
        if (this.workflow == null || !expected.id().equals(this.workflow.id())) {
            this.workflow = expected;
        }
    }

    private void applyPendingWorkflowState(FriendTask task) {
        if (this.pendingRestoredWorkflowId == null) {
            return;
        }
        if (this.workflow != null
                && this.pendingRestoredWorkflowId.equals(this.workflow.id())
                && (this.pendingRestoredWorkflowStepCount < 0
                || this.pendingRestoredWorkflowStepCount == this.workflow.stepCount())
                && this.pendingControllerTaskMatches(task)) {
            this.workflow.restoreCurrentIndex(this.pendingRestoredWorkflowIndex);
        }
    }

    private void applyPendingTransientState(FriendTask task) {
        if (this.pendingRestoredTransientState == null || !this.pendingControllerTaskMatches(task)) {
            return;
        }
        if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        String savedDimension = this.pendingRestoredTransientState.contains("Dimension", Tag.TAG_STRING)
                ? this.pendingRestoredTransientState.getString("Dimension")
                : "";
        if (!savedDimension.isBlank() && !savedDimension.equals(serverLevel.dimension().location().toString())) {
            return;
        }

        this.readBlockPos(this.pendingRestoredTransientState, "CraftingStation")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.isCraftingTableAt(serverLevel, pos))
                .ifPresent(pos -> this.craftingStationTarget = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "FurnaceStation")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.isFurnaceAt(serverLevel, pos))
                .ifPresent(pos -> this.furnaceStationTarget = pos);
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION) {
            return;
        }

        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionSupplyPoint")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.canStandAt(serverLevel, pos))
                .ifPresent(pos -> this.expeditionSupplyPoint = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionMineEntrance")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.canStandAt(serverLevel, pos))
                .ifPresent(pos -> this.expeditionMineEntrance = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionSupplyChest")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.isChestAt(serverLevel, pos))
                .ifPresent(pos -> this.expeditionSupplyChest = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionDigTarget")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionDigTarget = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionRouteTarget")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.canStandAt(serverLevel, pos))
                .ifPresent(pos -> this.expeditionRouteResumeTarget = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionRouteAnchor")
                .filter(pos -> this.isLoaded(serverLevel, pos) && this.canStandAt(serverLevel, pos))
                .ifPresent(pos -> this.expeditionRouteResumeAnchor = pos);
        this.expeditionRouteResumeWaypoints = new ArrayList<>();
        for (BlockPos waypoint : this.readBlockPosList(this.pendingRestoredTransientState, "ExpeditionRouteWaypoints")) {
            if (this.isLoaded(serverLevel, waypoint) && this.canStandAt(serverLevel, waypoint)) {
                this.expeditionRouteResumeWaypoints.add(waypoint);
            }
        }
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionLastBranchStep")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionLastBranchStepPos = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionMainBranchStart")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionMainBranchStart = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionSideBranchAnchor")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionSideBranchAnchor = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionSideBranchEnd")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionSideBranchEnd = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionLastTunnelStep")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionLastTunnelStepPos = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionLastTorch")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionLastTorchPos = pos);
        this.readBlockPos(this.pendingRestoredTransientState, "ExpeditionLavaRerouteOrigin")
                .filter(pos -> this.isLoaded(serverLevel, pos))
                .ifPresent(pos -> this.expeditionLavaRerouteOrigin = pos);
        if (this.pendingRestoredTransientState.contains("ExpeditionKnownHazards", Tag.TAG_LIST)) {
            this.expeditionKnownHazards = new ArrayList<>();
            for (BlockPos hazard : this.readBlockPosList(this.pendingRestoredTransientState, "ExpeditionKnownHazards")) {
                if (this.isLoaded(serverLevel, hazard)) {
                    this.addKnownExpeditionHazard(hazard);
                }
            }
        }

        this.readDirection(this.pendingRestoredTransientState, "ExpeditionDirection")
                .ifPresent(direction -> this.expeditionDirection = direction);
        this.readDirection(this.pendingRestoredTransientState, "ExpeditionMainDirection")
                .ifPresent(direction -> this.expeditionMainDirection = direction);
        this.readDirection(this.pendingRestoredTransientState, "ExpeditionSideDirection")
                .ifPresent(direction -> this.expeditionSideDirection = direction);

        if (this.pendingRestoredTransientState.contains("ExpeditionMineEntranceFromMemory")) {
            this.expeditionMineEntranceFromMemory = this.pendingRestoredTransientState.getBoolean("ExpeditionMineEntranceFromMemory");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRouteType", Tag.TAG_STRING)) {
            this.expeditionRouteResumeType = this.normalizeRouteType(this.pendingRestoredTransientState.getString("ExpeditionRouteType"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRouteGraphDepth", Tag.TAG_INT)) {
            this.expeditionRouteResumeGraphDepth = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionRouteGraphDepth"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRouteWaypointIndex", Tag.TAG_INT)) {
            this.expeditionRouteResumeWaypointIndex = Math.max(
                    0,
                    Math.min(
                            this.pendingRestoredTransientState.getInt("ExpeditionRouteWaypointIndex"),
                            this.expeditionRouteResumeWaypoints.size()
                    )
            );
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionReachedMineEntrance")) {
            this.expeditionReachedRememberedMineEntrance = this.pendingRestoredTransientState.getBoolean("ExpeditionReachedMineEntrance");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRouteFromMemory")) {
            this.expeditionRouteResumeFromMemory = this.pendingRestoredTransientState.getBoolean("ExpeditionRouteFromMemory");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionReachedRouteTarget")) {
            this.expeditionReachedRememberedRouteTarget = this.pendingRestoredTransientState.getBoolean("ExpeditionReachedRouteTarget");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionResupplyActive")) {
            this.expeditionResupplyActive = this.pendingRestoredTransientState.getBoolean("ExpeditionResupplyActive");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRecoveryActive")) {
            this.expeditionRecoveryActive = this.pendingRestoredTransientState.getBoolean("ExpeditionRecoveryActive");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRecoveryTicks", Tag.TAG_INT)) {
            this.expeditionRecoveryTicks = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionRecoveryTicks"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionRecoveryLastHealth", Tag.TAG_FLOAT)) {
            this.expeditionRecoveryLastHealth = Math.max(0.0F, this.pendingRestoredTransientState.getFloat("ExpeditionRecoveryLastHealth"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionThreatRetreatActive")) {
            this.expeditionThreatRetreatActive = this.pendingRestoredTransientState.getBoolean("ExpeditionThreatRetreatActive");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionThreatRetreatTicks", Tag.TAG_INT)) {
            this.expeditionThreatRetreatTicks = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionThreatRetreatTicks"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionLavaRerouteActive")) {
            this.expeditionLavaRerouteActive = this.pendingRestoredTransientState.getBoolean("ExpeditionLavaRerouteActive");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionTorchRestockUnavailable")) {
            this.expeditionTorchRestockUnavailable = this.pendingRestoredTransientState.getBoolean("ExpeditionTorchRestockUnavailable");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionToolRestockUnavailable")) {
            this.expeditionToolRestockUnavailable = this.pendingRestoredTransientState.getBoolean("ExpeditionToolRestockUnavailable");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionFoodRestockUnavailable")) {
            this.expeditionFoodRestockUnavailable = this.pendingRestoredTransientState.getBoolean("ExpeditionFoodRestockUnavailable");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionSupplyStatus", Tag.TAG_STRING)) {
            this.setExpeditionSupplyStatus(this.pendingRestoredTransientState.getString("ExpeditionSupplyStatus"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionReturningSide")) {
            this.expeditionReturningFromSideBranch = this.pendingRestoredTransientState.getBoolean("ExpeditionReturningSide");
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionMainSteps", Tag.TAG_INT)) {
            this.expeditionBranchMainSteps = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionMainSteps"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionSideStepsLeft", Tag.TAG_INT)) {
            this.expeditionSideStepsRemaining = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionSideStepsLeft"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionSideBranchCount", Tag.TAG_INT)) {
            this.expeditionSideBranchCount = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionSideBranchCount"));
        }
        if (this.pendingRestoredTransientState.contains("ExpeditionTorchSteps", Tag.TAG_INT)) {
            this.expeditionTunnelStepsSinceTorch = Math.max(0, this.pendingRestoredTransientState.getInt("ExpeditionTorchSteps"));
        }
    }

    private boolean pendingControllerTaskMatches(FriendTask task) {
        if (task == null) {
            return false;
        }
        if (this.pendingRestoredTaskType != null && !this.pendingRestoredTaskType.equals(task.type().name())) {
            return false;
        }
        String currentTarget = task.target() == null || task.target().isBlank() ? null : task.target();
        if (this.pendingRestoredTaskTarget == null) {
            if (currentTarget != null) {
                return false;
            }
        } else if (!this.pendingRestoredTaskTarget.equals(currentTarget)) {
            return false;
        }
        return this.pendingRestoredTaskAmount < 0 || this.pendingRestoredTaskAmount == task.amount();
    }

    private void clearPendingControllerState() {
        this.pendingRestoredWorkflowId = null;
        this.pendingRestoredWorkflowIndex = -1;
        this.pendingRestoredWorkflowStepCount = -1;
        this.pendingRestoredTaskType = null;
        this.pendingRestoredTaskTarget = null;
        this.pendingRestoredTaskAmount = -1;
        this.pendingRestoredTransientState = null;
    }

    private String normalizeRouteType(String type) {
        if (type == null) {
            return "none";
        }
        String normalized = type.trim().toLowerCase();
        return "main".equals(normalized) || "side".equals(normalized) ? normalized : "none";
    }

    private void executeWorkflow(ServerLevel serverLevel, FriendTask task) {
        if (this.workflow == null) {
            this.ensureWorkflow(task);
        }
        if (this.workflow == null) {
            this.friend.getFriendBrain().failTask("No workflow is available for " + task.type().name() + ".");
            return;
        }
        if (this.workflowOutputSatisfied()) {
            if (this.returnBeforeCompletingExpedition(serverLevel, task, "target satisfied")) {
                return;
            }
            this.rememberExpeditionCompleted(task, "target satisfied");
            this.friend.getFriendBrain().completeTask();
            return;
        }

        for (int guard = 0; guard < 4; guard++) {
            Optional<WorkStep> current = this.workflow.currentStep();
            if (current.isEmpty()) {
                if (this.returnBeforeCompletingExpedition(serverLevel, task, "workflow complete")) {
                    return;
                }
                this.rememberExpeditionCompleted(task, "workflow complete");
                this.friend.getFriendBrain().completeTask();
                return;
            }

            WorkStep step = current.get();
            if (this.completeCurrentInventoryStepIfSatisfied(step)) {
                continue;
            }
            boolean advanced = switch (step.type()) {
                case ACQUIRE_ITEM -> this.executeAcquireItemStep(serverLevel, step);
                case CRAFT_ITEM -> this.executeCraftItemStep(serverLevel, step);
                case PLACE_BLOCK -> this.executePlaceBlockStep(serverLevel, task, step);
                case SMELT_ITEM -> this.executeSmeltItemStep(serverLevel, step);
                case DESCEND_TO_LAYER -> this.executeDescendToLayerStep(serverLevel, step);
                case BRANCH_MINE_RESOURCE -> this.executeBranchMineResourceStep(serverLevel, step);
            };
            if (!advanced) {
                return;
            }
        }
    }

    private boolean returnBeforeCompletingExpedition(ServerLevel level, FriendTask task, String reason) {
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION) {
            return false;
        }
        Optional<BlockPos> returnTarget = this.expeditionCompletionReturnTarget(level, task);
        if (returnTarget.isEmpty()) {
            return false;
        }
        BlockPos target = returnTarget.get();
        if (this.friend.blockPosition().distSqr(target) <= 9.0D) {
            this.resetExpeditionMoveWatch();
            if (this.cleanupExpeditionInventoryBeforeCompletion(level, task)) {
                return true;
            }
            this.body.stop();
            this.setExpeditionSupplyStatus("complete: returned");
            return false;
        }
        this.friend.setFriendState(FriendState.RETURNING);
        this.setExpeditionSupplyStatus("returning: " + reason);
        this.sayThrottled("I found the target resource and am returning before I finish the expedition.");
        if (this.isExpeditionMoveStalled(target, "completion return")) {
            this.body.stop();
            this.setExpeditionSupplyStatus("complete: return path stalled");
            this.rememberExpeditionInterrupted(task, "return_stalled", "completion return path stalled");
            this.sayThrottled("The return path stalled, so I am finishing the completed expedition here.");
            return false;
        }
        this.body.moveTo(target, TASK_SPEED);
        return true;
    }

    private Optional<BlockPos> expeditionCompletionReturnTarget(ServerLevel level, FriendTask task) {
        if (this.expeditionSupplyPoint != null
                && level.hasChunkAt(this.expeditionSupplyPoint)
                && this.canStandAt(level, this.expeditionSupplyPoint)) {
            return Optional.of(this.expeditionSupplyPoint);
        }
        return this.findTaskPlayer(task)
                .filter(player -> player.level() == level)
                .map(ServerPlayer::blockPosition);
    }

    private boolean cleanupExpeditionInventoryBeforeCompletion(ServerLevel level, FriendTask task) {
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION || !this.hasCompletionCleanupOverflow(task)) {
            return false;
        }
        Optional<BlockPos> chestPos = this.findNearbySupplyChest(level, 8);
        if (chestPos.isEmpty() || !(level.getBlockEntity(chestPos.get()) instanceof Container chest)) {
            return false;
        }
        this.expeditionSupplyChest = chestPos.get();
        if (!this.interaction.canReachBlock(this.expeditionSupplyChest)) {
            this.setExpeditionSupplyStatus("moving to final unload");
            this.body.moveTo(this.findStandPositionNearBlock(level, this.expeditionSupplyChest).orElse(this.expeditionSupplyChest), TASK_SPEED);
            return true;
        }

        int moved = this.unloadMatchingInventoryItems(chest, stack -> !this.shouldKeepForExpedition(stack, task));
        if (moved > 0) {
            this.setExpeditionSupplyStatus("final inventory unloaded");
            this.rememberPortableNote(task, "expedition final unload into supply chest at "
                    + this.formatPos(this.expeditionSupplyChest)
                    + " moved="
                    + moved);
            this.rememberExpeditionResupplied(task);
        }
        return false;
    }

    private boolean hasCompletionCleanupOverflow(FriendTask task) {
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (!stack.isEmpty() && !this.shouldKeepForExpedition(stack, task)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExpeditionMoveStalled(BlockPos target, String label) {
        if (target == null) {
            this.resetExpeditionMoveWatch();
            return false;
        }
        BlockPos current = this.friend.blockPosition().immutable();
        if (this.expeditionMoveWatchTarget == null || !this.expeditionMoveWatchTarget.equals(target)) {
            this.expeditionMoveWatchTarget = target.immutable();
            this.expeditionMoveWatchLastPos = current;
            this.expeditionMoveWatchLabel = label == null || label.isBlank() ? "moving" : label;
            this.expeditionMoveWatchTicks = 0;
            return false;
        }

        this.expeditionMoveWatchLabel = label == null || label.isBlank() ? this.expeditionMoveWatchLabel : label;
        if (!current.equals(this.expeditionMoveWatchLastPos)) {
            this.expeditionMoveWatchLastPos = current;
            this.expeditionMoveWatchTicks = 0;
            return false;
        }

        this.expeditionMoveWatchTicks++;
        return this.expeditionMoveWatchTicks >= EXPEDITION_MOVE_STALL_TICKS;
    }

    private void resetExpeditionMoveWatch() {
        this.expeditionMoveWatchTarget = null;
        this.expeditionMoveWatchLastPos = null;
        this.expeditionMoveWatchLabel = "none";
        this.expeditionMoveWatchTicks = 0;
    }

    private void pauseCurrentExpedition(String status, String reason, String message) {
        FriendTask task = this.friend.getCurrentTask();
        if (task != null && task.type() == FriendTaskType.MINING_EXPEDITION) {
            this.rememberExpeditionInterrupted(task, status == null || status.isBlank() ? "paused" : status, reason);
            this.setExpeditionSupplyStatus("paused: " + reason);
        }
        this.body.stop();
        this.say(message);
        this.friend.getFriendBrain().completeTask();
    }

    private boolean executeAcquireItemStep(ServerLevel serverLevel, WorkStep step) {
        if (WorkflowFactory.LOGS.equals(step.target())) {
            return this.executeAcquireLogStep(serverLevel, step);
        }
        Optional<MiningTargetRegistry.MiningTarget> miningTarget = MiningTargetRegistry.find(step.target());
        if (miningTarget.isPresent()) {
            MiningTargetRegistry.MiningTarget target = miningTarget.get();
            if (!this.hasToolForAnySource(target.sourceBlocks())) {
                if (this.scheduleToolRecovery(target.toolRequirement(), target.displayName())) {
                    return true;
                }
                step.running("missing tool for " + target.displayName());
                this.sayThrottled("I need " + target.requiredToolHint() + " before mining " + target.displayName() + ".");
                return false;
            }
            return this.executeAcquireMineableResourceStep(
                    serverLevel,
                    step,
                    target.inventoryMatcher(),
                    target.displayName(),
                    target.sourceBlocks()
            );
        }
        if (WorkflowFactory.COBBLESTONE.equals(step.target())) {
            return this.executeAcquireMineableResourceStep(
                    serverLevel,
                    step,
                    stack -> stack.is(Items.COBBLESTONE),
                    "cobblestone",
                    VANILLA_COBBLESTONE_SOURCES
            );
        }
        if (WorkflowFactory.COAL.equals(step.target())) {
            return this.executeAcquireMineableResourceStep(
                    serverLevel,
                    step,
                    stack -> stack.is(Items.COAL),
                    "coal",
                    VANILLA_COAL_ORES
            );
        }
        if (WorkflowFactory.RAW_IRON.equals(step.target())) {
            return this.executeAcquireMineableResourceStep(
                    serverLevel,
                    step,
                    stack -> stack.is(Items.RAW_IRON),
                    "raw iron",
                    VANILLA_IRON_ORES
            );
        }
        Predicate<ItemStack> matcher = this.recipeMatcherFor(step.target());
        if (this.friend.countInventoryItems(matcher) >= step.amount()) {
            this.workflow.completeCurrent("already in inventory");
            return true;
        }
        step.running("waiting for item in inventory");
        this.sayThrottled("I need " + step.target() + " x" + step.amount() + " in my inventory before I can continue.");
        return false;
    }

    private boolean completeCurrentInventoryStepIfSatisfied(WorkStep step) {
        if (step.type() != WorkStepType.ACQUIRE_ITEM && step.type() != WorkStepType.CRAFT_ITEM) {
            return false;
        }
        Optional<Predicate<ItemStack>> matcher = this.workflowStepInventoryMatcher(step.target());
        if (matcher.isEmpty() || this.friend.countInventoryItems(matcher.get()) < step.amount()) {
            return false;
        }
        if (step.type() == WorkStepType.ACQUIRE_ITEM) {
            this.finishResourceExplore();
        }
        this.workflow.completeCurrent("already in inventory");
        return true;
    }

    private Optional<Predicate<ItemStack>> workflowStepInventoryMatcher(String target) {
        if (WorkflowFactory.LOGS.equals(target)) {
            return Optional.of(stack -> stack.is(ItemTags.LOGS));
        }
        Optional<MiningTargetRegistry.MiningTarget> miningTarget = MiningTargetRegistry.find(target);
        if (miningTarget.isPresent()) {
            return Optional.of(miningTarget.get().inventoryMatcher());
        }
        return Optional.of(this.recipeMatcherFor(target));
    }

    private boolean executeCraftItemStep(ServerLevel serverLevel, WorkStep step) {
        if (WorkflowFactory.PLANKS.equals(step.target())) {
            return this.executeCraftPlanksStep(serverLevel, step);
        }
        if (WorkflowFactory.CRAFTING_TABLE.equals(step.target())) {
            return this.executeCraftingTableCraftStep(serverLevel, step);
        }
        if (WorkflowFactory.STICKS.equals(step.target())) {
            return this.executeSticksCraftStep(serverLevel, step);
        }
        if (WorkflowFactory.CHEST.equals(step.target())) {
            return this.executeChestCraftStep(serverLevel, step);
        }
        if (WorkflowFactory.WOODEN_AXE.equals(step.target())) {
            return this.executeWoodenAxeCraftStep(serverLevel, step);
        }
        if (WorkflowFactory.WOODEN_PICKAXE.equals(step.target())) {
            return this.executeWoodenPickaxeCraftStep(serverLevel, step);
        }
        return this.executeDynamicCraftStep(serverLevel, step);
    }

    private boolean executePlaceBlockStep(ServerLevel serverLevel, FriendTask task, WorkStep step) {
        if (WorkflowFactory.CRAFTING_TABLE.equals(step.target())) {
            return this.executePlaceCraftingTableStep(serverLevel, task, step);
        }
        if (WorkflowFactory.FURNACE.equals(step.target())) {
            return this.executePlaceFurnaceStep(serverLevel, task, step);
        }
        if (WorkflowFactory.CHEST.equals(step.target())) {
            return this.executePlaceChestStep(serverLevel, task, step);
        }
        return this.failUnsupportedWorkflowStep(step);
    }

    private boolean executeSmeltItemStep(ServerLevel serverLevel, WorkStep step) {
        if (WorkflowFactory.IRON_INGOT.equals(step.target())) {
            return this.executeIronIngotSmeltStep(serverLevel, step);
        }
        return this.failUnsupportedWorkflowStep(step);
    }

    private boolean executeDescendToLayerStep(ServerLevel serverLevel, WorkStep step) {
        Optional<LayerTarget> parsed = this.parseLayerTarget(serverLevel, step.target());
        if (parsed.isEmpty()) {
            step.failed("invalid layer target");
            this.friend.getFriendBrain().failTask("The mining expedition has an invalid Y-layer target: " + step.target());
            return false;
        }

        LayerTarget target = parsed.get();
        String currentDimension = serverLevel.dimension().location().toString();
        if (!target.dimension().equals(currentDimension)) {
            step.failed("wrong dimension");
            this.friend.getFriendBrain().failTask("This expedition needs " + target.dimension() + ", but I am in " + currentDimension + ". I cannot change dimensions by myself yet.");
            return false;
        }

        int currentY = this.friend.blockPosition().getY();
        if (currentY >= target.minY() && currentY <= target.maxY()) {
            this.rememberPortableNote(this.friend.getCurrentTask(), "expedition reached layer target="
                    + step.target()
                    + " pos="
                    + this.formatPos(this.friend.blockPosition()));
            this.rememberExpeditionLayerReached(this.friend.getCurrentTask(), target);
            this.workflow.completeCurrent("reached target layer");
            return true;
        }

        if (this.moveToRememberedMineEntrance(serverLevel, step)) {
            return false;
        }

        Optional<BlockPos> nearbyLayerSpot = this.findNearestStandableInLayer(serverLevel, target, 16);
        if (nearbyLayerSpot.isPresent()) {
            BlockPos spot = nearbyLayerSpot.get();
            step.running("moving to known target-layer floor");
            this.sayThrottled("Moving to target mining layer at " + this.formatPos(spot) + ".");
            this.body.moveTo(spot, TASK_SPEED);
            return false;
        }

        if (currentY < target.minY()) {
            step.running("digging survival staircase up");
            this.updateTunnelTorchProgress();
            this.tryPlaceExpeditionTorch(serverLevel, step);
            return this.digOneStairUp(serverLevel, step);
        }

        step.running("digging survival staircase");
        this.updateTunnelTorchProgress();
        this.tryPlaceExpeditionTorch(serverLevel, step);
        return this.digOneStairDown(serverLevel, step);
    }

    private boolean executeBranchMineResourceStep(ServerLevel serverLevel, WorkStep step) {
        Optional<MiningTargetRegistry.MiningTarget> miningTarget = MiningTargetRegistry.find(step.target());
        if (miningTarget.isEmpty()) {
            return this.failUnsupportedWorkflowStep(step);
        }

        MiningTargetRegistry.MiningTarget target = miningTarget.get();
        int current = this.friend.countInventoryItems(target.inventoryMatcher());
        if (current >= step.amount()) {
            this.workflow.completeCurrent("resource acquired");
            return true;
        }
        if (!this.hasToolForAnySource(target.sourceBlocks())) {
            if (this.expeditionToolRestockUnavailable) {
                step.failed("missing tool and restock unavailable");
                this.pauseCurrentExpedition(
                        "paused",
                        "tool restock unavailable",
                        "I cannot continue branch mining because I do not have a usable "
                                + target.requiredToolHint()
                                + ", and the supply chest could not provide or craft one."
                );
                return false;
            }
            step.running("missing tool for branch mining");
            this.sayThrottled("I still need " + target.requiredToolHint() + " before branch mining " + target.displayName() + ".");
            return false;
        }

        if (this.refreshResourceTargetIfDue(serverLevel, step.target(), target.sourceBlocks())) {
            return this.executeAcquireMineableResourceStep(
                    serverLevel,
                    step,
                    target.inventoryMatcher(),
                    target.displayName(),
                    target.sourceBlocks()
            );
        }

        if (this.moveToRememberedBranchRouteTarget(serverLevel, step)) {
            return false;
        }

        step.running("digging branch tunnel");
        this.updateTunnelTorchProgress();
        this.tryPlaceExpeditionTorch(serverLevel, step);
        return this.digOneBranchTunnelStep(serverLevel, step);
    }

    private boolean failUnsupportedWorkflowStep(WorkStep step) {
        step.failed("unsupported step");
        this.friend.getFriendBrain().failTask("I do not know how to handle workflow step " + step.summary() + ".");
        return false;
    }

    private boolean executeAcquireLogStep(ServerLevel serverLevel, WorkStep step) {
        int requiredPlanks = this.requiredPlanksForPendingWorkflow(serverLevel);
        if (this.workflowOutputSatisfied() || this.availablePlankEquivalent() >= requiredPlanks) {
            this.workflow.completeCurrent("already have materials");
            return true;
        }

        if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            step.failed("mob griefing disabled");
            this.friend.getFriendBrain().failTask("Mob griefing is disabled, so I cannot cut logs for this workflow.");
            return false;
        }

        step.running("collecting log");
        if (this.collectOneLogIntoInventory(serverLevel)) {
            if (this.availablePlankEquivalent() >= requiredPlanks) {
                this.workflow.completeCurrent("log collected");
            }
            return true;
        }
        return false;
    }

    private boolean executeCraftPlanksStep(ServerLevel serverLevel, WorkStep step) {
        int requiredPlanks = this.requiredPlanksForPendingWorkflow(serverLevel);
        if (this.workflowOutputSatisfied() || this.countPlanks() >= requiredPlanks) {
            this.workflow.completeCurrent("already have planks");
            return true;
        }
        if (this.countLogs() <= 0) {
            step.running("waiting for logs");
            return false;
        }

        step.running("crafting planks");
        if (this.craftPlanksFromLog(serverLevel)) {
            this.say("I turned a log into planks.");
            if (this.countPlanks() >= requiredPlanks) {
                this.workflow.completeCurrent("crafted planks");
            }
            return true;
        }
        return false;
    }

    private boolean executeCraftingTableCraftStep(ServerLevel serverLevel, WorkStep step) {
        if (this.workflowNeedsCraftingStation(serverLevel) && this.findNearbyCraftingTable(serverLevel, 12).isPresent()) {
            this.workflow.completeCurrent("nearby crafting table available");
            return true;
        }
        if (this.hasCraftingTable()) {
            this.workflow.completeCurrent("already have crafting table");
            return true;
        }
        if (this.countPlanks() < 4) {
            step.running("waiting for planks");
            return false;
        }

        step.running("crafting table");
        CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOne(
                serverLevel,
                stack -> stack.is(Items.CRAFTING_TABLE),
                2
        );
        if (result == CraftingActionAdapter.CraftResult.CRAFTED) {
            this.workflow.completeCurrent("crafted table");
            this.say("I crafted a crafting table.");
            return true;
        }
        if (result == CraftingActionAdapter.CraftResult.INVENTORY_FULL) {
            step.failed("inventory full");
            this.friend.getFriendBrain().failTask("I have the planks, but my inventory is full.");
            return false;
        }
        if (this.consumePlanksAndCraftTableFallback()) {
            this.workflow.completeCurrent("crafted table with fallback");
            this.say("I crafted a crafting table with the fallback recipe.");
            return true;
        }
        return false;
    }

    private boolean executeSticksCraftStep(ServerLevel serverLevel, WorkStep step) {
        return this.executeRecipeCraftStep(
                serverLevel,
                step,
                stack -> stack.is(Items.STICK),
                this::countSticks,
                () -> this.countPlanks() >= 2,
                "waiting for planks",
                "I crafted sticks.",
                "crafted sticks"
        );
    }

    private boolean executeChestCraftStep(ServerLevel serverLevel, WorkStep step) {
        return this.executeRecipeCraftStep(
                serverLevel,
                step,
                stack -> stack.is(Items.CHEST),
                () -> this.hasChest() ? 1 : 0,
                () -> this.countPlanks() >= 8,
                "waiting for planks",
                "I crafted a chest.",
                "crafted chest"
        );
    }

    private boolean executeWoodenAxeCraftStep(ServerLevel serverLevel, WorkStep step) {
        return this.executeRecipeCraftStep(
                serverLevel,
                step,
                stack -> stack.is(Items.WOODEN_AXE),
                this::countWoodenAxes,
                () -> this.countPlanks() >= 3 && this.countSticks() >= 2,
                "waiting for planks and sticks",
                "I crafted a wooden axe.",
                "crafted wooden axe"
        );
    }

    private boolean executeWoodenPickaxeCraftStep(ServerLevel serverLevel, WorkStep step) {
        return this.executeRecipeCraftStep(
                serverLevel,
                step,
                stack -> stack.is(Items.WOODEN_PICKAXE),
                this::countWoodenPickaxes,
                () -> this.countPlanks() >= 3 && this.countSticks() >= 2,
                "waiting for planks and sticks",
                "I crafted a wooden pickaxe.",
                "crafted wooden pickaxe"
        );
    }

    private boolean executeDynamicCraftStep(ServerLevel serverLevel, WorkStep step) {
        Predicate<ItemStack> matcher = this.recipeMatcherFor(step.target());
        return this.executeRecipeCraftStep(
                serverLevel,
                step,
                matcher,
                () -> this.friend.countInventoryItems(matcher),
                () -> true,
                "waiting for recipe inputs",
                "I crafted " + step.target() + ".",
                "crafted " + step.target()
        );
    }

    private boolean executeIronIngotSmeltStep(ServerLevel serverLevel, WorkStep step) {
        if (this.countIronIngots() >= step.amount()) {
            this.workflow.completeCurrent("already have iron ingot");
            return true;
        }
        if (!this.ensureFurnaceStation(serverLevel, step)) {
            return false;
        }

        step.running("using furnace");
        SmeltingActionAdapter.SmeltResult result = this.smeltingAdapter.smeltOneAtFurnace(
                serverLevel,
                this.furnaceStationTarget,
                stack -> stack.is(Items.IRON_INGOT),
                stack -> stack.is(Items.COAL)
        );
        if (result == SmeltingActionAdapter.SmeltResult.SMELTED) {
            this.say("I smelted raw iron into an iron ingot.");
            if (this.countIronIngots() >= step.amount()) {
                this.rememberResourceKnowledge(
                        WorkflowFactory.IRON_INGOT,
                        "Observed success: use a real placed furnace, insert raw iron into input slot, insert coal fuel, wait for vanilla furnace ticking, then collect iron ingot output.",
                        "observed_furnace_smelting"
                );
                this.workflow.completeCurrent("smelted iron ingot");
            }
            return true;
        }
        if (result == SmeltingActionAdapter.SmeltResult.WORKING) {
            step.running("waiting for furnace");
            return false;
        }
        if (result == SmeltingActionAdapter.SmeltResult.MISSING_STATION) {
            step.running("waiting for furnace");
            return false;
        }
        if (result == SmeltingActionAdapter.SmeltResult.MISSING_INPUT) {
            step.running("waiting for raw iron");
            return false;
        }
        if (result == SmeltingActionAdapter.SmeltResult.MISSING_FUEL) {
            step.running("waiting for fuel");
            return false;
        }
        if (result == SmeltingActionAdapter.SmeltResult.INVENTORY_FULL) {
            step.failed("inventory full");
            this.friend.getFriendBrain().failTask("I smelted the iron, but my inventory is full.");
            return false;
        }
        if (result == SmeltingActionAdapter.SmeltResult.STATION_BLOCKED) {
            step.failed("furnace blocked");
            this.friend.getFriendBrain().failTask("The furnace is occupied by another item, so I cannot smelt iron there.");
            return false;
        }
        step.running("waiting for smelting inputs");
        return false;
    }

    private boolean executeRecipeCraftStep(
            ServerLevel serverLevel,
            WorkStep step,
            Predicate<ItemStack> resultMatcher,
            IntSupplier outputCount,
            BooleanSupplier hasLikelyInputs,
            String waitingForInputs,
            String successMessage,
            String completionNote
    ) {
        if (outputCount.getAsInt() >= step.amount()) {
            this.workflow.completeCurrent("already have " + step.target());
            return true;
        }
        if (!hasLikelyInputs.getAsBoolean()) {
            step.running(waitingForInputs);
            return false;
        }

        if (this.craftingAdapter.requiresCraftingTable(serverLevel, resultMatcher)
                && !this.ensureCraftingStation(serverLevel, step)) {
            return false;
        }

        step.running("crafting " + step.target());
        CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOneWithBestContext(
                serverLevel,
                resultMatcher,
                this.craftingStationTarget
        );
        if (result == CraftingActionAdapter.CraftResult.CRAFTED) {
            this.say(successMessage);
            if (outputCount.getAsInt() >= step.amount()) {
                this.workflow.completeCurrent(completionNote);
            }
            return true;
        }
        if (result == CraftingActionAdapter.CraftResult.MISSING_STATION) {
            step.running("waiting for crafting table");
            return false;
        }
        if (result == CraftingActionAdapter.CraftResult.INVENTORY_FULL) {
            step.failed("inventory full");
            this.friend.getFriendBrain().failTask("I have the materials, but my inventory is full.");
        }
        if (result == CraftingActionAdapter.CraftResult.NO_MATCHING_RECIPE) {
            step.running("waiting for matching recipe inputs");
        }
        return false;
    }

    private boolean executePlaceCraftingTableStep(ServerLevel serverLevel, FriendTask task, WorkStep step) {
        if (this.workflowNeedsCraftingStation(serverLevel)) {
            if (this.ensureCraftingStation(serverLevel, step)) {
                this.workflow.completeCurrent("crafting table ready");
                return true;
            }
            if (!this.hasCraftingTable()) {
                step.running("waiting for crafting table item");
                return false;
            }
        }
        if (!this.hasCraftingTable()) {
            step.running("waiting for crafting table item");
            return false;
        }
        if (this.placeTarget == null || !this.canPlaceCraftingTableAt(serverLevel, this.placeTarget)) {
            this.placeTarget = this.findCraftingTablePlacement(serverLevel, task).orElse(null);
        }
        if (this.placeTarget == null) {
            step.failed("no placement");
            this.friend.getFriendBrain().failTask("I made a crafting table, but I cannot find a safe place to put it.");
            return false;
        }
        if (!this.interaction.canReachBlock(this.placeTarget)) {
            step.running("moving to placement");
            this.sayThrottled("I made a crafting table. Moving to a place where I can put it down.");
        } else {
            step.running("placing");
        }

        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                serverLevel,
                this.placeTarget,
                Blocks.CRAFTING_TABLE,
                stack -> stack.is(Items.CRAFTING_TABLE),
                () -> this.findStandPositionNearBlock(serverLevel, this.placeTarget),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            return false;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.say("I placed a crafting table at " + this.formatPos(this.placeTarget) + ".");
            this.craftingStationTarget = this.placeTarget;
            this.workflow.completeCurrent("placed");
            if (!this.workflowNeedsCraftingStation(serverLevel)) {
                this.friend.getFriendBrain().completeTask();
            }
            return false;
        }

        this.placeTarget = null;
        this.sayThrottled("That spot is blocked. Looking for another place to put the crafting table.");
        return false;
    }

    private boolean executePlaceFurnaceStep(ServerLevel serverLevel, FriendTask task, WorkStep step) {
        if (this.findNearbyFurnace(serverLevel, 12).isPresent()) {
            this.furnaceStationTarget = this.findNearbyFurnace(serverLevel, 12).get();
            this.rememberExpeditionFurnaceReady(task, this.furnaceStationTarget, "reused furnace");
            this.workflow.completeCurrent("nearby furnace available");
            return true;
        }
        if (!this.hasFurnace()) {
            step.running("waiting for furnace item");
            return false;
        }
        if (this.placeTarget == null || !this.canPlaceBlockAt(serverLevel, this.placeTarget)) {
            this.placeTarget = this.findCraftingTablePlacement(serverLevel, task).orElse(null);
        }
        if (this.placeTarget == null) {
            step.failed("no placement");
            this.friend.getFriendBrain().failTask("I made a furnace, but I cannot find a safe place to put it.");
            return false;
        }
        if (!this.interaction.canReachBlock(this.placeTarget)) {
            step.running("moving to furnace placement");
            this.sayThrottled("Moving to a place where I can put the furnace down.");
        } else {
            step.running("placing furnace");
        }

        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                serverLevel,
                this.placeTarget,
                Blocks.FURNACE,
                stack -> stack.is(Items.FURNACE),
                () -> this.findStandPositionNearBlock(serverLevel, this.placeTarget),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            return false;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.say("I placed a furnace at " + this.formatPos(this.placeTarget) + ".");
            this.furnaceStationTarget = this.placeTarget;
            this.rememberExpeditionFurnaceReady(task, this.furnaceStationTarget, "placed furnace");
            this.workflow.completeCurrent("placed furnace");
            return false;
        }

        this.placeTarget = null;
        this.sayThrottled("That furnace spot is blocked. Looking for another place.");
        return false;
    }

    private boolean executePlaceChestStep(ServerLevel serverLevel, FriendTask task, WorkStep step) {
        Optional<BlockPos> nearbyChest = this.findNearbySupplyChest(serverLevel, 8);
        if (nearbyChest.isPresent()) {
            this.expeditionSupplyChest = nearbyChest.get();
            this.rememberPortableNote(task, "expedition reused supply chest at " + this.formatPos(this.expeditionSupplyChest));
            this.rememberExpeditionSupplyChest(task, this.expeditionSupplyChest, "reused supply chest");
            this.workflow.completeCurrent("supply chest ready");
            return true;
        }
        if (!this.hasChest()) {
            step.running("waiting for chest item");
            return false;
        }
        if (this.placeTarget == null || !this.canPlaceBlockAt(serverLevel, this.placeTarget)) {
            this.placeTarget = this.findSupplyChestPlacement(serverLevel, task).orElse(null);
        }
        if (this.placeTarget == null) {
            step.failed("no chest placement");
            this.friend.getFriendBrain().failTask("I made a chest, but I cannot find a safe supply spot.");
            return false;
        }
        if (!this.interaction.canReachBlock(this.placeTarget)) {
            step.running("moving to supply chest placement");
            this.sayThrottled("Moving to set up a supply chest.");
        } else {
            step.running("placing supply chest");
        }

        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                serverLevel,
                this.placeTarget,
                Blocks.CHEST,
                stack -> stack.is(Items.CHEST),
                () -> this.findStandPositionNearBlock(serverLevel, this.placeTarget),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            return false;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.say("I placed a supply chest at " + this.formatPos(this.placeTarget) + ".");
            this.expeditionSupplyChest = this.placeTarget;
            this.rememberPortableNote(task, "expedition placed supply chest at " + this.formatPos(this.expeditionSupplyChest));
            this.rememberExpeditionSupplyChest(task, this.expeditionSupplyChest, "placed supply chest");
            this.workflow.completeCurrent("placed supply chest");
            return true;
        }

        this.placeTarget = null;
        this.sayThrottled("That supply chest spot is blocked. Looking for another place.");
        return false;
    }

    private boolean collectOneLogIntoInventory(ServerLevel serverLevel) {
        if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            this.friend.getFriendBrain().failTask("Mob griefing is disabled, so I cannot break logs.");
            return false;
        }

        if (this.woodTarget == null
                || !isBreakableLog(serverLevel, this.woodTarget)
                || !this.isReachableMiningTarget(serverLevel, this.woodTarget)) {
            this.woodTarget = null;
            if (this.woodSearchCooldownTicks <= 0) {
                this.woodTarget = this.findNearestReachableLog(serverLevel, WOOD_SEARCH_RADIUS).orElse(null);
                this.woodSearchCooldownTicks = WOOD_SEARCH_INTERVAL_TICKS;
            }
            if (this.woodTarget == null) {
                if (this.clearLeafBlockingObservedLog(serverLevel)) {
                    return false;
                }
                return this.exploreForWood(serverLevel);
            }
            this.woodLeafTarget = null;
            this.resetWoodExploreTarget();
            this.say("I found a log at " + this.formatPos(this.woodTarget) + ".");
        }

        double distance = this.friend.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(this.woodTarget));
        if (!this.interaction.canReachBlock(this.woodTarget)) {
            this.sayThrottled("Moving to the log at " + this.formatPos(this.woodTarget) + " (" + Math.round(distance) + " blocks away).");
        }

        MineActionAdapter.MineResult result = this.mineAdapter.mineOne(
                serverLevel,
                this.woodTarget,
                () -> this.findStandPositionNearBlock(serverLevel, this.woodTarget),
                TASK_SPEED
        );
        switch (result) {
            case BROKEN -> {
                this.say("Collected wood. Inventory: " + this.friend.getInventorySummary());
                this.woodTarget = null;
                this.woodLeafTarget = null;
                this.woodSearchCooldownTicks = 0;
                this.resetWoodExploreTarget();
                return true;
            }
            case FAILED -> {
                this.friend.getFriendBrain().failTask("I reached the log, but I could not break it like a survival player.");
                return false;
            }
            case WORKING_FALLBACK -> {
                return false;
            }
        }

        return false;
    }

    private boolean clearLeafBlockingObservedLog(ServerLevel level) {
        if (this.woodLeafTarget == null
                || !this.isBreakableLeaf(level, this.woodLeafTarget)
                || !this.isReachableMiningTarget(level, this.woodLeafTarget)) {
            this.woodLeafTarget = this.findNearestReachableLeafBlockingObservedLog(level, WOOD_SEARCH_RADIUS).orElse(null);
        }
        if (this.woodLeafTarget == null) {
            return false;
        }

        if (!this.interaction.canReachBlock(this.woodLeafTarget)) {
            this.sayThrottled("Moving closer to leaves that block an observed log at "
                    + this.formatPos(this.woodLeafTarget)
                    + ".");
        }
        MineActionAdapter.MineResult result = this.mineAdapter.mineOne(
                level,
                this.woodLeafTarget,
                () -> this.findStandPositionNearBlock(level, this.woodLeafTarget),
                TASK_SPEED
        );
        switch (result) {
            case BROKEN -> {
                this.say("I cleared leaves blocking the tree.");
                this.woodLeafTarget = null;
                this.woodSearchCooldownTicks = 0;
            }
            case FAILED -> {
                this.woodLeafTarget = null;
            }
            case WORKING_FALLBACK -> {
            }
        }
        return true;
    }

    private boolean executeAcquireMineableResourceStep(
            ServerLevel serverLevel,
            WorkStep step,
            Predicate<ItemStack> inventoryMatcher,
            String displayName,
            Block... sourceBlocks
    ) {
        if (!this.resourceExploreBreadcrumbs.isEmpty()) {
            this.rememberResourceExploreBreadcrumb(this.friend.blockPosition());
        }
        int current = this.friend.countInventoryItems(inventoryMatcher);
        if (this.workflowOutputSatisfied() || current >= step.amount()) {
            this.finishResourceExplore();
            this.workflow.completeCurrent("already have " + displayName);
            return true;
        }

        if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            step.failed("mob griefing disabled");
            this.friend.getFriendBrain().failTask("Mob griefing is disabled, so I cannot mine " + displayName + ".");
            return false;
        }

        this.refreshResourceTargetIfDue(serverLevel, step.target(), sourceBlocks);

        if (this.resourceTarget == null) {
            return MiningTargetRegistry.find(step.target())
                    .map(target -> this.exploreForMineableResource(serverLevel, step, target))
                    .orElseGet(() -> {
                        step.running("searching for " + displayName);
                        this.sayThrottled("I need " + displayName + ", but I do not see an exposed reachable block nearby.");
                        return false;
                    });
        }

        double distance = this.friend.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(this.resourceTarget));
        Optional<BlockPos> approachTarget = Optional.empty();
        if (!this.interaction.canReachBlock(this.resourceTarget)) {
            approachTarget = this.findStandPositionNearBlock(serverLevel, this.resourceTarget);
            if (approachTarget.isEmpty()) {
                this.resourceTarget = null;
                step.running("searching for reachable " + displayName);
                this.sayThrottled("That " + displayName + " is not reachable like a survival player. Looking for another target.");
                return false;
            }
            boolean expeditionApproachStalled = this.friend.getCurrentTask() != null
                    && this.friend.getCurrentTask().type() == FriendTaskType.MINING_EXPEDITION
                    && this.isExpeditionMoveStalled(approachTarget.get(), "approach " + displayName);
            boolean ordinaryApproachStalled = (this.friend.getCurrentTask() == null
                    || this.friend.getCurrentTask().type() != FriendTaskType.MINING_EXPEDITION)
                    && this.isResourceExploreMoveStalled(approachTarget.get(), RESOURCE_TARGET_APPROACH_STALL_TICKS);
            if (expeditionApproachStalled || ordinaryApproachStalled) {
                this.body.stop();
                this.rejectResourceTarget();
                step.running("approach to " + displayName + " stalled");
                if (expeditionApproachStalled) {
                    this.setExpeditionSupplyStatus("searching: approach stalled");
                }
                this.sayThrottled("I cannot reach that " + displayName + " safely, so I am looking for another route.");
                return false;
            }
            step.running("moving to " + displayName);
            this.sayThrottled("Moving to " + displayName + " at " + this.formatPos(this.resourceTarget) + " (" + Math.round(distance) + " blocks away).");
        } else {
            this.resetExpeditionMoveWatch();
            step.running("mining " + displayName);
        }

        Optional<BlockPos> cachedApproachTarget = approachTarget;
        MineActionAdapter.MineResult result = this.mineAdapter.mineOne(
                serverLevel,
                this.resourceTarget,
                () -> cachedApproachTarget.isPresent() ? cachedApproachTarget : this.findStandPositionNearBlock(serverLevel, this.resourceTarget),
                TASK_SPEED,
                cachedApproachTarget.map(pos -> this.friend.blockPosition().distSqr(pos) <= 4.0D).orElse(false)
        );
        switch (result) {
            case BROKEN -> {
                BlockPos minedTarget = this.resourceTarget;
                this.resourceTarget = null;
                this.resourceSearchCooldownTicks = 0;
                int minedCount = this.friend.countInventoryItems(inventoryMatcher);
                if (minedCount > current) {
                    this.rememberVisibleExpeditionTargetMiningSuccess(step, minedCount, minedCount - current, minedTarget);
                }
                if (minedCount >= step.amount()) {
                    this.finishResourceExplore();
                    this.rememberResourceKnowledge(
                            step.target(),
                            "Observed success: mined " + displayName + " in " + serverLevel.dimension().location() + " using targeted survival-style block breaking.",
                            "observed_mining"
                    );
                    this.workflow.completeCurrent("mined " + displayName);
                }
                return true;
            }
            case FAILED -> {
                this.resourceTarget = null;
                this.sayThrottled("That " + displayName + " target failed. Looking for another one.");
                return false;
            }
            case WORKING_FALLBACK -> {
                return false;
            }
        }
        return false;
    }

    private boolean scheduleToolRecovery(
            MiningTargetRegistry.ToolRequirement requirement,
            String blockedResourceName
    ) {
        if (this.workflow == null || requirement == null || requirement == MiningTargetRegistry.ToolRequirement.NONE) {
            return false;
        }
        List<WorkStep> recoverySteps = WorkflowFactory.toolRecovery(requirement);
        if (!this.workflow.insertBeforeCurrent(recoverySteps)) {
            return false;
        }
        this.finishResourceExplore();
        this.body.stop();
        this.say("My mining tool is no longer usable. I am preparing a replacement before continuing "
                + blockedResourceName
                + ".");
        return true;
    }

    private boolean exploreForMineableResource(
            ServerLevel level,
            WorkStep step,
            MiningTargetRegistry.MiningTarget target
    ) {
        MiningTargetRegistry.ExplorationProfile profile = target.explorationProfile();
        if (profile == null) {
            step.running("searching for " + target.displayName());
            this.sayThrottled("I need " + target.displayName() + ", but I do not see an exposed reachable block nearby.");
            return false;
        }

        String currentDimension = level.dimension().location().toString();
        if (!profile.dimension().equals(currentDimension)) {
            step.failed("wrong dimension for resource exploration");
            this.friend.getFriendBrain().failTask("I need "
                    + target.displayName()
                    + " in "
                    + profile.dimension()
                    + ", but I am in "
                    + currentDimension
                    + ". I cannot change dimensions by myself yet.");
            return false;
        }

        this.ensureResourceExploreState(level, step.target(), profile);
        this.updateResourceExploreTraversalProgress();
        BlockPos current = this.friend.blockPosition();
        if (this.isFluidDisplaced(level, current)) {
            step.running("recovering from fluid displacement");
            this.sayThrottled("Water displaced me from the mining route, so I am backtracking to a dry passage.");
            if (this.moveBackAlongResourceExplorePath(step, "dry mining passage")) {
                return false;
            }
            this.rotateResourceExploreDirection();
            return false;
        }
        this.lowerResourceExploreAnchorIfStillSurface(level, current);
        Direction direction = this.resourceExploreDirection();
        BlockPos nextFeet;
        String label;
        if (current.getY() > this.resourceExploreTargetY) {
            nextFeet = current.relative(direction).below();
            label = "resource exploration staircase down";
        } else if (current.getY() < this.resourceExploreTargetY) {
            nextFeet = current.relative(direction).above();
            label = "resource exploration staircase up";
        } else {
            nextFeet = current.relative(direction);
            label = "resource exploration tunnel";
        }

        if (!this.canUsePassageFoot(level, nextFeet)) {
            if (this.tryRepairPassageFloor(level, step, nextFeet, label)) {
                return false;
            }
            this.rotateResourceExploreDirection();
            step.running("finding safe resource exploration direction");
            this.sayThrottled("I do not see exposed safely mineable "
                    + this.resourceExplorationSourceName(target)
                    + ", so I am rotating toward a safer exploration route.");
            return false;
        }

        step.running("digging " + label);
        this.sayThrottled("I do not see exposed safely mineable "
                + this.resourceExplorationSourceName(target)
                + ", so I am digging a survival "
                + (label.contains("staircase") ? "staircase" : "traversal tunnel")
                + " from Y "
                + current.getY()
                + " toward "
                + direction.getName()
                + " (preferred Y "
                + profile.preferredYMin()
                + ".."
                + profile.preferredYMax()
                + ", tunnel anchor Y "
                + this.resourceExploreTargetY
                + ", segment remaining "
                + this.resourceExploreStepsRemaining
                + ").");
        return this.moveOrDigResourceExplorePassage(level, step, nextFeet, label);
    }

    private String resourceExplorationSourceName(MiningTargetRegistry.MiningTarget target) {
        return WorkflowFactory.COBBLESTONE.equals(target.resourceId())
                ? "stone or cobblestone"
                : target.displayName();
    }

    private boolean moveOrDigResourceExplorePassage(
            ServerLevel level,
            WorkStep step,
            BlockPos nextFeet,
            String label
    ) {
        Optional<BlockPos> blocker = this.firstBlockingPassageBlock(level, nextFeet);
        if (blocker.isEmpty() && FriendPerception.canStandAt(level, nextFeet)) {
            this.resourceExploreDigTarget = null;
            if (this.isResourceExploreMoveStalled(nextFeet)) {
                this.body.stop();
                this.rotateResourceExploreDirection();
                step.running("movement stalled in " + label);
                this.sayThrottled("The resource exploration route stalled, so I am rotating toward another passage.");
                return false;
            }
            this.body.moveToNearby(nextFeet, TASK_SPEED);
            step.running("moving through " + label);
            return false;
        }

        if (blocker.isEmpty()) {
            this.rotateResourceExploreDirection();
            step.running("blocked by unsafe " + label);
            return false;
        }

        BlockPos digTarget = blocker.get();
        this.resourceExploreDigTarget = digTarget;
        BlockState state = level.getBlockState(digTarget);
        if (state.hasBlockEntity()
                || state.getDestroySpeed(level, digTarget) < 0.0F
                || !state.getFluidState().isEmpty()
                || this.hasNearbyFluidHazard(level, digTarget)
                || this.hasNearbyFluidLeak(level, digTarget)
                || this.hasFallingBlockCollapseRisk(level, digTarget)
                || this.isRiskyExpeditionPassageBlock(state)) {
            this.rotateResourceExploreDirection();
            step.running("avoiding unsafe block in " + label);
            return false;
        }

        if (!this.interaction.canReachBlock(digTarget)) {
            Optional<BlockPos> approach = this.findStandPositionNearBlock(level, digTarget);
            if (approach.isEmpty()) {
                this.rotateResourceExploreDirection();
                step.running("no safe dig approach for " + label);
                return false;
            }
            if (this.isResourceExploreMoveStalled(approach.get())) {
                this.body.stop();
                this.rotateResourceExploreDirection();
                step.running("dig approach stalled in " + label);
                this.sayThrottled("I could not reach that exploration dig position, so I am rotating toward another passage.");
                return false;
            }
            this.body.moveTo(approach.get(), TASK_SPEED);
            step.running("moving to dig " + label);
            return false;
        }

        this.resetResourceExploreMoveWatch();
        this.body.stop();
        SurvivalWorldInteractor.BreakResult result = this.interaction.tickBreakBlock(level, digTarget);
        switch (result) {
            case BROKEN -> {
                this.resourceExploreDigTarget = null;
                this.resourceSearchCooldownTicks = 0;
                step.running("dug " + label);
                return false;
            }
            case FAILED -> {
                this.rotateResourceExploreDirection();
                step.running("failed to dig " + label);
                return false;
            }
            case NOT_IN_REACH, WORKING -> {
                step.running("digging " + label);
                return false;
            }
        }
        return false;
    }

    private void ensureResourceExploreState(
            ServerLevel level,
            String targetKind,
            MiningTargetRegistry.ExplorationProfile profile
    ) {
        if (Objects.equals(targetKind, this.resourceExploreKind) && this.resourceExploreDirection != null) {
            return;
        }
        this.resetResourceExplore();
        this.resourceExploreKind = targetKind;
        this.resourceExploreDirection = HORIZONTAL_EXPEDITION_DIRECTIONS[this.friend.getRandom().nextInt(HORIZONTAL_EXPEDITION_DIRECTIONS.length)];
        this.resourceExploreTargetY = this.chooseResourceExploreTargetY(level, profile);
        this.resourceExploreBaseSegmentLength = Math.max(3, profile.traversalSegmentLength());
        this.resourceExploreStepsRemaining = this.resourceExploreBaseSegmentLength;
        this.resourceExploreLastStepPos = this.friend.blockPosition().immutable();
        this.rememberResourceExploreBreadcrumb(this.resourceExploreLastStepPos);
    }

    private int chooseResourceExploreTargetY(
            ServerLevel level,
            MiningTargetRegistry.ExplorationProfile profile
    ) {
        BlockPos current = this.friend.blockPosition();
        if (current.getY() > profile.preferredYMax()) {
            return profile.preferredYMax();
        }
        if (current.getY() < profile.preferredYMin()) {
            return profile.preferredYMin();
        }
        if (this.isSurfaceExposedResourceExplorePosition(level, current)) {
            return this.lowerResourceExploreAnchor(level, current.getY());
        }
        return current.getY();
    }

    private void lowerResourceExploreAnchorIfStillSurface(ServerLevel level, BlockPos current) {
        if (current.getY() != this.resourceExploreTargetY
                || !this.isSurfaceExposedResourceExplorePosition(level, current)) {
            return;
        }
        this.resourceExploreTargetY = this.lowerResourceExploreAnchor(level, current.getY());
    }

    private int lowerResourceExploreAnchor(ServerLevel level, int currentY) {
        return Math.max(level.getMinBuildHeight() + 2, currentY - RESOURCE_EXPLORE_SURFACE_DESCENT_BLOCKS);
    }

    private boolean isSurfaceExposedResourceExplorePosition(ServerLevel level, BlockPos pos) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return pos.getY() >= surfaceY - 1 || level.canSeeSky(pos.above());
    }

    private void updateResourceExploreTraversalProgress() {
        BlockPos current = this.friend.blockPosition().immutable();
        if (this.resourceExploreLastStepPos == null) {
            this.resourceExploreLastStepPos = current;
            return;
        }
        if (current.equals(this.resourceExploreLastStepPos)) {
            return;
        }
        this.resourceExploreLastStepPos = current;
        this.rememberResourceExploreBreadcrumb(current);
        this.resourceExploreStepsRemaining--;
        if (this.resourceExploreStepsRemaining <= 0) {
            this.advanceResourceExploreTraversalDirection();
        }
    }

    private Direction resourceExploreDirection() {
        if (this.resourceExploreDirection == null || this.resourceExploreDirection.getAxis().isVertical()) {
            Direction facing = this.friend.getDirection();
            this.resourceExploreDirection = facing.getAxis().isVertical() ? Direction.NORTH : facing;
        }
        return this.resourceExploreDirection;
    }

    private void rotateResourceExploreDirection() {
        this.rotateResourceExploreDirection(false);
    }

    private void advanceResourceExploreTraversalDirection() {
        this.rotateResourceExploreDirection(true);
    }

    private void rotateResourceExploreDirection(boolean expandTraversal) {
        Direction base = this.resourceExploreDirection();
        this.resourceExploreDirection = base.getClockWise();
        if (expandTraversal) {
            this.resourceExploreTurns++;
        }
        int traversalLength = this.resourceExploreBaseSegmentLength + (this.resourceExploreTurns / 2) * 2;
        this.resourceExploreStepsRemaining = expandTraversal
                ? traversalLength
                : Math.max(1, Math.min(this.resourceExploreStepsRemaining, traversalLength));
        this.resourceExploreDigTarget = null;
        this.resetResourceExploreMoveWatch();
        this.interaction.cancelBreakBlock();
    }

    private boolean isResourceExploreMoveStalled(BlockPos target) {
        return this.isResourceExploreMoveStalled(target, RESOURCE_EXPLORE_MOVE_STALL_TICKS);
    }

    private boolean isResourceExploreMoveStalled(BlockPos target, int stallTicks) {
        if (target == null) {
            this.resetResourceExploreMoveWatch();
            return false;
        }
        BlockPos current = this.friend.blockPosition().immutable();
        if (this.resourceExploreMoveWatchTarget == null || !this.resourceExploreMoveWatchTarget.equals(target)) {
            this.resourceExploreMoveWatchTarget = target.immutable();
            this.resourceExploreMoveWatchLastPos = current;
            this.resourceExploreMoveWatchTicks = 0;
            return false;
        }
        if (!current.equals(this.resourceExploreMoveWatchLastPos)) {
            this.resourceExploreMoveWatchLastPos = current;
            this.resourceExploreMoveWatchTicks = 0;
            return false;
        }
        this.resourceExploreMoveWatchTicks++;
        return this.resourceExploreMoveWatchTicks >= stallTicks;
    }

    private void resetResourceExploreMoveWatch() {
        this.resourceExploreMoveWatchTarget = null;
        this.resourceExploreMoveWatchLastPos = null;
        this.resourceExploreMoveWatchTicks = 0;
    }

    private void resetResourceExplore() {
        this.finishResourceExplore();
        this.resourceExploreBreadcrumbs = new ArrayList<>();
    }

    private void finishResourceExplore() {
        this.resourceExploreKind = null;
        this.resourceExploreDigTarget = null;
        this.resourceExploreDirection = null;
        this.resourceExploreLastStepPos = null;
        this.resourceExploreTargetY = 0;
        this.resourceExploreBaseSegmentLength = 0;
        this.resourceExploreStepsRemaining = 0;
        this.resourceExploreTurns = 0;
        this.resetResourceExploreMoveWatch();
    }

    private void rememberResourceExploreBreadcrumb(BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (!this.resourceExploreBreadcrumbs.isEmpty()
                && this.resourceExploreBreadcrumbs.get(this.resourceExploreBreadcrumbs.size() - 1).equals(pos)) {
            return;
        }
        this.resourceExploreBreadcrumbs.add(pos.immutable());
        while (this.resourceExploreBreadcrumbs.size() > RESOURCE_EXPLORE_BREADCRUMB_LIMIT) {
            this.resourceExploreBreadcrumbs.remove(0);
        }
    }

    private boolean moveBackAlongResourceExplorePath(WorkStep step, String destinationLabel) {
        if (this.resourceExploreBreadcrumbs.isEmpty()) {
            return false;
        }

        BlockPos current = this.friend.blockPosition().immutable();
        while (!this.resourceExploreBreadcrumbs.isEmpty()
                && this.resourceExploreBreadcrumbs.get(this.resourceExploreBreadcrumbs.size() - 1).equals(current)) {
            this.resourceExploreBreadcrumbs.remove(this.resourceExploreBreadcrumbs.size() - 1);
        }
        if (this.resourceExploreBreadcrumbs.isEmpty()) {
            this.resetResourceExploreMoveWatch();
            return false;
        }

        BlockPos previous = this.resourceExploreBreadcrumbs.get(this.resourceExploreBreadcrumbs.size() - 1);
        if (this.isResourceExploreMoveStalled(previous)) {
            this.body.stop();
            this.resourceExploreBreadcrumbs = new ArrayList<>();
            this.resetResourceExploreMoveWatch();
            return false;
        }

        step.running("backtracking resource exploration route to " + destinationLabel);
        this.sayThrottled("Returning along the explored mining route before moving to the " + destinationLabel + ".");
        if (current.distSqr(previous) <= 4.0D) {
            this.body.moveToNearby(previous, TASK_SPEED);
        } else {
            this.body.moveTo(previous, TASK_SPEED);
        }
        return true;
    }

    private void rememberVisibleExpeditionTargetMiningSuccess(
            WorkStep step,
            int minedTotal,
            int minedDelta,
            BlockPos minedTarget
    ) {
        FriendTask task = this.friend.getCurrentTask();
        if (task == null
                || task.type() != FriendTaskType.MINING_EXPEDITION
                || step.type() != WorkStepType.BRANCH_MINE_RESOURCE) {
            return;
        }
        if (minedTotal <= 0 || minedDelta <= 0) {
            return;
        }
        BlockPos hitPos = minedTarget == null ? this.friend.blockPosition() : minedTarget;
        this.rememberPortableNote(task, "expedition mined "
                + step.target()
                + " amount="
                + minedDelta
                + " total="
                + minedTotal
                + " pos="
                + this.formatPos(hitPos));
        this.rememberExpeditionMiningSuccess(task, step.target(), minedTotal, minedDelta, hitPos);
    }

    private boolean craftPlanksFromLog(ServerLevel level) {
        CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOne(
                level,
                stack -> stack.is(ItemTags.PLANKS),
                2
        );
        if (result == CraftingActionAdapter.CraftResult.CRAFTED) {
            return true;
        }
        if (result == CraftingActionAdapter.CraftResult.INVENTORY_FULL) {
            this.friend.getFriendBrain().failTask("I have a log, but my inventory is full.");
            return false;
        }
        return this.consumeLogAndCraftPlanksFallback();
    }

    private boolean consumeLogAndCraftPlanksFallback() {
        if (!this.friend.consumeInventoryItems(stack -> stack.is(ItemTags.LOGS), 1)) {
            return false;
        }
        ItemStack remainder = this.friend.insertIntoInventory(new ItemStack(Items.OAK_PLANKS, 4));
        if (!remainder.isEmpty()) {
            Block.popResource((ServerLevel) this.friend.level(), this.friend.blockPosition(), remainder);
        }
        return true;
    }

    private boolean consumePlanksAndCraftTableFallback() {
        if (!this.friend.consumeInventoryItems(stack -> stack.is(ItemTags.PLANKS), 4)) {
            return false;
        }
        ItemStack remainder = this.friend.insertIntoInventory(new ItemStack(Items.CRAFTING_TABLE));
        if (!remainder.isEmpty()) {
            this.friend.getFriendBrain().failTask("I crafted a table, but my inventory is full.");
            return false;
        }
        return true;
    }

    private int countPlanks() {
        return this.friend.countInventoryItems(stack -> stack.is(ItemTags.PLANKS));
    }

    private int availablePlankEquivalent() {
        return this.countPlanks() + this.countLogs() * 4;
    }

    private int requiredPlanksForPendingWorkflow(ServerLevel level) {
        if (this.workflow == null) {
            return 0;
        }

        int requiredPlanks = 0;
        int availableSticks = this.countSticks();
        boolean craftingTableReady = this.hasCraftingTable() || this.findNearbyCraftingTable(level, 12).isPresent();
        for (WorkStep pending : this.workflow.pendingSteps()) {
            if (pending.type() != WorkStepType.CRAFT_ITEM) {
                continue;
            }
            switch (pending.target()) {
                case WorkflowFactory.PLANKS -> {
                    FriendTask task = this.friend.getCurrentTask();
                    if (task != null
                            && task.type() == FriendTaskType.CRAFT_ITEM
                            && WorkflowFactory.PLANKS.equals(task.target())) {
                        requiredPlanks = Math.max(requiredPlanks, pending.amount());
                    }
                }
                case WorkflowFactory.CRAFTING_TABLE -> {
                    if (!craftingTableReady) {
                        requiredPlanks += 4;
                        craftingTableReady = true;
                    }
                }
                case WorkflowFactory.STICKS -> {
                    int missingSticks = Math.max(0, pending.amount() - availableSticks);
                    int stickCrafts = divideRoundUp(missingSticks, 4);
                    requiredPlanks += stickCrafts * 2;
                    availableSticks += stickCrafts * 4;
                }
                case WorkflowFactory.CHEST -> {
                    int missingChests = Math.max(0, pending.amount() - (this.hasChest() ? 1 : 0));
                    requiredPlanks += missingChests * 8;
                }
                case WorkflowFactory.WOODEN_AXE -> {
                    int missingAxes = Math.max(0, pending.amount() - this.countWoodenAxes());
                    int requiredSticks = missingAxes * 2;
                    int stickCrafts = divideRoundUp(Math.max(0, requiredSticks - availableSticks), 4);
                    requiredPlanks += missingAxes * 3 + stickCrafts * 2;
                    availableSticks += stickCrafts * 4 - requiredSticks;
                }
                case WorkflowFactory.WOODEN_PICKAXE -> {
                    int missingPickaxes = Math.max(0, pending.amount() - this.countWoodenPickaxes());
                    int requiredSticks = missingPickaxes * 2;
                    int stickCrafts = divideRoundUp(Math.max(0, requiredSticks - availableSticks), 4);
                    requiredPlanks += missingPickaxes * 3 + stickCrafts * 2;
                    availableSticks += stickCrafts * 4 - requiredSticks;
                }
                case WorkflowFactory.TORCH -> {
                    int missingTorches = Math.max(0, pending.amount() - this.countTorches());
                    int torchCrafts = divideRoundUp(missingTorches, 4);
                    int stickCrafts = divideRoundUp(Math.max(0, torchCrafts - availableSticks), 4);
                    requiredPlanks += stickCrafts * 2;
                    availableSticks += stickCrafts * 4 - torchCrafts;
                }
                default -> {
                }
            }
        }
        return requiredPlanks;
    }

    private static int divideRoundUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    private int countLogs() {
        return this.friend.countInventoryItems(stack -> stack.is(ItemTags.LOGS));
    }

    private int countSticks() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.STICK));
    }

    private boolean hasCraftingTable() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.CRAFTING_TABLE)) > 0;
    }

    private boolean hasChest() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.CHEST)) > 0;
    }

    private boolean hasFurnace() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.FURNACE)) > 0;
    }

    private boolean hasBlastFurnace() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.BLAST_FURNACE)) > 0;
    }

    private Block expeditionFloorRepairBlockToPlace(FriendTask task) {
        if (this.canUseForFloorRepair(task, Items.COBBLESTONE)) {
            return Blocks.COBBLESTONE;
        }
        if (this.canUseForFloorRepair(task, Items.COBBLED_DEEPSLATE)) {
            return Blocks.COBBLED_DEEPSLATE;
        }
        if (this.canUseForFloorRepair(task, Items.DIRT)) {
            return Blocks.DIRT;
        }
        if (this.canUseForFloorRepair(task, Items.NETHERRACK)) {
            return Blocks.NETHERRACK;
        }
        return null;
    }

    private int countConstructionRepairBlocks() {
        FriendTask task = this.friend.getCurrentTask();
        return this.countFloorRepairItems(task, Items.COBBLESTONE)
                + this.countFloorRepairItems(task, Items.COBBLED_DEEPSLATE)
                + this.countFloorRepairItems(task, Items.DIRT)
                + this.countFloorRepairItems(task, Items.NETHERRACK);
    }

    private int countFloorRepairItems(FriendTask task, net.minecraft.world.item.Item item) {
        return this.canUseForFloorRepair(task, item)
                ? this.friend.countInventoryItems(stack -> stack.is(item))
                : 0;
    }

    private boolean canUseForFloorRepair(FriendTask task, net.minecraft.world.item.Item item) {
        if (item == null || this.friend.countInventoryItems(stack -> stack.is(item)) <= 0) {
            return false;
        }
        if (task == null || task.target() == null || task.target().isBlank()) {
            return true;
        }
        return MiningTargetRegistry.find(task.target())
                .map(target -> !target.inventoryMatcher().test(new ItemStack(item)))
                .orElse(true);
    }

    private boolean isMatchingFloorRepairBlock(ItemStack stack, Block block) {
        return !stack.isEmpty() && block != null && stack.is(block.asItem());
    }

    private Block supplyFurnaceBlockToPlace() {
        if (this.hasFurnace()) {
            return Blocks.FURNACE;
        }
        if (this.hasBlastFurnace()) {
            return Blocks.BLAST_FURNACE;
        }
        return null;
    }

    private int countWoodenAxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.WOODEN_AXE));
    }

    private int countWoodenPickaxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.WOODEN_PICKAXE));
    }

    private int countStonePickaxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.STONE_PICKAXE));
    }

    private int countCoal() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.COAL));
    }

    private int countRawIron() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.RAW_IRON));
    }

    private int countIronIngots() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.IRON_INGOT));
    }

    private int countIronPickaxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.IRON_PICKAXE));
    }

    private int countTorches() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.TORCH));
    }

    private int emptyInventorySlots() {
        int empty = 0;
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            if (this.friend.getFriendInventory().getItem(slot).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    private boolean ensureCraftingStation(ServerLevel level, WorkStep step) {
        if (this.craftingStationTarget == null || !this.isCraftingTableAt(level, this.craftingStationTarget)) {
            this.craftingStationTarget = this.findNearbyCraftingTable(level, 12).orElse(null);
        }
        if (this.craftingStationTarget == null) {
            return false;
        }

        if (!this.interaction.canReachBlock(this.craftingStationTarget)) {
            if (this.moveBackAlongResourceExplorePath(step, "crafting table")) {
                return false;
            }
            step.running("moving to crafting table");
            this.sayThrottled("Moving to the crafting table at " + this.formatPos(this.craftingStationTarget) + ".");
            Optional<BlockPos> approach = this.findStandPositionNearBlock(level, this.craftingStationTarget);
            if (approach.isEmpty()) {
                this.tickStationConstructionRecovery(level, step, this.craftingStationTarget, "crafting table");
                return false;
            }
            BlockPos approachTarget = approach.get();
            if (this.isExpeditionMoveStalled(approachTarget, "moving to crafting table")) {
                this.body.stop();
                this.resetExpeditionMoveWatch();
                this.tickStationConstructionRecovery(level, step, this.craftingStationTarget, "crafting table");
                return false;
            }
            this.resetConstructionPathRecovery();
            this.body.moveTo(approachTarget, TASK_SPEED);
            return false;
        }

        this.resetExpeditionMoveWatch();
        this.resetConstructionPathRecovery();
        this.resourceExploreBreadcrumbs = new ArrayList<>();
        this.body.stop();
        return true;
    }

    private boolean ensureFurnaceStation(ServerLevel level, WorkStep step) {
        if (this.furnaceStationTarget == null || !this.isFurnaceAt(level, this.furnaceStationTarget)) {
            this.furnaceStationTarget = this.findNearbyFurnace(level, 12).orElse(null);
        }
        if (this.furnaceStationTarget == null) {
            step.running("waiting for furnace");
            return false;
        }
        if (!this.interaction.canReachBlock(this.furnaceStationTarget)) {
            if (this.moveBackAlongResourceExplorePath(step, "furnace")) {
                return false;
            }
            step.running("moving to furnace");
            this.sayThrottled("Moving to the furnace at " + this.formatPos(this.furnaceStationTarget) + ".");
            Optional<BlockPos> approach = this.findStandPositionNearBlock(level, this.furnaceStationTarget);
            if (approach.isEmpty()) {
                this.tickStationConstructionRecovery(level, step, this.furnaceStationTarget, "furnace");
                return false;
            }
            BlockPos approachTarget = approach.get();
            if (this.isExpeditionMoveStalled(approachTarget, "moving to furnace")) {
                this.body.stop();
                this.resetExpeditionMoveWatch();
                this.tickStationConstructionRecovery(level, step, this.furnaceStationTarget, "furnace");
                return false;
            }
            this.resetConstructionPathRecovery();
            this.body.moveTo(approachTarget, TASK_SPEED);
            return false;
        }
        this.resetExpeditionMoveWatch();
        this.resetConstructionPathRecovery();
        this.resourceExploreBreadcrumbs = new ArrayList<>();
        this.body.stop();
        return true;
    }

    private void tickStationConstructionRecovery(ServerLevel level, WorkStep step, BlockPos station, String label) {
        Optional<BlockPos> destination = this.findConstructionStandPositionNearBlock(level, station);
        if (destination.isEmpty()) {
            step.failed("no construction destination for " + label);
            this.friend.getFriendBrain().failTask("I cannot find a safe place to build a route back to the " + label + ".");
            return;
        }

        ConstructionTravelResult result = this.tickConstructionTravel(level, destination.get(), label);
        switch (result) {
            case WORKING -> {
                step.running("constructing route to " + label);
                this.sayThrottled("Ordinary pathfinding failed. I am digging or repairing a short route to the "
                        + label
                        + ".");
            }
            case COMPLETE -> step.running("construction route to " + label + " completed");
            case FAILED -> {
                step.failed("construction route to " + label + " failed");
                this.friend.getFriendBrain().failTask("I cannot safely construct a route back to the " + label + ".");
            }
        }
    }

    private ConstructionTravelResult tickConstructionTravel(ServerLevel level, BlockPos destination, String label) {
        if (destination == null) {
            this.resetConstructionPathRecovery();
            return ConstructionTravelResult.FAILED;
        }
        if (this.friend.blockPosition().equals(destination)) {
            this.resetConstructionPathRecovery();
            return ConstructionTravelResult.COMPLETE;
        }
        if (this.constructionPathTarget == null || !this.constructionPathTarget.equals(destination)) {
            this.resetConstructionPathRecovery();
            this.constructionPathOrigin = this.friend.blockPosition().immutable();
            this.constructionPathTarget = destination.immutable();
            this.constructionPathLabel = label == null || label.isBlank() ? "destination" : label;
        }

        if (this.constructionPathPlan == null) {
            if (this.constructionPathFuture == null) {
                int repairBlocks = this.countConstructionRepairBlocks();
                ConstructionPathSnapshot snapshot = this.constructionPathLlmPlanner.capture(
                        level,
                        this.constructionPathOrigin,
                        this.constructionPathTarget,
                        repairBlocks
                );
                this.constructionPathFuture = this.constructionPathLlmPlanner.planAsync(this.friend.getUUID(), snapshot);
                this.body.stop();
            }
            if (!this.constructionPathFuture.isDone()) {
                return ConstructionTravelResult.WORKING;
            }

            Optional<ConstructionRoutePlan> llmPlan;
            try {
                llmPlan = this.constructionPathFuture.getNow(Optional.empty());
            } catch (RuntimeException ignored) {
                llmPlan = Optional.empty();
            }
            this.constructionPathPlan = llmPlan
                    .filter(plan -> this.isConstructionPlanUsable(level, plan))
                    .orElseGet(() -> LocalConstructionPathfinder.plan(
                            level,
                            this.friend,
                            this.constructionPathTarget,
                            this.countConstructionRepairBlocks()
                    ).orElse(null));
            if (this.constructionPathPlan == null || !this.isConstructionPlanUsable(level, this.constructionPathPlan)) {
                this.resetConstructionPathRecovery();
                return ConstructionTravelResult.FAILED;
            }
            this.constructionPathStepIndex = 0;
        }

        if (this.constructionPathStepIndex >= this.constructionPathPlan.steps.size()) {
            ConstructionTravelResult result = this.friend.blockPosition().equals(this.constructionPathTarget)
                    ? ConstructionTravelResult.COMPLETE
                    : ConstructionTravelResult.FAILED;
            this.resetConstructionPathRecovery();
            return result;
        }

        BlockPos current = this.friend.blockPosition().immutable();
        BlockPos nextFeet = this.constructionPathPlan.steps
                .get(this.constructionPathStepIndex)
                .absoluteFrom(this.constructionPathOrigin);
        if (current.equals(nextFeet)) {
            this.constructionPathStepIndex++;
            this.constructionPathLastMovePos = current;
            this.constructionPathMoveStallTicks = 0;
            return ConstructionTravelResult.WORKING;
        }

        Optional<LocalConstructionPathfinder.Transition> inspected = LocalConstructionPathfinder.inspectTransition(
                level,
                current,
                nextFeet,
                this.countConstructionRepairBlocks()
        );
        if (inspected.isEmpty()) {
            this.resetConstructionPathRecovery();
            return ConstructionTravelResult.FAILED;
        }

        LocalConstructionPathfinder.Transition transition = inspected.get();
        if (transition.floorToPlace() != null) {
            Block block = this.expeditionFloorRepairBlockToPlace(this.friend.getCurrentTask());
            if (block == null || !this.interaction.canReachBlock(transition.floorToPlace())) {
                this.resetConstructionPathRecovery();
                return ConstructionTravelResult.FAILED;
            }
            this.body.stop();
            if (!this.interaction.placeBlock(
                    level,
                    transition.floorToPlace(),
                    block,
                    stack -> this.isMatchingFloorRepairBlock(stack, block)
            )) {
                this.resetConstructionPathRecovery();
                return ConstructionTravelResult.FAILED;
            }
            return ConstructionTravelResult.WORKING;
        }

        for (BlockPos blocker : transition.blockers()) {
            if (this.isPassageSpaceClear(level, blocker)) {
                continue;
            }
            if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                    || !this.interaction.canReachBlock(blocker)) {
                this.resetConstructionPathRecovery();
                return ConstructionTravelResult.FAILED;
            }
            this.body.stop();
            SurvivalWorldInteractor.BreakResult result = this.interaction.tickBreakBlock(level, blocker);
            if (result == SurvivalWorldInteractor.BreakResult.FAILED
                    || result == SurvivalWorldInteractor.BreakResult.NOT_IN_REACH) {
                this.resetConstructionPathRecovery();
                return ConstructionTravelResult.FAILED;
            }
            return ConstructionTravelResult.WORKING;
        }

        if (this.isConstructionMoveStalled(current)) {
            this.resetConstructionPathRecovery();
            return ConstructionTravelResult.FAILED;
        }
        this.body.moveToNearby(nextFeet, TASK_SPEED);
        return ConstructionTravelResult.WORKING;
    }

    private boolean isConstructionPlanUsable(ServerLevel level, ConstructionRoutePlan plan) {
        if (plan == null || this.constructionPathOrigin == null || this.constructionPathTarget == null) {
            return false;
        }
        plan.normalize();
        if (plan.steps.isEmpty()) {
            return false;
        }

        BlockPos current = this.constructionPathOrigin;
        int repairBlocks = this.countConstructionRepairBlocks();
        for (ConstructionRoutePlan.Step step : plan.steps) {
            BlockPos next = step.absoluteFrom(this.constructionPathOrigin);
            Optional<LocalConstructionPathfinder.Transition> transition = LocalConstructionPathfinder.inspectTransition(
                    level,
                    current,
                    next,
                    repairBlocks
            );
            if (transition.isEmpty()) {
                return false;
            }
            if (transition.get().floorToPlace() != null) {
                repairBlocks--;
            }
            current = next;
        }
        return current.equals(this.constructionPathTarget);
    }

    private boolean isConstructionMoveStalled(BlockPos current) {
        if (this.constructionPathLastMovePos == null || !this.constructionPathLastMovePos.equals(current)) {
            this.constructionPathLastMovePos = current;
            this.constructionPathMoveStallTicks = 0;
            return false;
        }
        this.constructionPathMoveStallTicks++;
        return this.constructionPathMoveStallTicks >= CONSTRUCTION_MOVE_STALL_TICKS;
    }

    private void resetConstructionPathRecovery() {
        this.constructionPathFuture = null;
        this.constructionPathPlan = null;
        this.constructionPathOrigin = null;
        this.constructionPathTarget = null;
        this.constructionPathLastMovePos = null;
        this.constructionPathLabel = "none";
        this.constructionPathStepIndex = 0;
        this.constructionPathMoveStallTicks = 0;
        this.interaction.cancelBreakBlock();
    }

    private Optional<BlockPos> findConstructionStandPositionNearBlock(ServerLevel level, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos candidate = target.offset(x, y, z);
                    if (candidate.equals(target)
                            || candidate.below().equals(target)
                            || !this.canStandAt(level, candidate)
                            || !this.interaction.canReachBlockFrom(candidate, target)) {
                        continue;
                    }
                    double distance = this.friend.blockPosition().distSqr(candidate);
                    if (distance < bestDistance) {
                        best = candidate.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private String constructionPathSummary() {
        if (this.constructionPathTarget == null) {
            return "idle";
        }
        String source = this.constructionPathPlan == null
                ? (this.constructionPathFuture == null ? "planning" : "waiting_llm_or_fallback")
                : this.constructionPathPlan.source;
        int stepCount = this.constructionPathPlan == null ? 0 : this.constructionPathPlan.steps.size();
        return this.constructionPathLabel
                + "@"
                + this.formatPos(this.constructionPathTarget)
                + ",source="
                + source
                + ",step="
                + this.constructionPathStepIndex
                + "/"
                + stepCount
                + ",stall="
                + this.constructionPathMoveStallTicks;
    }

    private Optional<BlockPos> findNearbyCraftingTable(ServerLevel level, int radius) {
        BlockPos center = this.friend.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -3; y <= 3; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!this.isCraftingTableAt(level, cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isCraftingTableAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
    }

    private Optional<BlockPos> findNearbyFurnace(ServerLevel level, int radius) {
        return this.findNearestBlock(level, radius, pos -> this.isFurnaceAt(level, pos));
    }

    private boolean isFurnaceAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.FURNACE)
                || level.getBlockState(pos).is(Blocks.BLAST_FURNACE);
    }

    private boolean isRegularFurnaceAt(ServerLevel level, BlockPos pos) {
        return pos != null && level.getBlockState(pos).is(Blocks.FURNACE);
    }

    private Optional<BlockPos> findNearbySupplyFurnace(ServerLevel level, int radius) {
        if (this.furnaceStationTarget != null && this.isFurnaceAt(level, this.furnaceStationTarget)) {
            return Optional.of(this.furnaceStationTarget);
        }
        BlockPos center = this.expeditionSupplyChest != null
                ? this.expeditionSupplyChest
                : (this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -2; y <= 2; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!this.isFurnaceAt(level, cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> findNearbyRegularSupplyFurnace(ServerLevel level, int radius) {
        if (this.furnaceStationTarget != null && this.isRegularFurnaceAt(level, this.furnaceStationTarget)) {
            return Optional.of(this.furnaceStationTarget);
        }
        BlockPos center = this.expeditionSupplyChest != null
                ? this.expeditionSupplyChest
                : (this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -2; y <= 2; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!this.isRegularFurnaceAt(level, cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> findSupplyFurnacePlacement(ServerLevel level) {
        BlockPos center = this.expeditionSupplyChest != null
                ? this.expeditionSupplyChest
                : (this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    for (int y = -1; y <= 1; y++) {
                        BlockPos candidate = center.offset(x, y, z);
                        if (!this.canPlaceBlockAt(level, candidate)) {
                            continue;
                        }
                        double distance = this.friend.blockPosition().distSqr(candidate);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate.immutable();
                        }
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> findNearbySupplyChest(ServerLevel level, int radius) {
        if (this.expeditionSupplyChest != null && this.isChestAt(level, this.expeditionSupplyChest)) {
            return Optional.of(this.expeditionSupplyChest);
        }
        BlockPos center = this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -2; y <= 2; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!this.isChestAt(level, cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isChestAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.CHEST);
    }

    private boolean collectSupplyFurnaceOutputIfNearby(ServerLevel level, FriendTask task) {
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION || this.expeditionResupplyActive) {
            return false;
        }
        BlockPos stationCenter = this.expeditionSupplyChest != null
                ? this.expeditionSupplyChest
                : this.expeditionSupplyPoint;
        if (stationCenter == null || this.friend.blockPosition().distSqr(stationCenter) > 144.0D) {
            return false;
        }

        Optional<BlockPos> chestPos = this.findNearbySupplyChest(level, 8);
        if (chestPos.isEmpty() || !(level.getBlockEntity(chestPos.get()) instanceof Container chest)) {
            return false;
        }
        this.expeditionSupplyChest = chestPos.get();

        Optional<BlockPos> furnacePos = this.findNearbySupplyFurnace(level, 8);
        if (furnacePos.isEmpty() || !this.supplyFurnaceHasOutput(level, furnacePos.get())) {
            return false;
        }
        this.furnaceStationTarget = furnacePos.get();

        if (!this.interaction.canReachBlock(this.furnaceStationTarget)
                || !this.interaction.canReachBlock(this.expeditionSupplyChest)) {
            this.setExpeditionSupplyStatus("moving to collect supply furnace output");
            BlockPos approachTarget = this.findStandPositionNearBlock(level, this.furnaceStationTarget)
                    .orElse(this.furnaceStationTarget);
            this.body.moveTo(approachTarget, TASK_SPEED);
            return true;
        }

        this.body.stop();
        return this.collectSupplyFurnaceOutputToChest(level, task, chest);
    }

    private boolean supplyFurnaceHasOutput(ServerLevel level, BlockPos furnacePos) {
        return this.isFurnaceAt(level, furnacePos)
                && level.getBlockEntity(furnacePos) instanceof Container furnace
                && !furnace.getItem(2).isEmpty();
    }

    private boolean collectSupplyFurnaceOutputToChest(ServerLevel level, FriendTask task, Container chest) {
        if (this.furnaceStationTarget == null
                || !this.isFurnaceAt(level, this.furnaceStationTarget)
                || !(level.getBlockEntity(this.furnaceStationTarget) instanceof Container furnace)) {
            return false;
        }
        ItemStack output = furnace.getItem(2);
        if (output.isEmpty()) {
            return false;
        }

        ItemStack toMove = output.copy();
        ItemStack remainder = this.insertIntoContainer(chest, toMove);
        int moved = output.getCount() - remainder.getCount();
        if (moved <= 0) {
            this.setExpeditionSupplyStatus("blocked: supply chest full");
            return false;
        }

        String label = output.getHoverName().getString();
        output.shrink(moved);
        if (output.isEmpty()) {
            furnace.setItem(2, ItemStack.EMPTY);
        }
        furnace.setChanged();
        chest.setChanged();
        level.sendBlockUpdated(this.furnaceStationTarget, level.getBlockState(this.furnaceStationTarget), level.getBlockState(this.furnaceStationTarget), 3);
        if (this.expeditionSupplyChest != null) {
            level.sendBlockUpdated(this.expeditionSupplyChest, level.getBlockState(this.expeditionSupplyChest), level.getBlockState(this.expeditionSupplyChest), 3);
        }
        this.setExpeditionSupplyStatus("collected supply furnace output");
        this.rememberPortableNote(task, "collected supply furnace output "
                + label
                + " x"
                + moved
                + " into supply chest at "
                + this.formatPos(this.expeditionSupplyChest));
        return true;
    }

    private ResupplyResult resupplyAtSupplyChest(ServerLevel level, FriendTask task) {
        this.setExpeditionSupplyStatus("finding supply chest");
        Optional<BlockPos> chestPos = this.findNearbySupplyChest(level, 8);
        if (chestPos.isEmpty() && this.hasChest()) {
            Optional<BlockPos> placement = this.findSupplyChestPlacement(level, task);
            if (placement.isPresent()) {
                this.setExpeditionSupplyStatus("placing supply chest");
                PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                        level,
                        placement.get(),
                        Blocks.CHEST,
                        stack -> stack.is(Items.CHEST),
                        () -> this.findStandPositionNearBlock(level, placement.get()),
                        TASK_SPEED
                );
                if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
                    return ResupplyResult.WORKING;
                }
                if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
                    this.expeditionSupplyChest = placement.get();
                    this.rememberExpeditionSupplyChest(task, this.expeditionSupplyChest, "placed emergency supply chest");
                    chestPos = Optional.of(placement.get());
                }
            }
        }
        if (chestPos.isEmpty()) {
            this.setExpeditionSupplyStatus("blocked: no supply chest");
            return ResupplyResult.FAILED;
        }
        this.expeditionSupplyChest = chestPos.get();
        this.setExpeditionSupplyStatus("using supply chest");
        if (!(level.getBlockEntity(chestPos.get()) instanceof Container chest)) {
            this.setExpeditionSupplyStatus("blocked: supply chest unavailable");
            return ResupplyResult.FAILED;
        }

        SupplySmeltResult smeltResult = this.processSupplyStationSmelting(level, task, chest);
        if (smeltResult == SupplySmeltResult.WORKING) {
            return ResupplyResult.WORKING;
        }

        boolean movedAny = false;
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (stack.isEmpty() || this.shouldKeepForExpedition(stack, task)) {
                continue;
            }
            int original = stack.getCount();
            ItemStack remainder = this.insertIntoContainer(chest, stack.copy());
            int moved = original - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            this.setExpeditionSupplyStatus("unloading inventory");
            stack.shrink(moved);
            if (stack.isEmpty()) {
                this.friend.getFriendInventory().setItem(slot, ItemStack.EMPTY);
            }
            movedAny = true;
        }
        if (movedAny) {
            this.friend.getFriendInventory().setChanged();
            chest.setChanged();
            this.rememberPortableNote(task, "expedition unloaded non-essential inventory into supply chest at "
                    + this.formatPos(this.expeditionSupplyChest)
                    + "; freeSlots="
                    + this.emptyInventorySlots());
            this.rememberExpeditionResupplied(task);
        }
        if (this.hasStorableExpeditionOverflow(task)
                && !this.canContainerAcceptStorableExpeditionOverflow(chest, task)) {
            ResupplyResult expansion = this.placeAdditionalSupplyChest(level, task, chest);
            if (expansion == ResupplyResult.WORKING || expansion == ResupplyResult.COMPLETE) {
                return ResupplyResult.WORKING;
            }
        }
        SupplyRestockResult restockResult = this.restockExpeditionSuppliesFromChest(level, task, chest);
        if (restockResult == SupplyRestockResult.WORKING) {
            return ResupplyResult.WORKING;
        }
        if (smeltResult == SupplySmeltResult.DONE) {
            this.setExpeditionSupplyStatus("smelting complete");
        } else if (restockResult == SupplyRestockResult.RESTOCKED) {
            this.setExpeditionSupplyStatus("restocked expedition supplies");
        } else if (smeltResult == SupplySmeltResult.SKIPPED) {
            this.setExpeditionSupplyStatus("storing raw materials");
        } else if (!movedAny) {
            this.setExpeditionSupplyStatus(this.emptyInventorySlots() > 1 ? "supply ready" : "blocked: supply chest full");
        }
        return (movedAny || restockResult == SupplyRestockResult.RESTOCKED || smeltResult == SupplySmeltResult.DONE || this.emptyInventorySlots() > 1)
                && this.emptyInventorySlots() > 1
                ? ResupplyResult.COMPLETE
                : ResupplyResult.FAILED;
    }

    private ResupplyResult placeAdditionalSupplyChest(ServerLevel level, FriendTask task, Container chest) {
        if (!this.hasChest()) {
            SupplyCraftResult craftResult = this.craftSupplyChestFromChest(level, task, chest);
            if (craftResult == SupplyCraftResult.WORKING) {
                return ResupplyResult.WORKING;
            }
            if (!this.hasChest()) {
                this.setExpeditionSupplyStatus("blocked: no extra supply chest");
                return ResupplyResult.FAILED;
            }
        }
        Optional<BlockPos> placement = this.findSupplyChestPlacement(level, task);
        if (placement.isEmpty()) {
            this.setExpeditionSupplyStatus("blocked: no room for extra supply chest");
            return ResupplyResult.FAILED;
        }
        this.setExpeditionSupplyStatus("placing extra supply chest");
        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                level,
                placement.get(),
                Blocks.CHEST,
                stack -> stack.is(Items.CHEST),
                () -> this.findStandPositionNearBlock(level, placement.get()),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            return ResupplyResult.WORKING;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.expeditionSupplyChest = placement.get();
            this.setExpeditionSupplyStatus("expanded supply storage");
            this.rememberPortableNote(task, "expedition placed extra supply chest at "
                    + this.formatPos(this.expeditionSupplyChest));
            this.rememberExpeditionSupplyChest(task, this.expeditionSupplyChest, "placed extra supply chest");
            return ResupplyResult.COMPLETE;
        }
        this.setExpeditionSupplyStatus("blocked: extra supply chest placement failed");
        return ResupplyResult.FAILED;
    }

    private SupplyRestockResult restockExpeditionSuppliesFromChest(ServerLevel level, FriendTask task, Container chest) {
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION) {
            return SupplyRestockResult.NONE;
        }

        boolean neededTorches = this.countTorches() <= EXPEDITION_LOW_TORCH_THRESHOLD;
        boolean neededTool = !this.hasUsableExpeditionPickaxe(task);
        boolean neededFood = this.friend.getHungerProvider().getFoodLevel() <= EXPEDITION_LOW_FOOD_THRESHOLD
                && this.carriedFoodItems() <= 0;
        int movedTorches = 0;
        while (this.countTorches() < EXPEDITION_RESTOCK_TORCH_TARGET
                && this.moveOneMatchingFromContainer(chest, stack -> stack.is(Items.TORCH))) {
            movedTorches++;
        }
        int craftedTorches = this.craftExpeditionTorchesFromSupplyChest(level, chest);
        boolean movedTool = neededTool && this.moveOneMatchingFromContainer(
                chest,
                stack -> this.isUsableExpeditionPickaxe(task, stack)
        );
        SupplyCraftResult toolCraftResult = neededTool && !movedTool
                ? this.craftExpeditionPickaxeFromSupplyChest(level, task, chest)
                : SupplyCraftResult.NONE;
        boolean craftedTool = toolCraftResult == SupplyCraftResult.CRAFTED;
        boolean workingToolCraft = toolCraftResult == SupplyCraftResult.WORKING;
        int movedFood = 0;
        while (this.carriedFoodItems() < EXPEDITION_RESTOCK_FOOD_ITEMS
                && this.moveOneMatchingFromContainer(chest, this::isExpeditionFood)) {
            movedFood++;
        }

        if (this.countTorches() > EXPEDITION_LOW_TORCH_THRESHOLD) {
            this.expeditionTorchRestockUnavailable = false;
        } else if (neededTorches
                && this.countContainerItems(chest, stack -> stack.is(Items.TORCH)) <= 0
                && !this.canCraftExpeditionTorchesFromSupplyChest(chest)) {
            this.expeditionTorchRestockUnavailable = true;
        }

        if (this.hasUsableExpeditionPickaxe(task)) {
            this.expeditionToolRestockUnavailable = false;
        } else if (neededTool
                && this.countContainerItems(chest, stack -> this.isUsableExpeditionPickaxe(task, stack)) <= 0
                && !this.canCraftExpeditionPickaxeFromSupplyChest(level, task, chest)
                && !workingToolCraft) {
            this.expeditionToolRestockUnavailable = true;
        }

        if (this.carriedFoodItems() > 0) {
            this.expeditionFoodRestockUnavailable = false;
        } else if (neededFood && this.countContainerItems(chest, this::isExpeditionFood) <= 0) {
            this.expeditionFoodRestockUnavailable = true;
        }

        if (movedTorches <= 0 && craftedTorches <= 0 && !movedTool && !craftedTool && movedFood <= 0) {
            return workingToolCraft ? SupplyRestockResult.WORKING : SupplyRestockResult.NONE;
        }

        this.rememberPortableNote(task, "expedition restocked supplies from chest at "
                + this.formatPos(this.expeditionSupplyChest)
                + ": torches="
                + movedTorches
                + ", craftedTorches="
                + craftedTorches
                + ", pickaxe="
                + movedTool
                + ", craftedPickaxe="
                + craftedTool
                + ", food="
                + movedFood);
        this.rememberExpeditionResupplied(task);
        return workingToolCraft ? SupplyRestockResult.WORKING : SupplyRestockResult.RESTOCKED;
    }

    private int craftExpeditionTorchesFromSupplyChest(ServerLevel level, Container chest) {
        int crafted = 0;
        Predicate<ItemStack> torchMatcher = this.recipeMatcherFor(WorkflowFactory.TORCH);
        while (this.countTorches() < EXPEDITION_RESTOCK_TORCH_TARGET
                && this.countContainerItems(chest, this::isTorchCraftingFuel) > 0
                && this.ensureSupplyChestSticks(level, chest, 1)) {
            if (!this.moveOneMatchingFromContainer(chest, this::isTorchCraftingFuel)
                    || !this.moveOneMatchingFromContainer(chest, stack -> stack.is(Items.STICK))) {
                break;
            }

            int before = this.countTorches();
            CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOne(level, torchMatcher, 2);
            if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
                break;
            }
            crafted += Math.max(0, this.countTorches() - before);
        }
        return crafted;
    }

    private boolean canCraftExpeditionTorchesFromSupplyChest(Container chest) {
        return this.countContainerItems(chest, this::isTorchCraftingFuel) > 0
                && (this.countContainerItems(chest, stack -> stack.is(Items.STICK)) > 0
                || this.potentialSupplyChestPlanks(chest) >= 2);
    }

    private boolean ensureSupplyChestSticks(ServerLevel level, Container chest, int amount) {
        int required = Math.max(1, amount);
        if (this.countContainerItems(chest, stack -> stack.is(Items.STICK)) >= required) {
            return true;
        }
        while (this.countContainerItems(chest, stack -> stack.is(Items.STICK)) < required) {
            if (!this.ensureSupplyChestPlanks(level, chest, 2)) {
                return false;
            }
            int movedPlanks = 0;
            while (movedPlanks < 2 && this.moveOneMatchingFromContainer(chest, stack -> stack.is(ItemTags.PLANKS))) {
                movedPlanks++;
            }
            if (movedPlanks < 2) {
                return false;
            }
            CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOne(
                    level,
                    this.recipeMatcherFor(WorkflowFactory.STICKS),
                    2
            );
            if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
                this.unloadSupplyCraftingRemainders(chest);
                return false;
            }
            this.unloadMatchingInventoryItems(chest, stack -> stack.is(Items.STICK));
        }
        return this.countContainerItems(chest, stack -> stack.is(Items.STICK)) >= required;
    }

    private boolean ensureSupplyChestPlanks(ServerLevel level, Container chest, int amount) {
        int required = Math.max(1, amount);
        if (this.countContainerItems(chest, stack -> stack.is(ItemTags.PLANKS)) >= required) {
            return true;
        }
        while (this.countContainerItems(chest, stack -> stack.is(ItemTags.PLANKS)) < required
                && this.countContainerItems(chest, stack -> stack.is(ItemTags.LOGS)) > 0) {
            if (!this.moveOneMatchingFromContainer(chest, stack -> stack.is(ItemTags.LOGS))) {
                return false;
            }
            CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOne(
                    level,
                    stack -> stack.is(ItemTags.PLANKS),
                    2
            );
            if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
                this.unloadSupplyCraftingRemainders(chest);
                return false;
            }
            this.unloadMatchingInventoryItems(chest, stack -> stack.is(ItemTags.PLANKS));
        }
        return this.countContainerItems(chest, stack -> stack.is(ItemTags.PLANKS)) >= required;
    }

    private int potentialSupplyChestPlanks(Container chest) {
        if (chest == null) {
            return 0;
        }
        return this.countContainerItems(chest, stack -> stack.is(ItemTags.PLANKS))
                + this.countContainerItems(chest, stack -> stack.is(ItemTags.LOGS)) * 4;
    }

    private SupplyCraftResult craftExpeditionPickaxeFromSupplyChest(ServerLevel level, FriendTask task, Container chest) {
        if (!this.canCraftExpeditionPickaxeFromSupplyChest(level, task, chest)) {
            return SupplyCraftResult.NONE;
        }
        SupplyStationResult stationResult = this.ensureSupplyCraftingTable(level, task, chest);
        if (stationResult == SupplyStationResult.WORKING) {
            return SupplyCraftResult.WORKING;
        }
        if (stationResult != SupplyStationResult.READY) {
            return SupplyCraftResult.NONE;
        }

        MiningTargetRegistry.ToolRequirement requirement = MiningTargetRegistry.find(task.target())
                .map(MiningTargetRegistry.MiningTarget::toolRequirement)
                .orElse(MiningTargetRegistry.ToolRequirement.STONE_PICKAXE);
        if (requirement != MiningTargetRegistry.ToolRequirement.IRON_PICKAXE
                && this.craftPickaxeFromSupplyChest(
                        level,
                        task,
                        chest,
                        WorkflowFactory.STONE_PICKAXE,
                        stack -> stack.is(Items.COBBLESTONE),
                        MiningTargetRegistry.ToolRequirement.STONE_PICKAXE
                )) {
            return SupplyCraftResult.CRAFTED;
        }
        return this.craftPickaxeFromSupplyChest(
                level,
                task,
                chest,
                WorkflowFactory.IRON_PICKAXE,
                stack -> stack.is(Items.IRON_INGOT),
                MiningTargetRegistry.ToolRequirement.IRON_PICKAXE
        ) ? SupplyCraftResult.CRAFTED : SupplyCraftResult.NONE;
    }

    private boolean craftPickaxeFromSupplyChest(
            ServerLevel level,
            FriendTask task,
            Container chest,
            String pickaxeTarget,
            Predicate<ItemStack> headMaterialMatcher,
            MiningTargetRegistry.ToolRequirement craftedRequirement
    ) {
        if (!this.canCraftSupplyPickaxe(chest, headMaterialMatcher)) {
            return false;
        }
        if (!this.ensureSupplyChestSticks(level, chest, 2)) {
            return false;
        }
        if (!this.moveMatchingItemsFromContainer(chest, headMaterialMatcher, 3)
                || !this.moveMatchingItemsFromContainer(chest, stack -> stack.is(Items.STICK), 2)) {
            this.unloadSupplyCraftingRemainders(chest);
            return false;
        }

        CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOneWithBestContext(
                level,
                this.recipeMatcherFor(pickaxeTarget),
                this.craftingStationTarget
        );
        if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
            this.unloadSupplyCraftingRemainders(chest);
            return false;
        }

        if (this.hasUsableExpeditionPickaxe(task)) {
            this.rememberPortableNote(task, "crafted supply "
                    + craftedRequirement.name().toLowerCase()
                    + " at "
                    + this.formatPos(this.craftingStationTarget));
            return true;
        }
        return false;
    }

    private boolean canCraftSupplyPickaxe(Container chest, Predicate<ItemStack> headMaterialMatcher) {
        return this.countContainerItems(chest, headMaterialMatcher) >= 3
                && (this.countContainerItems(chest, stack -> stack.is(Items.STICK)) >= 2
                || this.potentialSupplyChestPlanks(chest) >= 2);
    }

    private SupplyCraftResult craftSupplyFurnaceFromChest(ServerLevel level, FriendTask task, Container chest) {
        if (!this.canCraftSupplyFurnaceFromChest(level, chest)) {
            return SupplyCraftResult.NONE;
        }
        SupplyStationResult stationResult = this.ensureSupplyCraftingTable(level, task, chest);
        if (stationResult == SupplyStationResult.WORKING) {
            return SupplyCraftResult.WORKING;
        }
        if (stationResult != SupplyStationResult.READY) {
            return SupplyCraftResult.NONE;
        }

        if (!this.moveMatchingItemsFromContainer(chest, stack -> stack.is(Items.COBBLESTONE), 8)) {
            this.unloadSupplyCraftingRemainders(chest);
            return SupplyCraftResult.NONE;
        }

        CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOneWithBestContext(
                level,
                this.recipeMatcherFor(WorkflowFactory.FURNACE),
                this.craftingStationTarget
        );
        if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
            this.unloadSupplyCraftingRemainders(chest);
            return SupplyCraftResult.NONE;
        }
        if (!this.hasFurnace()) {
            return SupplyCraftResult.NONE;
        }

        this.setExpeditionSupplyStatus("crafted supply furnace");
        this.rememberPortableNote(task, "crafted supply furnace from chest cobblestone at "
                + this.formatPos(this.craftingStationTarget));
        return SupplyCraftResult.CRAFTED;
    }

    private boolean canCraftSupplyFurnaceFromChest(ServerLevel level, Container chest) {
        if (chest == null || this.countContainerItems(chest, stack -> stack.is(Items.COBBLESTONE)) < 8) {
            return false;
        }
        return this.hasSupplyCraftingTable(level)
                || this.hasCraftingTable()
                || this.countContainerItems(chest, stack -> stack.is(Items.CRAFTING_TABLE)) > 0
                || this.potentialSupplyChestPlanks(chest) >= 4;
    }

    private SupplyCraftResult craftSupplyChestFromChest(ServerLevel level, FriendTask task, Container chest) {
        if (!this.canCraftSupplyChestFromChest(level, chest)) {
            return SupplyCraftResult.NONE;
        }
        SupplyStationResult stationResult = this.ensureSupplyCraftingTable(level, task, chest);
        if (stationResult == SupplyStationResult.WORKING) {
            return SupplyCraftResult.WORKING;
        }
        if (stationResult != SupplyStationResult.READY) {
            return SupplyCraftResult.NONE;
        }

        if (!this.ensureSupplyChestPlanks(level, chest, 8)
                || !this.moveMatchingItemsFromContainer(chest, stack -> stack.is(ItemTags.PLANKS), 8)) {
            this.unloadSupplyCraftingRemainders(chest);
            return SupplyCraftResult.NONE;
        }
        CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOneWithBestContext(
                level,
                this.recipeMatcherFor(WorkflowFactory.CHEST),
                this.craftingStationTarget
        );
        if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
            this.unloadSupplyCraftingRemainders(chest);
            return SupplyCraftResult.NONE;
        }
        if (!this.hasChest()) {
            return SupplyCraftResult.NONE;
        }

        this.setExpeditionSupplyStatus("crafted extra supply chest");
        this.rememberPortableNote(task, "crafted extra supply chest from chest planks at "
                + this.formatPos(this.craftingStationTarget));
        return SupplyCraftResult.CRAFTED;
    }

    private boolean canCraftSupplyChestFromChest(ServerLevel level, Container chest) {
        if (chest == null) {
            return false;
        }
        int planks = this.potentialSupplyChestPlanks(chest);
        boolean hasStationPath = this.hasSupplyCraftingTable(level)
                || this.hasCraftingTable()
                || this.countContainerItems(chest, stack -> stack.is(Items.CRAFTING_TABLE)) > 0;
        return hasStationPath ? planks >= 8 : planks >= 12;
    }

    private boolean canCraftExpeditionPickaxeFromSupplyChest(ServerLevel level, FriendTask task, Container chest) {
        MiningTargetRegistry.ToolRequirement requirement = MiningTargetRegistry.find(task.target())
                .map(MiningTargetRegistry.MiningTarget::toolRequirement)
                .orElse(MiningTargetRegistry.ToolRequirement.STONE_PICKAXE);
        int planks = this.potentialSupplyChestPlanks(chest);
        boolean hasStationPath = this.hasSupplyCraftingTable(level)
                || this.hasCraftingTable()
                || this.countContainerItems(chest, stack -> stack.is(Items.CRAFTING_TABLE)) > 0
                || planks >= 4;
        if (!hasStationPath) {
            return false;
        }
        boolean mustCraftStationFromPlanks = !this.hasSupplyCraftingTable(level)
                && !this.hasCraftingTable()
                && this.countContainerItems(chest, stack -> stack.is(Items.CRAFTING_TABLE)) <= 0;
        int planksAvailableForSticks = mustCraftStationFromPlanks ? Math.max(0, planks - 4) : planks;
        boolean canMakeSticks = this.countContainerItems(chest, stack -> stack.is(Items.STICK)) >= 2
                || planksAvailableForSticks >= 2;
        if (requirement != MiningTargetRegistry.ToolRequirement.IRON_PICKAXE
                && this.countContainerItems(chest, stack -> stack.is(Items.COBBLESTONE)) >= 3
                && canMakeSticks) {
            return true;
        }
        return this.countContainerItems(chest, stack -> stack.is(Items.IRON_INGOT)) >= 3 && canMakeSticks;
    }

    private boolean hasSupplyCraftingTable(ServerLevel level) {
        if (this.craftingStationTarget == null || !this.isCraftingTableAt(level, this.craftingStationTarget)) {
            this.craftingStationTarget = this.findNearbyCraftingTable(level, 12).orElse(null);
        }
        return this.craftingStationTarget != null && this.isCraftingTableAt(level, this.craftingStationTarget);
    }

    private SupplyStationResult ensureSupplyCraftingTable(ServerLevel level, FriendTask task, Container chest) {
        if (this.hasSupplyCraftingTable(level)) {
            this.rememberExpeditionSupplyCraftingTable(task, this.craftingStationTarget, "reused supply crafting table");
            return SupplyStationResult.READY;
        }
        if (!this.hasCraftingTable() && this.moveOneMatchingFromContainer(chest, stack -> stack.is(Items.CRAFTING_TABLE))) {
            this.setExpeditionSupplyStatus("pulled supply crafting table");
        }
        if (!this.hasCraftingTable() && this.potentialSupplyChestPlanks(chest) >= 4) {
            if (!this.ensureSupplyChestPlanks(level, chest, 4)
                    || !this.moveMatchingItemsFromContainer(chest, stack -> stack.is(ItemTags.PLANKS), 4)) {
                return SupplyStationResult.UNAVAILABLE;
            }
            CraftingActionAdapter.CraftResult result = this.craftingAdapter.craftOne(
                    level,
                    this.recipeMatcherFor(WorkflowFactory.CRAFTING_TABLE),
                    2
            );
            if (result != CraftingActionAdapter.CraftResult.CRAFTED) {
                this.unloadSupplyCraftingRemainders(chest);
                return SupplyStationResult.UNAVAILABLE;
            }
            this.setExpeditionSupplyStatus("crafted supply crafting table");
            this.rememberPortableNote(task, "crafted supply crafting table from chest planks at "
                    + this.formatPos(this.expeditionSupplyChest));
        }
        if (!this.hasCraftingTable()) {
            return SupplyStationResult.UNAVAILABLE;
        }

        if (this.placeTarget == null || !this.canPlaceCraftingTableAt(level, this.placeTarget)) {
            this.placeTarget = this.findSupplyCraftingTablePlacement(level, task).orElse(null);
        }
        if (this.placeTarget == null) {
            return SupplyStationResult.UNAVAILABLE;
        }

        this.setExpeditionSupplyStatus("placing supply crafting table");
        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                level,
                this.placeTarget,
                Blocks.CRAFTING_TABLE,
                stack -> stack.is(Items.CRAFTING_TABLE),
                () -> this.findStandPositionNearBlock(level, this.placeTarget),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            return SupplyStationResult.WORKING;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.craftingStationTarget = this.placeTarget;
            this.placeTarget = null;
            this.rememberPortableNote(task, "placed supply crafting table at "
                    + this.formatPos(this.craftingStationTarget));
            this.rememberExpeditionSupplyCraftingTable(task, this.craftingStationTarget, "placed supply crafting table");
            return SupplyStationResult.READY;
        }

        this.placeTarget = null;
        return SupplyStationResult.UNAVAILABLE;
    }

    private boolean moveMatchingItemsFromContainer(Container container, Predicate<ItemStack> matcher, int amount) {
        int moved = 0;
        while (moved < amount && this.moveOneMatchingFromContainer(container, matcher)) {
            moved++;
        }
        return moved >= amount;
    }

    private void unloadSupplyCraftingRemainders(Container chest) {
        this.unloadMatchingInventoryItems(chest, stack -> stack.is(Items.STICK)
                || stack.is(Items.COBBLESTONE)
                || stack.is(Items.IRON_INGOT)
                || stack.is(ItemTags.PLANKS)
                || stack.is(ItemTags.LOGS)
                || stack.is(Items.CRAFTING_TABLE));
    }

    private SupplySmeltResult processSupplyStationSmelting(ServerLevel level, FriendTask task, Container chest) {
        Optional<SupplySmeltTarget> target = this.nextSupplySmeltTarget(level, task, chest);
        if (target.isEmpty()) {
            return SupplySmeltResult.NONE;
        }

        SupplySmeltTarget smeltTarget = target.get();
        this.setExpeditionSupplyStatus("preparing supply smelting: " + smeltTarget.label());
        SupplyFurnaceResult furnaceResult = smeltTarget.regularFurnaceOnly()
                ? this.ensureRegularSupplyFurnaceStation(level, task, chest)
                : this.ensureSupplyFurnaceStation(level, task, chest);
        if (furnaceResult == SupplyFurnaceResult.WORKING) {
            return SupplySmeltResult.WORKING;
        }
        if (furnaceResult == SupplyFurnaceResult.UNAVAILABLE) {
            this.setExpeditionSupplyStatus("smelting skipped: no furnace");
            return SupplySmeltResult.SKIPPED;
        }

        if (!this.supplyFurnaceContains(level, smeltTarget)
                && this.friend.countInventoryItems(smeltTarget.inputMatcher()) <= 0
                && !this.moveOneMatchingFromContainer(chest, smeltTarget.inputMatcher())) {
            if (this.countContainerItems(chest, smeltTarget.inputMatcher()) > 0
                    && this.unloadMatchingInventoryItems(chest, stack -> !this.shouldKeepForExpedition(stack, task)) > 0) {
                this.setExpeditionSupplyStatus("making room for smelting input");
                return SupplySmeltResult.WORKING;
            }
            this.setExpeditionSupplyStatus("smelting skipped: missing input");
            return SupplySmeltResult.SKIPPED;
        }

        this.setExpeditionSupplyStatus("smelting " + smeltTarget.label());
        SmeltingActionAdapter.SmeltResult result = this.smeltingAdapter.smeltOneAtFurnace(
                level,
                this.furnaceStationTarget,
                smeltTarget.outputMatcher(),
                this::isSupplyFurnaceFuel
        );
        if (result == SmeltingActionAdapter.SmeltResult.SMELTED) {
            this.setExpeditionSupplyStatus("collecting smelted " + smeltTarget.label());
            this.rememberPortableNote(task, "supply furnace smelted " + smeltTarget.label()
                    + " at "
                    + this.formatPos(this.furnaceStationTarget));
            this.rememberExpeditionSupplySmelted(task, smeltTarget.label());
            this.unloadMatchingInventoryItems(chest, smeltTarget.outputMatcher());
            return this.friend.countInventoryItems(smeltTarget.inputMatcher()) > 0
                    || this.countContainerItems(chest, smeltTarget.inputMatcher()) > 0
                    || this.supplyFurnaceContains(level, smeltTarget)
                    ? SupplySmeltResult.WORKING
                    : SupplySmeltResult.DONE;
        }
        if (result == SmeltingActionAdapter.SmeltResult.WORKING) {
            this.setExpeditionSupplyStatus("waiting for supply furnace");
            this.sayThrottled("Supply furnace is processing " + smeltTarget.label() + ".");
            return SupplySmeltResult.WORKING;
        }
        if (result == SmeltingActionAdapter.SmeltResult.INVENTORY_FULL) {
            this.setExpeditionSupplyStatus("making room for furnace output");
            this.unloadMatchingInventoryItems(chest, stack -> !this.shouldKeepForExpedition(stack, task));
            return SupplySmeltResult.DONE;
        }
        if (result == SmeltingActionAdapter.SmeltResult.MISSING_FUEL) {
            if (this.moveOneMatchingFromContainer(chest, this::isSupplyFurnaceFuel)
                    || (this.unloadMatchingInventoryItems(chest, stack -> !this.shouldKeepForExpedition(stack, task)) > 0
                    && this.moveOneMatchingFromContainer(chest, this::isSupplyFurnaceFuel))) {
                this.setExpeditionSupplyStatus("pulling supply furnace fuel");
                this.rememberPortableNote(task, "supply furnace pulled fuel from supply chest at "
                        + this.formatPos(this.expeditionSupplyChest));
                return SupplySmeltResult.WORKING;
            }
            this.setExpeditionSupplyStatus("smelting skipped: missing fuel");
            this.sayThrottled("I do not have fuel for supply smelting, so I will store the raw ore.");
            return SupplySmeltResult.SKIPPED;
        }
        if (result == SmeltingActionAdapter.SmeltResult.MISSING_INPUT) {
            if (this.moveOneMatchingFromContainer(chest, smeltTarget.inputMatcher())) {
                this.setExpeditionSupplyStatus("pulling supply smelting input");
                this.rememberPortableNote(task, "supply furnace pulled " + smeltTarget.label()
                        + " from supply chest at "
                        + this.formatPos(this.expeditionSupplyChest));
                return SupplySmeltResult.WORKING;
            }
            this.setExpeditionSupplyStatus("smelting skipped: missing input");
            return SupplySmeltResult.NONE;
        }
        this.setExpeditionSupplyStatus("smelting skipped");
        return SupplySmeltResult.SKIPPED;
    }

    private SupplyFurnaceResult ensureSupplyFurnaceStation(ServerLevel level, FriendTask task, Container chest) {
        if (this.furnaceStationTarget != null && this.isFurnaceAt(level, this.furnaceStationTarget)) {
            this.setExpeditionSupplyStatus("using supply furnace");
            return SupplyFurnaceResult.READY;
        }

        Optional<BlockPos> nearbyFurnace = this.findNearbySupplyFurnace(level, 8);
        if (nearbyFurnace.isPresent()) {
            this.furnaceStationTarget = nearbyFurnace.get();
            this.setExpeditionSupplyStatus("reusing supply furnace");
            this.rememberExpeditionFurnaceReady(task, this.furnaceStationTarget, "reused supply furnace");
            return SupplyFurnaceResult.READY;
        }

        if (this.supplyFurnaceBlockToPlace() == null && chest != null) {
            if (this.moveOneMatchingFromContainer(chest, stack -> stack.is(Items.FURNACE))
                    || this.moveOneMatchingFromContainer(chest, stack -> stack.is(Items.BLAST_FURNACE))) {
                this.setExpeditionSupplyStatus("pulled supply furnace from chest");
                this.rememberPortableNote(task, "pulled supply furnace from chest at "
                        + this.formatPos(this.expeditionSupplyChest));
            }
        }
        if (this.supplyFurnaceBlockToPlace() == null) {
            SupplyCraftResult craftResult = this.craftSupplyFurnaceFromChest(level, task, chest);
            if (craftResult == SupplyCraftResult.WORKING) {
                return SupplyFurnaceResult.WORKING;
            }
        }
        Block supplyFurnaceBlock = this.supplyFurnaceBlockToPlace();
        if (supplyFurnaceBlock == null) {
            this.setExpeditionSupplyStatus("smelting skipped: no carried furnace");
            return SupplyFurnaceResult.UNAVAILABLE;
        }
        if (this.placeTarget == null || !this.canPlaceBlockAt(level, this.placeTarget)) {
            this.placeTarget = this.findSupplyFurnacePlacement(level).orElse(null);
        }
        if (this.placeTarget == null) {
            this.setExpeditionSupplyStatus("smelting skipped: no furnace placement");
            return SupplyFurnaceResult.UNAVAILABLE;
        }

        this.setExpeditionSupplyStatus("placing supply furnace");
        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                level,
                this.placeTarget,
                supplyFurnaceBlock,
                stack -> stack.is(supplyFurnaceBlock.asItem()),
                () -> this.findStandPositionNearBlock(level, this.placeTarget),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            this.sayThrottled("Setting up a supply furnace.");
            return SupplyFurnaceResult.WORKING;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.furnaceStationTarget = this.placeTarget;
            this.placeTarget = null;
            this.setExpeditionSupplyStatus("supply furnace ready");
            this.rememberPortableNote(task, "expedition placed supply furnace at " + this.formatPos(this.furnaceStationTarget));
            this.rememberExpeditionFurnaceReady(task, this.furnaceStationTarget, "placed supply furnace");
            return SupplyFurnaceResult.READY;
        }

        this.placeTarget = null;
        this.setExpeditionSupplyStatus("smelting skipped: furnace placement failed");
        return SupplyFurnaceResult.UNAVAILABLE;
    }

    private SupplyFurnaceResult ensureRegularSupplyFurnaceStation(ServerLevel level, FriendTask task, Container chest) {
        if (this.furnaceStationTarget != null && this.isRegularFurnaceAt(level, this.furnaceStationTarget)) {
            this.setExpeditionSupplyStatus("using supply furnace");
            return SupplyFurnaceResult.READY;
        }

        Optional<BlockPos> nearbyFurnace = this.findNearbyRegularSupplyFurnace(level, 8);
        if (nearbyFurnace.isPresent()) {
            this.furnaceStationTarget = nearbyFurnace.get();
            this.setExpeditionSupplyStatus("reusing supply furnace");
            this.rememberExpeditionFurnaceReady(task, this.furnaceStationTarget, "reused regular supply furnace");
            return SupplyFurnaceResult.READY;
        }

        if (!this.hasFurnace() && chest != null && this.moveOneMatchingFromContainer(chest, stack -> stack.is(Items.FURNACE))) {
            this.setExpeditionSupplyStatus("pulled regular supply furnace from chest");
            this.rememberPortableNote(task, "pulled regular supply furnace from chest at "
                    + this.formatPos(this.expeditionSupplyChest));
        }
        if (!this.hasFurnace()) {
            SupplyCraftResult craftResult = this.craftSupplyFurnaceFromChest(level, task, chest);
            if (craftResult == SupplyCraftResult.WORKING) {
                return SupplyFurnaceResult.WORKING;
            }
        }
        if (!this.hasFurnace()) {
            this.setExpeditionSupplyStatus("food smelting skipped: no regular furnace");
            return SupplyFurnaceResult.UNAVAILABLE;
        }
        if (this.placeTarget == null || !this.canPlaceBlockAt(level, this.placeTarget)) {
            this.placeTarget = this.findSupplyFurnacePlacement(level).orElse(null);
        }
        if (this.placeTarget == null) {
            this.setExpeditionSupplyStatus("food smelting skipped: no furnace placement");
            return SupplyFurnaceResult.UNAVAILABLE;
        }

        this.setExpeditionSupplyStatus("placing supply furnace");
        PlaceActionAdapter.PlaceResult placeResult = this.placeAdapter.placeBlock(
                level,
                this.placeTarget,
                Blocks.FURNACE,
                stack -> stack.is(Items.FURNACE),
                () -> this.findStandPositionNearBlock(level, this.placeTarget),
                TASK_SPEED
        );
        if (placeResult == PlaceActionAdapter.PlaceResult.WORKING) {
            this.sayThrottled("Setting up a supply furnace.");
            return SupplyFurnaceResult.WORKING;
        }
        if (placeResult == PlaceActionAdapter.PlaceResult.PLACED) {
            this.furnaceStationTarget = this.placeTarget;
            this.placeTarget = null;
            this.setExpeditionSupplyStatus("supply furnace ready");
            this.rememberPortableNote(task, "expedition placed regular supply furnace at " + this.formatPos(this.furnaceStationTarget));
            this.rememberExpeditionFurnaceReady(task, this.furnaceStationTarget, "placed regular supply furnace");
            return SupplyFurnaceResult.READY;
        }

        this.placeTarget = null;
        this.setExpeditionSupplyStatus("food smelting skipped: furnace placement failed");
        return SupplyFurnaceResult.UNAVAILABLE;
    }

    private Optional<SupplySmeltTarget> nextSupplySmeltTarget(ServerLevel level, FriendTask task, Container chest) {
        SupplySmeltTarget iron = new SupplySmeltTarget(
                "raw iron",
                stack -> stack.is(Items.RAW_IRON) || stack.is(Items.IRON_ORE) || stack.is(Items.DEEPSLATE_IRON_ORE),
                stack -> stack.is(Items.IRON_INGOT),
                false
        );
        if (this.canSupplySmelt(level, task, chest, iron, WorkflowFactory.RAW_IRON)) {
            return Optional.of(iron);
        }

        SupplySmeltTarget gold = new SupplySmeltTarget(
                "raw gold",
                stack -> stack.is(Items.RAW_GOLD)
                        || stack.is(Items.GOLD_ORE)
                        || stack.is(Items.DEEPSLATE_GOLD_ORE)
                        || stack.is(Items.NETHER_GOLD_ORE),
                stack -> stack.is(Items.GOLD_INGOT),
                false
        );
        if (this.canSupplySmelt(level, task, chest, gold, "minecraft:raw_gold")) {
            return Optional.of(gold);
        }

        SupplySmeltTarget copper = new SupplySmeltTarget(
                "raw copper",
                stack -> stack.is(Items.RAW_COPPER) || stack.is(Items.COPPER_ORE) || stack.is(Items.DEEPSLATE_COPPER_ORE),
                stack -> stack.is(Items.COPPER_INGOT),
                false
        );
        if (this.canSupplySmelt(level, task, chest, copper, "minecraft:raw_copper")) {
            return Optional.of(copper);
        }

        SupplySmeltTarget food = new SupplySmeltTarget(
                "food",
                this::isCookableExpeditionFood,
                this::isExpeditionFood,
                true
        );
        if (this.canSupplySmelt(level, task, chest, food, "")) {
            return Optional.of(food);
        }

        SupplySmeltTarget charcoal = new SupplySmeltTarget(
                "charcoal",
                stack -> stack.is(ItemTags.LOGS),
                stack -> stack.is(Items.CHARCOAL),
                true
        );
        if (this.canSupplySmelt(level, task, chest, charcoal, "")) {
            return Optional.of(charcoal);
        }
        return Optional.empty();
    }

    private boolean canSupplySmelt(
            ServerLevel level,
            FriendTask task,
            Container chest,
            SupplySmeltTarget target,
            String protectedResourceId
    ) {
        if (target.regularFurnaceOnly() && !this.hasAvailableRegularSupplyFurnace(level, chest)) {
            return false;
        }
        return !this.matchesTaskTarget(task, protectedResourceId)
                && (this.friend.countInventoryItems(target.inputMatcher()) > 0
                || this.countContainerItems(chest, target.inputMatcher()) > 0
                || this.supplyFurnaceContains(level, target));
    }

    private boolean matchesTaskTarget(FriendTask task, String resourceId) {
        return task != null
                && task.target() != null
                && task.target().equals(resourceId);
    }

    private boolean isTorchCraftingFuel(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    private boolean isSupplyFurnaceFuel(ItemStack stack) {
        return this.isTorchCraftingFuel(stack)
                || stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.PLANKS)
                || stack.is(Items.STICK);
    }

    private boolean hasAvailableRegularSupplyFurnace(ServerLevel level, Container chest) {
        return (this.furnaceStationTarget != null && this.isRegularFurnaceAt(level, this.furnaceStationTarget))
                || this.findNearbyRegularSupplyFurnace(level, 8).isPresent()
                || this.hasFurnace()
                || (chest != null && this.countContainerItems(chest, stack -> stack.is(Items.FURNACE)) > 0)
                || this.canCraftSupplyFurnaceFromChest(level, chest);
    }

    private boolean hasUsableExpeditionPickaxe() {
        return this.friend.countInventoryItems(this::isUsableExpeditionPickaxe) > 0;
    }

    private boolean hasUsableExpeditionPickaxe(FriendTask task) {
        return this.friend.countInventoryItems(stack -> this.isUsableExpeditionPickaxe(task, stack)) > 0;
    }

    private boolean isUsableExpeditionPickaxe(FriendTask task, ItemStack stack) {
        if (!this.isUsableExpeditionPickaxe(stack)) {
            return false;
        }
        if (task == null || task.target() == null || task.target().isBlank()) {
            return true;
        }
        return MiningTargetRegistry.find(task.target())
                .map(target -> this.canHarvestAnyTargetSource(stack, target.sourceBlocks()))
                .orElse(true);
    }

    private boolean isUsableExpeditionPickaxe(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) {
            return false;
        }
        return !stack.isDamageableItem() || this.remainingDurability(stack) > EXPEDITION_MIN_PICKAXE_DURABILITY;
    }

    private boolean canHarvestAnyTargetSource(ItemStack stack, Block... sourceBlocks) {
        if (sourceBlocks.length == 0) {
            return true;
        }
        for (Block block : sourceBlocks) {
            BlockState state = block.defaultBlockState();
            if (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)) {
                return true;
            }
        }
        return false;
    }

    private int bestExpeditionPickaxeDurability() {
        int best = -1;
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) {
                continue;
            }
            if (!stack.isDamageableItem()) {
                return Integer.MAX_VALUE;
            }
            best = Math.max(best, this.remainingDurability(stack));
        }
        return best;
    }

    private int remainingDurability(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private boolean eatCarriedFoodIfNeeded(FriendTask task) {
        return this.eatCarriedFoodIfBelow(task, EXPEDITION_LOW_FOOD_THRESHOLD);
    }

    private boolean eatCarriedFoodIfBelow(FriendTask task, int foodThreshold) {
        if (task == null
                || task.type() != FriendTaskType.MINING_EXPEDITION
                || this.friend.getHungerProvider().getFoodLevel() > foodThreshold) {
            return false;
        }

        int slot = this.bestCarriedFoodSlot();
        if (slot < 0) {
            return false;
        }

        ItemStack stack = this.friend.getFriendInventory().getItem(slot);
        FoodProperties food = stack.getFoodProperties(this.friend);
        if (food == null) {
            return false;
        }

        this.friend.getInventoryProvider().setSelectedSlot(slot);
        int beforeFood = this.friend.getHungerProvider().getFoodLevel();
        String foodName = stack.getHoverName().getString();
        int newFood = Math.min(20, beforeFood + food.getNutrition());
        float newSaturation = Math.min(
                newFood,
                this.friend.getHungerProvider().getSaturationLevel()
                        + food.getNutrition() * food.getSaturationModifier() * 2.0F
        );
        stack.shrink(1);
        if (stack.isEmpty()) {
            this.friend.getFriendInventory().setItem(slot, ItemStack.EMPTY);
        }
        this.friend.getFriendInventory().setChanged();
        this.friend.getHungerProvider().setFoodLevel(newFood);
        this.friend.getHungerProvider().setSaturationLevel(newSaturation);
        this.friend.swing(InteractionHand.MAIN_HAND);
        this.setExpeditionSupplyStatus("ate food");
        this.rememberPortableNote(task, "expedition ate "
                + foodName
                + " food="
                + beforeFood
                + "->"
                + newFood);
        return true;
    }

    private int bestCarriedFoodSlot() {
        int bestSlot = -1;
        int bestNutrition = -1;
        float bestSaturation = -1.0F;
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (!this.isExpeditionFood(stack)) {
                continue;
            }
            FoodProperties food = stack.getFoodProperties(this.friend);
            float saturation = food.getNutrition() * food.getSaturationModifier();
            if (food.getNutrition() > bestNutrition
                    || (food.getNutrition() == bestNutrition && saturation > bestSaturation)) {
                bestSlot = slot;
                bestNutrition = food.getNutrition();
                bestSaturation = saturation;
            }
        }
        return bestSlot;
    }

    private int carriedFoodItems() {
        return this.friend.countInventoryItems(this::isExpeditionFood);
    }

    private boolean isExpeditionFood(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getFoodProperties(this.friend) != null
                && !this.isRiskyExpeditionFood(stack);
    }

    private boolean isCookableExpeditionFood(ItemStack stack) {
        return stack.is(Items.BEEF)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.MUTTON)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.RABBIT)
                || stack.is(Items.COD)
                || stack.is(Items.SALMON)
                || stack.is(Items.POTATO)
                || stack.is(Items.KELP);
    }

    private boolean isRiskyExpeditionFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.PUFFERFISH)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.SUSPICIOUS_STEW);
    }

    private boolean supplyFurnaceContains(ServerLevel level, SupplySmeltTarget target) {
        if (this.furnaceStationTarget == null || !this.isFurnaceAt(level, this.furnaceStationTarget)) {
            return false;
        }
        if (!(level.getBlockEntity(this.furnaceStationTarget) instanceof Container furnace)) {
            return false;
        }
        ItemStack input = furnace.getItem(0);
        ItemStack output = furnace.getItem(2);
        return (!input.isEmpty() && target.inputMatcher().test(input))
                || (!output.isEmpty() && target.outputMatcher().test(output));
    }

    private int countContainerItems(Container container, Predicate<ItemStack> matcher) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && matcher.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean moveOneMatchingFromContainer(Container container, Predicate<ItemStack> matcher) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            ItemStack toMove = stack.copyWithCount(1);
            ItemStack remainder = this.friend.insertIntoInventory(toMove);
            if (!remainder.isEmpty()) {
                return false;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
            container.setChanged();
            this.friend.getFriendInventory().setChanged();
            return true;
        }
        return false;
    }

    private int unloadMatchingInventoryItems(Container chest, Predicate<ItemStack> matcher) {
        int movedTotal = 0;
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            int original = stack.getCount();
            ItemStack remainder = this.insertIntoContainer(chest, stack.copy());
            int moved = original - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            stack.shrink(moved);
            if (stack.isEmpty()) {
                this.friend.getFriendInventory().setItem(slot, ItemStack.EMPTY);
            }
            movedTotal += moved;
        }
        if (movedTotal > 0) {
            this.friend.getFriendInventory().setChanged();
            chest.setChanged();
        }
        return movedTotal;
    }

    private boolean shouldKeepForExpedition(ItemStack stack, FriendTask task) {
        if (this.isExpeditionFood(stack)) {
            return true;
        }
        if (stack.is(Items.TORCH)
                || stack.is(Items.WOODEN_PICKAXE)
                || stack.is(Items.STONE_PICKAXE)
                || stack.is(Items.IRON_PICKAXE)
                || stack.is(Items.DIAMOND_PICKAXE)
                || stack.is(Items.NETHERITE_PICKAXE)
                || stack.is(Items.WOODEN_AXE)
                || stack.is(Items.STONE_AXE)
                || stack.is(Items.IRON_AXE)
                || stack.is(Items.DIAMOND_AXE)
                || stack.is(Items.NETHERITE_AXE)
                || stack.is(Items.WOODEN_SWORD)
                || stack.is(Items.STONE_SWORD)
                || stack.is(Items.IRON_SWORD)
                || stack.is(Items.DIAMOND_SWORD)
                || stack.is(Items.NETHERITE_SWORD)) {
            return true;
        }
        if (task.target() != null) {
            Optional<MiningTargetRegistry.MiningTarget> target = MiningTargetRegistry.find(task.target());
            return target.isPresent() && target.get().inventoryMatcher().test(stack);
        }
        return false;
    }

    private boolean hasStorableExpeditionOverflow(FriendTask task) {
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (this.isStorableExpeditionOverflow(stack, task)) {
                return true;
            }
        }
        return false;
    }

    private boolean canContainerAcceptStorableExpeditionOverflow(Container container, FriendTask task) {
        if (container == null) {
            return false;
        }
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getFriendInventory().getItem(slot);
            if (this.isStorableExpeditionOverflow(stack, task) && this.canContainerAccept(container, stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStorableExpeditionOverflow(ItemStack stack, FriendTask task) {
        return !stack.isEmpty()
                && !stack.is(Items.CHEST)
                && !this.shouldKeepForExpedition(stack, task);
    }

    private boolean canContainerAccept(Container container, ItemStack stack) {
        if (container == null || stack.isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                return true;
            }
            if (!ItemStack.isSameItemSameTags(existing, stack)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            if (existing.getCount() < max) {
                return true;
            }
        }
        return false;
    }

    private ItemStack insertIntoContainer(Container container, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int moved = Math.min(remainder.getCount(), max - existing.getCount());
            if (moved <= 0) {
                continue;
            }
            existing.grow(moved);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), container.getMaxStackSize()));
            ItemStack inserted = remainder.copyWithCount(moved);
            container.setItem(slot, inserted);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remainder;
    }

    private Optional<MiningExpeditionPlan> parseMiningExpeditionPlan(FriendTask task) {
        if (task.message() == null || task.message().isBlank()) {
            return Optional.empty();
        }
        try {
            MiningExpeditionPlan plan = JsonUtils.GSON.fromJson(task.message(), MiningExpeditionPlan.class);
            if (plan == null) {
                return Optional.empty();
            }
            plan.normalize(task.target(), Math.max(1, task.amount()), "task_json");
            return Optional.of(plan);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private boolean tryPlaceExpeditionTorch(ServerLevel level, WorkStep step) {
        if (this.countTorches() <= 0) {
            return false;
        }

        BlockPos torchPos = this.friend.blockPosition();
        boolean intervalDue = this.expeditionTunnelStepsSinceTorch >= TORCH_INTERVAL_STEPS;
        if (intervalDue && this.hasNearbyTorch(level, torchPos, 5)) {
            this.expeditionTunnelStepsSinceTorch = 0;
            return false;
        }
        if (!this.shouldPlaceTorch(level, torchPos, intervalDue) || !this.interaction.canPlaceBlockAt(level, torchPos)) {
            return false;
        }

        PlaceActionAdapter.PlaceResult result = this.placeAdapter.placeBlock(
                level,
                torchPos,
                Blocks.TORCH,
                stack -> stack.is(Items.TORCH),
                () -> Optional.of(this.friend.blockPosition()),
                TASK_SPEED
        );
        if (result == PlaceActionAdapter.PlaceResult.PLACED) {
            this.expeditionLastTorchPos = torchPos.immutable();
            this.expeditionTunnelStepsSinceTorch = 0;
            step.running("placed torch");
            this.sayThrottled("Placed a torch to keep the mining route lit.");
            this.rememberExpeditionTorchPlaced(torchPos);
            return true;
        }
        return false;
    }

    private boolean shouldPlaceTorch(ServerLevel level, BlockPos pos, boolean intervalDue) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        if (level.getBrightness(LightLayer.SKY, pos) > 0) {
            return false;
        }
        boolean dark = level.getBrightness(LightLayer.BLOCK, pos) <= 7;
        if (!dark && !intervalDue) {
            return false;
        }
        int radius = intervalDue ? 5 : 7;
        return !this.hasNearbyTorch(level, pos, radius);
    }

    private boolean hasNearbyTorch(ServerLevel level, BlockPos pos, int radius) {
        return this.findNearestBlock(level, radius, candidate -> this.isTorchAt(level, candidate)).isPresent();
    }

    private boolean isTorchAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.TORCH)
                || level.getBlockState(pos).is(Blocks.WALL_TORCH);
    }

    private void updateTunnelTorchProgress() {
        BlockPos current = this.friend.blockPosition().immutable();
        if (this.expeditionLastTunnelStepPos == null) {
            this.expeditionLastTunnelStepPos = current;
            return;
        }
        if (current.equals(this.expeditionLastTunnelStepPos)) {
            return;
        }
        this.expeditionLastTunnelStepPos = current;
        this.expeditionTunnelStepsSinceTorch++;
    }

    private void resetTorchPattern() {
        this.expeditionLastTunnelStepPos = null;
        this.expeditionLastTorchPos = null;
        this.expeditionTunnelStepsSinceTorch = 0;
    }

    private Optional<LayerTarget> parseLayerTarget(ServerLevel level, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return Optional.empty();
        }
        String dimension = level.dimension().location().toString();
        String yRange = rawTarget.trim();
        int at = yRange.indexOf('@');
        if (at >= 0) {
            dimension = yRange.substring(0, at).trim();
            yRange = yRange.substring(at + 1).trim();
        }
        String[] parts = yRange.split("\\.\\.", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            if (min > max) {
                int swap = min;
                min = max;
                max = swap;
            }
            int worldMin = level.getMinBuildHeight() + 2;
            int worldMax = level.getMaxBuildHeight() - 3;
            min = Math.max(worldMin, min);
            max = Math.min(worldMax, max);
            if (min > max || dimension.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new LayerTarget(dimension, min, max));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<BlockPos> findNearestStandableInLayer(ServerLevel level, LayerTarget target, int radius) {
        if (target.maxY() - target.minY() > 20) {
            return Optional.empty();
        }

        BlockPos center = this.friend.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = target.minY(); y <= target.maxY(); y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    cursor.set(center.getX() + x, y, center.getZ() + z);
                    if (!FriendPerception.canStandAt(level, cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean digOneStairDown(ServerLevel level, WorkStep step) {
        Direction direction = this.expeditionDirection();
        BlockPos nextFeet = this.friend.blockPosition().relative(direction).below();
        if (!this.canUsePassageFoot(level, nextFeet)) {
            if (this.tryRepairPassageFloor(level, step, nextFeet, "staircase down")) {
                return false;
            }
            this.rotateExpeditionDirection();
            step.running("finding safe staircase direction");
            this.sayThrottled("Looking for a safer staircase direction.");
            return false;
        }
        if (this.isNearCurrentExpeditionHazard(level, nextFeet)) {
            this.rotateExpeditionDirection();
            step.running("avoiding remembered staircase hazard");
            this.sayThrottled("Avoiding a remembered hazard near the staircase route.");
            return false;
        }
        return this.moveOrDigPassage(level, step, nextFeet, "staircase down");
    }

    private boolean digOneStairUp(ServerLevel level, WorkStep step) {
        Direction direction = this.expeditionDirection();
        BlockPos nextFeet = this.friend.blockPosition().relative(direction).above();
        if (!this.canUsePassageFoot(level, nextFeet)) {
            if (this.tryRepairPassageFloor(level, step, nextFeet, "staircase up")) {
                return false;
            }
            this.rotateExpeditionDirection();
            step.running("finding safe upward staircase direction");
            this.sayThrottled("Looking for a safer upward staircase direction.");
            return false;
        }
        if (this.isNearCurrentExpeditionHazard(level, nextFeet)) {
            this.rotateExpeditionDirection();
            step.running("avoiding remembered upward staircase hazard");
            this.sayThrottled("Avoiding a remembered hazard near the upward staircase route.");
            return false;
        }
        return this.moveOrDigPassage(level, step, nextFeet, "staircase up");
    }

    private boolean digOneBranchTunnelStep(ServerLevel level, WorkStep step) {
        this.updateBranchPatternProgress();
        if (this.moveBackFromSideBranch(step)) {
            return false;
        }
        this.ensureBranchPatternDirection();
        this.maybeStartSideBranch();

        Direction direction = this.currentBranchDirection();
        BlockPos nextFeet = this.friend.blockPosition().relative(direction);
        if (!this.canUsePassageFoot(level, nextFeet)) {
            if (this.tryRepairPassageFloor(level, step, nextFeet, "branch tunnel")) {
                return false;
            }
            this.rotateBranchDirection();
            step.running("finding safe branch direction");
            this.sayThrottled("Rotating the branch tunnel because the next space is unsafe.");
            return false;
        }
        if (this.isNearCurrentExpeditionHazard(level, nextFeet)) {
            this.rotateBranchDirection(nextFeet, "remembered hazard");
            step.running("avoiding remembered branch hazard");
            this.sayThrottled("Rotating the branch tunnel around a remembered hazard.");
            return false;
        }
        String label = this.expeditionSideStepsRemaining > 0 ? "side branch" : "branch tunnel";
        return this.moveOrDigPassage(level, step, nextFeet, label);
    }

    private void updateBranchPatternProgress() {
        BlockPos current = this.friend.blockPosition().immutable();
        if (this.expeditionLastBranchStepPos == null) {
            this.expeditionLastBranchStepPos = current;
            return;
        }
        if (current.equals(this.expeditionLastBranchStepPos)) {
            return;
        }
        this.expeditionLastBranchStepPos = current;
        if (this.expeditionReturningFromSideBranch) {
            return;
        }
        if (this.expeditionSideStepsRemaining > 0) {
            this.expeditionSideStepsRemaining--;
            if (this.expeditionSideStepsRemaining <= 0 && this.expeditionSideBranchAnchor != null) {
                this.expeditionSideBranchEnd = current;
                this.rememberExpeditionBranchRoute(
                        "side",
                        this.expeditionSideDirection,
                        this.expeditionSideBranchAnchor,
                        this.expeditionSideBranchEnd,
                        BRANCH_SIDE_LENGTH,
                        BRANCH_SIDE_LENGTH,
                        "completed"
                );
                this.expeditionReturningFromSideBranch = true;
                this.rememberExpeditionBranchNote("completed side branch and returning to anchor at "
                        + this.formatPos(this.expeditionSideBranchAnchor));
            }
            return;
        }
        this.expeditionBranchMainSteps++;
    }

    private boolean moveBackFromSideBranch(WorkStep step) {
        if (!this.expeditionReturningFromSideBranch || this.expeditionSideBranchAnchor == null) {
            return false;
        }
        if (this.friend.blockPosition().distSqr(this.expeditionSideBranchAnchor) > 4.0D) {
            this.body.moveTo(this.expeditionSideBranchAnchor, TASK_SPEED);
            step.running("returning from side branch");
            return true;
        }

        this.expeditionReturningFromSideBranch = false;
        this.expeditionSideBranchAnchor = null;
        this.expeditionSideBranchEnd = null;
        this.expeditionSideDirection = null;
        this.expeditionDirection = this.expeditionMainDirection == null ? this.expeditionDirection() : this.expeditionMainDirection;
        this.expeditionBranchMainSteps = 0;
        this.expeditionMainBranchStart = this.friend.blockPosition().immutable();
        this.rememberExpeditionBranchNote("returned to main branch at " + this.formatPos(this.friend.blockPosition()));
        return false;
    }

    private void ensureBranchPatternDirection() {
        if (this.expeditionMainDirection == null || this.expeditionMainDirection.getAxis().isVertical()) {
            Direction fallback = this.expeditionDirection();
            Optional<Direction> preferred = this.preferredInitialBranchDirectionFromResourceHits(
                    this.friend.blockPosition(),
                    fallback
            );
            this.expeditionMainDirection = preferred.orElse(fallback);
            this.expeditionDirection = this.expeditionMainDirection;
            if (preferred.isPresent()) {
                this.rememberExpeditionBranchNote("selected main branch dir="
                        + this.expeditionMainDirection.getName()
                        + " using resource-hit memory at "
                        + this.formatPos(this.friend.blockPosition()));
            }
        }
        if (this.expeditionDirection == null || this.expeditionDirection.getAxis().isVertical()) {
            this.expeditionDirection = this.expeditionMainDirection;
        }
        if (this.expeditionMainBranchStart == null) {
            this.expeditionMainBranchStart = this.friend.blockPosition().immutable();
        }
    }

    private void maybeStartSideBranch() {
        if (this.expeditionReturningFromSideBranch
                || this.expeditionSideStepsRemaining > 0
                || this.expeditionBranchMainSteps < BRANCH_MAIN_SEGMENT_LENGTH) {
            return;
        }
        this.rememberExpeditionBranchRoute(
                "main",
                this.expeditionMainDirection,
                this.expeditionMainBranchStart,
                this.friend.blockPosition(),
                BRANCH_MAIN_SEGMENT_LENGTH,
                this.expeditionBranchMainSteps,
                "completed"
        );
        this.expeditionSideBranchAnchor = this.friend.blockPosition().immutable();
        this.expeditionSideBranchCount++;
        Optional<Direction> preferredSide = this.preferredSideBranchDirection(this.expeditionSideBranchAnchor);
        this.expeditionSideDirection = preferredSide.orElseGet(() -> this.expeditionSideBranchCount % 2 == 0
                        ? this.expeditionMainDirection.getCounterClockWise()
                        : this.expeditionMainDirection.getClockWise());
        this.expeditionDirection = this.expeditionSideDirection;
        this.expeditionSideStepsRemaining = BRANCH_SIDE_LENGTH;
        this.markTorchDueAtBranchAnchor();
        this.rememberExpeditionBranchNote("started side branch "
                + this.expeditionSideBranchCount
                + " dir="
                + this.expeditionSideDirection.getName()
                + " anchor="
                + this.formatPos(this.expeditionSideBranchAnchor)
                + (preferredSide.isPresent() ? " using resource-hit memory" : ""));
    }

    private void markTorchDueAtBranchAnchor() {
        if (this.countTorches() > 0) {
            this.expeditionTunnelStepsSinceTorch = Math.max(this.expeditionTunnelStepsSinceTorch, TORCH_INTERVAL_STEPS);
        }
    }

    private Optional<Direction> preferredSideBranchDirection(BlockPos anchor) {
        if (anchor == null || this.expeditionMainDirection == null || this.expeditionMainDirection.getAxis().isVertical()) {
            return Optional.empty();
        }
        FriendTask task = this.friend.getCurrentTask();
        Optional<ExpeditionMemory> memory = this.findRememberedExpedition(task, this.currentDimension());
        if (memory.isEmpty() || memory.get().resourceHits == null || memory.get().resourceHits.isEmpty()) {
            return Optional.empty();
        }
        Direction clockwise = this.expeditionMainDirection.getClockWise();
        Direction counterClockwise = this.expeditionMainDirection.getCounterClockWise();
        double clockwiseScore = this.scoreResourceHitDirection(memory.get(), anchor, clockwise);
        double counterClockwiseScore = this.scoreResourceHitDirection(memory.get(), anchor, counterClockwise);
        if (Math.max(clockwiseScore, counterClockwiseScore) <= 0.0D
                || Math.abs(clockwiseScore - counterClockwiseScore) < EXPEDITION_RESOURCE_DIRECTION_SCORE_MARGIN) {
            return Optional.empty();
        }
        return Optional.of(clockwiseScore > counterClockwiseScore ? clockwise : counterClockwise);
    }

    private Optional<Direction> preferredInitialBranchDirectionFromResourceHits(BlockPos anchor, Direction fallback) {
        if (anchor == null || fallback == null || fallback.getAxis().isVertical()) {
            return Optional.empty();
        }
        FriendTask task = this.friend.getCurrentTask();
        Optional<ExpeditionMemory> memory = this.findRememberedExpedition(task, this.currentDimension());
        if (memory.isEmpty() || memory.get().resourceHits == null || memory.get().resourceHits.isEmpty()) {
            return Optional.empty();
        }

        Direction best = fallback;
        double fallbackScore = this.scoreResourceHitDirection(memory.get(), anchor, fallback);
        double bestScore = fallbackScore;
        for (Direction direction : HORIZONTAL_EXPEDITION_DIRECTIONS) {
            double score = this.scoreResourceHitDirection(memory.get(), anchor, direction);
            if (score > bestScore) {
                best = direction;
                bestScore = score;
            }
        }
        if (best == fallback
                || bestScore <= 0.0D
                || bestScore < fallbackScore + EXPEDITION_RESOURCE_DIRECTION_SCORE_MARGIN) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private Optional<Direction> preferredRotationDirectionFromResourceHits(BlockPos anchor, Direction base) {
        if (anchor == null || base == null || base.getAxis().isVertical()) {
            return Optional.empty();
        }
        FriendTask task = this.friend.getCurrentTask();
        Optional<ExpeditionMemory> memory = this.findRememberedExpedition(task, this.currentDimension());
        if (memory.isEmpty() || memory.get().resourceHits == null || memory.get().resourceHits.isEmpty()) {
            return Optional.empty();
        }
        Direction clockwise = base.getClockWise();
        Direction counterClockwise = base.getCounterClockWise();
        double clockwiseScore = this.scoreResourceHitDirection(memory.get(), anchor, clockwise);
        double counterClockwiseScore = this.scoreResourceHitDirection(memory.get(), anchor, counterClockwise);
        if (counterClockwiseScore <= 0.0D
                || counterClockwiseScore < clockwiseScore + EXPEDITION_RESOURCE_DIRECTION_SCORE_MARGIN) {
            return Optional.empty();
        }
        return Optional.of(counterClockwise);
    }

    private double scoreResourceHitDirection(ExpeditionMemory memory, BlockPos anchor, Direction direction) {
        if (memory == null || memory.resourceHits == null || anchor == null || direction == null || direction.getAxis().isVertical()) {
            return 0.0D;
        }
        String dimension = this.currentDimension();
        double score = 0.0D;
        for (ExpeditionMemory.ResourceHit hit : memory.resourceHits) {
            if (hit == null || hit.position == null || !hit.position.isInDimension(dimension)) {
                continue;
            }
            BlockPos pos = hit.position.asBlockPos();
            double dx = pos.getX() - anchor.getX();
            double dz = pos.getZ() - anchor.getZ();
            double forward = dx * direction.getStepX() + dz * direction.getStepZ();
            if (forward <= 0.0D) {
                continue;
            }
            double distanceSqr = Math.max(1.0D, anchor.distSqr(pos));
            double amountBonus = this.resourceHitScoreAmount(hit) * 3.0D;
            double directionBonus = direction.getName().equalsIgnoreCase(hit.direction) ? 12.0D : 0.0D;
            score += (forward * forward * 24.0D / distanceSqr) + amountBonus + directionBonus;
        }
        return score;
    }

    private String currentExpeditionRouteType() {
        if (this.expeditionSideStepsRemaining > 0
                || this.expeditionReturningFromSideBranch
                || this.expeditionSideBranchAnchor != null) {
            return "side";
        }
        if (this.expeditionMainBranchStart != null || this.expeditionMainDirection != null) {
            return "main";
        }
        return "unknown";
    }

    private String currentExpeditionRouteDirectionName() {
        Direction direction = this.expeditionSideStepsRemaining > 0 && this.expeditionSideDirection != null
                ? this.expeditionSideDirection
                : (this.expeditionMainDirection == null ? this.expeditionDirection : this.expeditionMainDirection);
        return direction == null ? "unknown" : direction.getName();
    }

    private Direction currentBranchDirection() {
        Direction direction = this.expeditionSideStepsRemaining > 0
                ? this.expeditionSideDirection
                : this.expeditionMainDirection;
        if (direction == null || direction.getAxis().isVertical()) {
            direction = this.expeditionDirection();
        }
        this.expeditionDirection = direction;
        return direction;
    }

    private void rotateBranchDirection() {
        this.rotateBranchDirection(this.friend.blockPosition().immutable(), "safety");
    }

    private void rotateBranchDirection(BlockPos interruptedAt, String reason) {
        BlockPos routeEnd = interruptedAt == null ? this.friend.blockPosition().immutable() : interruptedAt.immutable();
        if (this.expeditionSideStepsRemaining > 0 && this.expeditionSideBranchAnchor != null) {
            this.rememberExpeditionBranchRoute(
                    "side",
                    this.expeditionSideDirection,
                    this.expeditionSideBranchAnchor,
                    routeEnd,
                    BRANCH_SIDE_LENGTH,
                    Math.max(0, BRANCH_SIDE_LENGTH - this.expeditionSideStepsRemaining),
                    "interrupted"
            );
        } else if (this.expeditionMainBranchStart != null && this.expeditionBranchMainSteps > 0) {
            this.rememberExpeditionBranchRoute(
                    "main",
                    this.expeditionMainDirection,
                    this.expeditionMainBranchStart,
                    routeEnd,
                    BRANCH_MAIN_SEGMENT_LENGTH,
                    this.expeditionBranchMainSteps,
                    "interrupted"
            );
        }
        Direction base = this.expeditionMainDirection == null || this.expeditionMainDirection.getAxis().isVertical()
                ? this.expeditionDirection()
                : this.expeditionMainDirection;
        Optional<Direction> preferredDirection = this.preferredRotationDirectionFromResourceHits(routeEnd, base);
        this.expeditionDirection = preferredDirection.orElseGet(base::getClockWise);
        this.expeditionMainDirection = this.expeditionDirection;
        this.expeditionSideDirection = null;
        this.expeditionSideStepsRemaining = 0;
        this.expeditionReturningFromSideBranch = false;
        this.expeditionSideBranchAnchor = null;
        this.expeditionSideBranchEnd = null;
        this.expeditionBranchMainSteps = 0;
        this.expeditionDigTarget = null;
        this.interaction.cancelBreakBlock();
        this.expeditionMainBranchStart = this.friend.blockPosition().immutable();
        this.rememberExpeditionBranchNote("rotated main branch to "
                + this.expeditionDirection.getName()
                + " for "
                + (reason == null || reason.isBlank() ? "safety" : reason)
                + (preferredDirection.isPresent() ? " using resource-hit memory" : "")
                + " near "
                + this.formatPos(routeEnd));
    }

    private void resetBranchPattern() {
        this.expeditionLastBranchStepPos = null;
        this.expeditionMainBranchStart = null;
        this.expeditionSideBranchAnchor = null;
        this.expeditionSideBranchEnd = null;
        this.expeditionMainDirection = null;
        this.expeditionSideDirection = null;
        this.expeditionBranchMainSteps = 0;
        this.expeditionSideStepsRemaining = 0;
        this.expeditionSideBranchCount = 0;
        this.expeditionReturningFromSideBranch = false;
    }

    private boolean moveOrDigPassage(ServerLevel level, WorkStep step, BlockPos nextFeet, String label) {
        Optional<BlockPos> blocker = this.firstBlockingPassageBlock(level, nextFeet);
        if (blocker.isEmpty() && FriendPerception.canStandAt(level, nextFeet)) {
            this.expeditionDigTarget = null;
            if (this.isExpeditionMoveStalled(nextFeet, "moving through " + label)) {
                this.body.stop();
                this.resetExpeditionMoveWatch();
                this.rotatePassageDirection(label);
                step.running("movement stalled in " + label);
                this.sayThrottled("I could not move through that "
                        + label
                        + ", so I am rotating toward another route.");
                this.rememberExpeditionRouteNote("route_stall", "movement stalled in "
                        + label
                        + " near "
                        + this.formatPos(nextFeet));
                return false;
            }
            this.body.moveToNearby(nextFeet, TASK_SPEED);
            step.running("moving through " + label);
            return false;
        }

        if (blocker.isEmpty()) {
            this.rotatePassageDirection(label);
            step.running("blocked by unsafe " + label);
            return false;
        }

        BlockPos target = blocker.get();
        this.expeditionDigTarget = target;
        BlockState state = level.getBlockState(target);
        if (state.hasBlockEntity()
                || state.getDestroySpeed(level, target) < 0.0F
                || !state.getFluidState().isEmpty()) {
            this.rotatePassageDirection(label);
            step.running("avoiding unsafe block in " + label);
            return false;
        }
        if (this.hasNearbyFluidHazard(level, target)) {
            this.rotatePassageDirection(label);
            step.running("avoiding lava near " + label);
            this.rememberExpeditionHazardAvoided("lava", target, "lava near " + label);
            return false;
        }
        if (this.hasNearbyFluidLeak(level, target)) {
            this.rotatePassageDirection(label);
            step.running("avoiding fluid leak near " + label);
            this.rememberExpeditionHazardAvoided("fluid", target, "fluid leak near " + label);
            return false;
        }
        if (this.hasFallingBlockCollapseRisk(level, target)) {
            this.rotatePassageDirection(label);
            step.running("avoiding collapse risk near " + label);
            this.rememberExpeditionHazardAvoided("collapse", target, "falling block above "
                    + this.formatPos(target));
            return false;
        }
        if (this.isRiskyExpeditionPassageBlock(state)) {
            this.rotatePassageDirection(label);
            step.running("avoiding risky block in " + label);
            this.rememberExpeditionHazardAvoided("risky_block", target, "risky block "
                    + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " at "
                    + this.formatPos(target));
            return false;
        }

        if (!this.interaction.canReachBlock(target)) {
            Optional<BlockPos> approach = this.findStandPositionNearBlock(level, target);
            if (approach.isEmpty()) {
                this.resetExpeditionMoveWatch();
                this.rotatePassageDirection(label);
                step.running("no safe approach for " + label);
                this.rememberExpeditionRouteNote("route_blocked", "no safe dig approach for "
                        + label
                        + " near "
                        + this.formatPos(target));
                return false;
            }
            if (this.isExpeditionMoveStalled(approach.get(), "approaching dig target in " + label)) {
                this.body.stop();
                this.resetExpeditionMoveWatch();
                this.rotatePassageDirection(label);
                step.running("dig approach stalled in " + label);
                this.sayThrottled("I could not reach that "
                        + label
                        + " dig position, so I am rotating toward another route.");
                this.rememberExpeditionRouteNote("route_stall", "dig approach stalled in "
                        + label
                        + " near "
                        + this.formatPos(target));
                return false;
            }
            this.body.moveTo(approach.get(), TASK_SPEED);
            step.running("moving to dig " + label);
            return false;
        }

        this.resetExpeditionMoveWatch();
        this.body.stop();
        SurvivalWorldInteractor.BreakResult result = this.interaction.tickBreakBlock(level, target);
        switch (result) {
            case BROKEN -> {
                this.expeditionDigTarget = null;
                step.running("dug " + label);
                return false;
            }
            case FAILED -> {
                this.expeditionDigTarget = null;
                this.rotatePassageDirection(label);
                step.running("failed to dig " + label);
                return false;
            }
            case NOT_IN_REACH, WORKING -> {
                step.running("digging " + label);
                return false;
            }
        }
        return false;
    }

    private boolean tryRepairPassageFloor(ServerLevel level, WorkStep step, BlockPos nextFeet, String label) {
        BlockPos floorPos = nextFeet == null ? null : nextFeet.below();
        if (!this.canRepairPassageFloorAt(level, floorPos, nextFeet)) {
            return false;
        }
        Block block = this.expeditionFloorRepairBlockToPlace(this.friend.getCurrentTask());
        if (block == null) {
            return false;
        }
        this.setExpeditionSupplyStatus("repairing floor gap");
        PlaceActionAdapter.PlaceResult result = this.placeAdapter.placeBlock(
                level,
                floorPos,
                block,
                stack -> this.isMatchingFloorRepairBlock(stack, block),
                () -> this.findStandPositionNearBlock(level, floorPos),
                TASK_SPEED
        );
        if (result == PlaceActionAdapter.PlaceResult.WORKING) {
            step.running("moving to repair floor for " + label);
            return true;
        }
        if (result == PlaceActionAdapter.PlaceResult.PLACED) {
            step.running("repaired floor for " + label);
            this.rememberExpeditionRouteNote("route_repaired", "repaired floor gap for "
                    + label
                    + " at "
                    + this.formatPos(floorPos));
            return true;
        }
        return false;
    }

    private boolean canRepairPassageFloorAt(ServerLevel level, BlockPos floorPos, BlockPos feetPos) {
        if (floorPos == null
                || feetPos == null
                || !level.hasChunkAt(floorPos)
                || !level.hasChunkAt(floorPos.below())
                || !level.hasChunkAt(feetPos)
                || !level.hasChunkAt(feetPos.above())
                || !this.hasLoadedNeighborhood(level, floorPos)
                || !this.hasLoadedNeighborhood(level, feetPos)) {
            return false;
        }
        BlockState floor = level.getBlockState(floorPos);
        BlockState support = level.getBlockState(floorPos.below());
        return floor.canBeReplaced()
                && floor.getFluidState().isEmpty()
                && support.getFluidState().isEmpty()
                && !support.isAir()
                && !this.isRiskyExpeditionFloorBlock(support)
                && support.isFaceSturdy(level, floorPos.below(), Direction.UP)
                && level.getFluidState(feetPos).isEmpty()
                && level.getFluidState(feetPos.above()).isEmpty()
                && !this.hasNearbyFluidHazard(level, floorPos)
                && !this.hasNearbyFluidHazard(level, feetPos);
    }

    private void rotatePassageDirection(String label) {
        if (label != null && label.contains("branch")) {
            this.rotateBranchDirection();
            return;
        }
        this.rotateExpeditionDirection();
    }

    private Optional<BlockPos> firstBlockingPassageBlock(ServerLevel level, BlockPos nextFeet) {
        if (nextFeet.getY() < this.friend.blockPosition().getY()) {
            BlockPos descendingTransitionHead = nextFeet.above(2);
            if (!this.isPassageSpaceClear(level, descendingTransitionHead)) {
                return Optional.of(descendingTransitionHead.immutable());
            }
            BlockPos head = nextFeet.above();
            if (!this.isPassageSpaceClear(level, head)) {
                return Optional.of(head.immutable());
            }
            if (!this.isPassageSpaceClear(level, nextFeet)) {
                return Optional.of(nextFeet.immutable());
            }
            return Optional.empty();
        }
        if (!this.isPassageSpaceClear(level, nextFeet)) {
            return Optional.of(nextFeet.immutable());
        }
        BlockPos head = nextFeet.above();
        if (!this.isPassageSpaceClear(level, head)) {
            return Optional.of(head.immutable());
        }
        return Optional.empty();
    }

    private boolean isPassageSpaceClear(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private boolean canUsePassageFoot(ServerLevel level, BlockPos feetPos) {
        if (feetPos == null
                || !level.hasChunkAt(feetPos)
                || !level.hasChunkAt(feetPos.above())
                || !level.hasChunkAt(feetPos.below())
                || !this.hasLoadedNeighborhood(level, feetPos)
                || !this.hasLoadedNeighborhood(level, feetPos.above())) {
            return false;
        }
        BlockState below = level.getBlockState(feetPos.below());
        return below.getFluidState().isEmpty()
                && !below.isAir()
                && !this.isRiskyExpeditionFloorBlock(below)
                && below.isFaceSturdy(level, feetPos.below(), Direction.UP)
                && level.getFluidState(feetPos).isEmpty()
                && level.getFluidState(feetPos.above()).isEmpty()
                && !this.hasNearbyFluidHazard(level, feetPos)
                && !this.hasNearbyFluidHazard(level, feetPos.above());
    }

    private boolean isRiskyExpeditionFloorBlock(BlockState state) {
        return this.isRiskyExpeditionBlock(state);
    }

    private boolean isRiskyExpeditionPassageBlock(BlockState state) {
        return this.isRiskyExpeditionBlock(state);
    }

    private boolean isRiskyExpeditionBlock(BlockState state) {
        Block block = state.getBlock();
        return EXPEDITION_RISKY_BLOCKS.contains(block)
                || block instanceof FallingBlock
                || this.isVanillaInfestedBlock(block);
    }

    private boolean isVanillaInfestedBlock(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return "minecraft".equals(key.getNamespace()) && key.getPath().startsWith("infested_");
    }

    private boolean hasFallingBlockCollapseRisk(ServerLevel level, BlockPos minedPos) {
        if (minedPos == null || !level.hasChunkAt(minedPos)) {
            return true;
        }
        BlockPos above = minedPos.above();
        if (!level.hasChunkAt(above)) {
            return true;
        }
        return level.getBlockState(above).getBlock() instanceof FallingBlock;
    }

    private boolean isFluidDisplaced(ServerLevel level, BlockPos feetPos) {
        return feetPos != null
                && (!level.getFluidState(feetPos).isEmpty()
                || !level.getFluidState(feetPos.above()).isEmpty());
    }

    private boolean hasImmediateLavaHazard(ServerLevel level, BlockPos feetPos) {
        return this.hasNearbyFluidHazard(level, feetPos)
                || this.hasNearbyFluidHazard(level, feetPos.above());
    }

    private boolean hasNearbyFluidHazard(ServerLevel level, BlockPos center) {
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

    private boolean hasNearbyFluidLeak(ServerLevel level, BlockPos center) {
        if (center == null || !level.hasChunkAt(center)) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            cursor.setWithOffset(center, direction);
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            if (this.isNonLavaFluid(level, cursor)) {
                return true;
            }
        }
        return this.isNonLavaFluid(level, center);
    }

    private boolean isNonLavaFluid(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        return !level.getFluidState(pos).isEmpty() && !level.getFluidState(pos).is(FluidTags.LAVA);
    }

    private boolean hasLoadedNeighborhood(ServerLevel level, BlockPos center) {
        if (center == null || !level.hasChunkAt(center)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (!level.hasChunkAt(center.relative(direction))) {
                return false;
            }
        }
        return true;
    }

    private Direction expeditionDirection() {
        if (this.expeditionDirection == null || this.expeditionDirection.getAxis().isVertical()) {
            Direction facing = this.friend.getDirection();
            this.expeditionDirection = facing.getAxis().isVertical() ? Direction.NORTH : facing;
        }
        return this.expeditionDirection;
    }

    private void rotateExpeditionDirection() {
        Direction base = this.expeditionDirection();
        this.expeditionDirection = this.preferredRotationDirectionFromResourceHits(this.friend.blockPosition(), base)
                .orElseGet(base::getClockWise);
        this.expeditionDigTarget = null;
        this.interaction.cancelBreakBlock();
    }

    private Optional<BlockPos> findNearestMineableSource(ServerLevel level, int radius, Block... sourceBlocks) {
        return this.findNearestReachableBlock(level, radius, radius, radius,
                pos -> this.isMineableSource(level, pos, sourceBlocks)
                        && this.isExposedTargetBlock(level, pos)
                        && this.isSafeTargetedResourceBreak(level, pos)
                        && !this.isProtectedResourceExploreStructure(pos)
                        && !this.isRejectedResourceTarget(pos));
    }

    private void rejectResourceTarget() {
        if (this.resourceTarget != null) {
            this.resourceRejectedTarget = this.resourceTarget.immutable();
            this.resourceRejectedTargetTicks = RESOURCE_TARGET_REJECT_TICKS;
        }
        this.resourceTarget = null;
        this.resourceSearchCooldownTicks = 0;
        this.resetResourceExploreMoveWatch();
        this.interaction.cancelBreakBlock();
    }

    private boolean isRejectedResourceTarget(BlockPos pos) {
        return this.resourceRejectedTargetTicks > 0
                && this.resourceRejectedTarget != null
                && this.resourceRejectedTarget.equals(pos);
    }

    private boolean refreshResourceTargetIfDue(ServerLevel level, String targetKind, Block... sourceBlocks) {
        boolean targetKindChanged = !Objects.equals(targetKind, this.resourceTargetKind);
        if (!targetKindChanged
                && this.resourceTarget != null
                && this.isVisibleMineableSource(level, this.resourceTarget, sourceBlocks)) {
            return true;
        }

        this.resourceTarget = null;
        this.resourceTargetKind = targetKind;
        if (targetKindChanged) {
            this.resourceSearchCooldownTicks = 0;
        }
        if (this.resourceSearchCooldownTicks > 0) {
            return false;
        }

        this.resourceTarget = this.findNearestMineableSource(level, 18, sourceBlocks).orElse(null);
        this.resourceSearchCooldownTicks = RESOURCE_SEARCH_INTERVAL_TICKS;
        return this.resourceTarget != null;
    }

    private Optional<BlockPos> findNearestReachableLog(ServerLevel level, int radius) {
        return this.findNearestReachableBlock(level, radius, WOOD_SEARCH_DOWN, WOOD_SEARCH_UP,
                pos -> isBreakableLog(level, pos)
                        && this.isExposedTargetBlock(level, pos));
    }

    private Optional<BlockPos> findNearestReachableLeafBlockingObservedLog(ServerLevel level, int radius) {
        return this.findNearestReachableBlock(level, radius, WOOD_SEARCH_DOWN, WOOD_SEARCH_UP,
                pos -> this.isBreakableLeaf(level, pos)
                        && this.isExposedTargetBlock(level, pos)
                        && this.hasAdjacentExposedBreakableLog(level, pos));
    }

    private boolean isBreakableLeaf(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.LEAVES)
                && !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private boolean hasAdjacentExposedBreakableLog(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (isBreakableLog(level, adjacent) && this.isExposedTargetBlock(level, adjacent)) {
                return true;
            }
        }
        return false;
    }

    private Optional<BlockPos> findNearestReachableBlock(
            ServerLevel level,
            int horizontalRadius,
            int down,
            int up,
            Predicate<BlockPos> candidatePredicate
    ) {
        BlockPos center = this.friend.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -down; y <= up; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (level.hasChunkAt(cursor) && candidatePredicate.test(cursor)) {
                        candidates.add(cursor.immutable());
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(center::distSqr));
        return candidates.stream()
                .filter(pos -> this.isReachableMiningTarget(level, pos))
                .findFirst();
    }

    private Optional<BlockPos> findNearestBlock(ServerLevel level, int radius, Predicate<BlockPos> predicate) {
        return this.findNearestBlock(level, radius, radius, radius, predicate);
    }

    private Optional<BlockPos> findNearestBlock(ServerLevel level, int horizontalRadius, int down, int up, Predicate<BlockPos> predicate) {
        BlockPos center = this.friend.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -down; y <= up; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (!predicate.test(cursor)) {
                        continue;
                    }
                    double distance = center.distSqr(cursor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isMineableSource(ServerLevel level, BlockPos pos, Block... sourceBlocks) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        for (Block block : sourceBlocks) {
            if (level.getBlockState(pos).is(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisibleMineableSource(ServerLevel level, BlockPos pos, Block... sourceBlocks) {
        return this.isMineableSource(level, pos, sourceBlocks)
                && this.isExposedTargetBlock(level, pos)
                && this.isSafeTargetedResourceBreak(level, pos)
                && !this.isProtectedResourceExploreStructure(pos)
                && this.isReachableMiningTarget(level, pos);
    }

    private boolean isSafeTargetedResourceBreak(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && state.getFluidState().isEmpty()
                && !this.hasNearbyFluidHazard(level, pos)
                && !this.hasNearbyFluidLeak(level, pos)
                && !this.hasFallingBlockCollapseRisk(level, pos)
                && !this.isRiskyExpeditionPassageBlock(state);
    }

    private boolean isProtectedResourceExploreStructure(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (pos.equals(this.friend.blockPosition().below())) {
            return true;
        }
        for (BlockPos breadcrumb : this.resourceExploreBreadcrumbs) {
            if (pos.equals(breadcrumb.below())) {
                return true;
            }
        }
        return false;
    }

    private boolean isReachableMiningTarget(ServerLevel level, BlockPos pos) {
        return this.interaction.canReachBlock(pos) || this.findStandPositionNearBlock(level, pos).isPresent();
    }

    private boolean isExposedTargetBlock(ServerLevel level, BlockPos pos) {
        return FriendPerception.isExposedBlock(level, pos);
    }

    private boolean hasToolForAnySource(Block... sourceBlocks) {
        if (sourceBlocks.length == 0) {
            return true;
        }
        for (Block block : sourceBlocks) {
            BlockState state = block.defaultBlockState();
            if (!state.requiresCorrectToolForDrops()) {
                return true;
            }
            for (int slot = 0; slot < this.friend.getInventoryProvider().getContainerSize(); slot++) {
                ItemStack stack = this.friend.getInventoryProvider().getItem(slot);
                if (!stack.isEmpty() && stack.isCorrectToolForDrops(state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean workflowNeedsCraftingStation(ServerLevel level) {
        return this.workflow != null && this.workflow.hasPendingStep(step ->
                step.type() == WorkStepType.CRAFT_ITEM
                        && this.craftingAdapter.requiresCraftingTable(level, this.recipeMatcherFor(step.target())));
    }

    private boolean workflowOutputSatisfied() {
        if (this.workflow == null) {
            return false;
        }
        FriendTask task = this.friend.getCurrentTask();
        if (task != null && task.type() == FriendTaskType.CRAFT_ITEM && task.target() != null && !task.target().isBlank()) {
            Predicate<ItemStack> matcher = this.recipeMatcherFor(task.target());
            return this.friend.countInventoryItems(matcher) >= Math.max(1, task.amount());
        }
        if (task != null
                && (task.type() == FriendTaskType.MINE_RESOURCE || task.type() == FriendTaskType.MINING_EXPEDITION)
                && task.target() != null
                && !task.target().isBlank()) {
            Optional<MiningTargetRegistry.MiningTarget> target = MiningTargetRegistry.find(task.target());
            if (target.isPresent()) {
                return this.friend.countInventoryItems(target.get().inventoryMatcher()) >= Math.max(1, task.amount());
            }
        }
        return switch (this.workflow.id()) {
            case "make_crafting_table" -> this.hasCraftingTable();
            case "make_sticks" -> this.countSticks() >= 4;
            case "make_chest" -> this.hasChest();
            case "make_wooden_axe" -> this.countWoodenAxes() >= 1;
            case "make_wooden_pickaxe" -> this.countWoodenPickaxes() >= 1;
            case "make_stone_pickaxe" -> this.countStonePickaxes() >= 1;
            case "make_furnace" -> this.hasFurnace() || this.furnaceStationTarget != null;
            case "make_iron_ingot" -> this.countIronIngots() >= 1;
            case "make_iron_pickaxe" -> this.countIronPickaxes() >= 1;
            default -> false;
        };
    }

    private String workflowSummary() {
        return this.workflow == null ? "none" : this.workflow.summary();
    }

    private Predicate<ItemStack> recipeMatcherFor(String target) {
        return switch (target) {
            case WorkflowFactory.PLANKS -> stack -> stack.is(ItemTags.PLANKS);
            case WorkflowFactory.CRAFTING_TABLE -> stack -> stack.is(Items.CRAFTING_TABLE);
            case WorkflowFactory.STICKS -> stack -> stack.is(Items.STICK);
            case WorkflowFactory.CHEST -> stack -> stack.is(Items.CHEST);
            case WorkflowFactory.WOODEN_AXE -> stack -> stack.is(Items.WOODEN_AXE);
            case WorkflowFactory.WOODEN_PICKAXE -> stack -> stack.is(Items.WOODEN_PICKAXE);
            case WorkflowFactory.COBBLESTONE -> stack -> stack.is(Items.COBBLESTONE);
            case WorkflowFactory.STONE_PICKAXE -> stack -> stack.is(Items.STONE_PICKAXE);
            case WorkflowFactory.FURNACE -> stack -> stack.is(Items.FURNACE);
            case WorkflowFactory.COAL -> stack -> stack.is(Items.COAL);
            case WorkflowFactory.RAW_IRON -> stack -> stack.is(Items.RAW_IRON);
            case WorkflowFactory.IRON_INGOT -> stack -> stack.is(Items.IRON_INGOT);
            case WorkflowFactory.IRON_PICKAXE -> stack -> stack.is(Items.IRON_PICKAXE);
            default -> this.parseItemId(target)
                    .<Predicate<ItemStack>>map(id -> stack -> !stack.isEmpty()
                            && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    .orElse(stack -> false);
        };
    }

    private Optional<ResourceLocation> parseItemId(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawTarget.contains(":") ? rawTarget.trim() : "minecraft:" + rawTarget.trim();
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    private String craftingStationSummary() {
        return this.craftingStationTarget == null ? "none" : this.formatPos(this.craftingStationTarget);
    }

    private String furnaceStationSummary() {
        return this.furnaceStationTarget == null ? "none" : this.formatPos(this.furnaceStationTarget);
    }

    private String resourceExploreSummary() {
        if (this.resourceExploreKind == null && this.resourceExploreBreadcrumbs.isEmpty()) {
            return "none";
        }
        return "target="
                + (this.resourceExploreKind == null ? "returning" : this.resourceExploreKind)
                + ",dir="
                + (this.resourceExploreDirection == null ? "none" : this.resourceExploreDirection.getName())
                + ",dig="
                + this.formatPos(this.resourceExploreDigTarget)
                + ",targetY="
                + this.resourceExploreTargetY
                + ",segmentRemaining="
                + this.resourceExploreStepsRemaining
                + ",turns="
                + this.resourceExploreTurns
                + ",breadcrumbs="
                + this.resourceExploreBreadcrumbs.size();
    }

    private String expeditionSummary() {
        if (this.expeditionSupplyPoint == null && this.expeditionMineEntrance == null && this.expeditionDigTarget == null) {
            return "none";
        }
        String supply = this.expeditionSupplyPoint == null ? "none" : this.formatPos(this.expeditionSupplyPoint);
        String entrance = this.expeditionMineEntrance == null ? "none" : this.formatPos(this.expeditionMineEntrance);
        String routeTarget = this.expeditionRouteResumeTarget == null ? "none" : this.formatPos(this.expeditionRouteResumeTarget);
        String routeAnchor = this.expeditionRouteResumeAnchor == null ? "none" : this.formatPos(this.expeditionRouteResumeAnchor);
        String dig = this.expeditionDigTarget == null ? "none" : this.formatPos(this.expeditionDigTarget);
        String direction = this.expeditionDirection == null ? "none" : this.expeditionDirection.getName();
        String mainDirection = this.expeditionMainDirection == null ? "none" : this.expeditionMainDirection.getName();
        String sideDirection = this.expeditionSideDirection == null ? "none" : this.expeditionSideDirection.getName();
        int pickaxeDurability = this.bestExpeditionPickaxeDurability();
        int resourceHits = this.rememberedResourceHitCount();
        String pickaxeDurabilitySummary = pickaxeDurability < 0
                ? "none"
                : pickaxeDurability == Integer.MAX_VALUE ? "unlimited" : String.valueOf(pickaxeDurability);
        return "supply="
                + supply
                + ",entrance="
                + entrance
                + ",dir="
                + direction
                + ",dig="
                + dig
                + ",routeTarget="
                + routeTarget
                + ",routeType="
                + this.expeditionRouteResumeType
                + ",routeAnchor="
                + routeAnchor
                + ",routeDepth="
                + this.expeditionRouteResumeGraphDepth
                + ",routeWaypoint="
                + Math.min(this.expeditionRouteResumeWaypointIndex, this.expeditionRouteResumeWaypoints.size())
                + "/"
                + this.expeditionRouteResumeWaypoints.size()
                + ",routeReached="
                + this.expeditionReachedRememberedRouteTarget
                + ",resupply="
                + this.expeditionResupplyActive
                + ",recovery="
                + this.expeditionRecoveryActive
                + ",recoveryWait="
                + this.expeditionRecoveryTicks
                + "/"
                + EXPEDITION_RECOVERY_TIMEOUT_TICKS
                + ",threatRetreat="
                + this.expeditionThreatRetreatActive
                + ",threatWait="
                + this.expeditionThreatRetreatTicks
                + "/"
                + EXPEDITION_THREAT_RETREAT_TIMEOUT_TICKS
                + ",lavaReroute="
                + this.expeditionLavaRerouteActive
                + ",lavaOrigin="
                + (this.expeditionLavaRerouteOrigin == null ? "none" : this.formatPos(this.expeditionLavaRerouteOrigin))
                + ",knownHazards="
                + (this.expeditionKnownHazards == null ? 0 : this.expeditionKnownHazards.size())
                + ",resourceHits="
                + resourceHits
                + ",supplyStatus="
                + this.expeditionSupplyStatus
                + ",moveWatch="
                + this.expeditionMoveWatchLabel
                + ":"
                + this.expeditionMoveWatchTicks
                + "/"
                + EXPEDITION_MOVE_STALL_TICKS
                + ",torches="
                + this.countTorches()
                + ",foodItems="
                + this.carriedFoodItems()
                + ",pickaxeDurability="
                + pickaxeDurabilitySummary
                + ",restockBlocked={torch="
                + this.expeditionTorchRestockUnavailable
                + ",tool="
                + this.expeditionToolRestockUnavailable
                + ",food="
                + this.expeditionFoodRestockUnavailable
                + "}"
                + ",supplyStock={"
                + this.expeditionSupplyStockSummary()
                + "}"
                + ",branch={main="
                + mainDirection
                + ",mainSteps="
                + this.expeditionBranchMainSteps
                + ",side="
                + sideDirection
                + ",sideLeft="
                + this.expeditionSideStepsRemaining
                + ",returning="
                + this.expeditionReturningFromSideBranch
                + ",torchSteps="
                + this.expeditionTunnelStepsSinceTorch
                + ",lastTorch="
                + (this.expeditionLastTorchPos == null ? "none" : this.formatPos(this.expeditionLastTorchPos))
                + "}";
    }

    private int rememberedResourceHitCount() {
        FriendTask task = this.friend.getCurrentTask();
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION) {
            return 0;
        }
        return this.findRememberedExpedition(task, this.expeditionMemoryDimension(task))
                .map(memory -> memory.resourceHits == null ? 0 : memory.resourceHits.size())
                .orElse(0);
    }

    private String expeditionSupplyStockSummary() {
        if (this.expeditionSupplyChest == null || !(this.friend.level() instanceof ServerLevel level)) {
            return "none";
        }
        if (!this.isLoaded(level, this.expeditionSupplyChest)
                || !(level.getBlockEntity(this.expeditionSupplyChest) instanceof Container chest)) {
            return "unavailable";
        }
        int torches = this.countContainerItems(chest, stack -> stack.is(Items.TORCH));
        int food = this.countContainerItems(chest, this::isExpeditionFood);
        int cookableFood = this.countContainerItems(chest, this::isCookableExpeditionFood);
        int fuel = this.countContainerItems(chest, this::isSupplyFurnaceFuel);
        int sticks = this.countContainerItems(chest, stack -> stack.is(Items.STICK));
        int planks = this.countContainerItems(chest, stack -> stack.is(ItemTags.PLANKS));
        int logs = this.countContainerItems(chest, stack -> stack.is(ItemTags.LOGS));
        int pickaxes = this.countContainerItems(chest, stack -> stack.is(ItemTags.PICKAXES));
        int craftingTables = this.countContainerItems(chest, stack -> stack.is(Items.CRAFTING_TABLE));
        int furnaces = this.countContainerItems(chest, stack -> stack.is(Items.FURNACE) || stack.is(Items.BLAST_FURNACE));
        return "torches="
                + torches
                + ",food="
                + food
                + ",cookableFood="
                + cookableFood
                + ",fuel="
                + fuel
                + ",sticks="
                + sticks
                + ",planks="
                + planks
                + ",logs="
                + logs
                + ",pickaxes="
                + pickaxes
                + ",tables="
                + craftingTables
                + ",furnaces="
                + furnaces;
    }

    private Optional<BlockPos> findCraftingTablePlacement(ServerLevel level, FriendTask task) {
        BlockPos center = this.friend.blockPosition();
        Optional<ServerPlayer> owner = this.findTaskPlayer(task);
        if (owner.isPresent() && this.friend.distanceTo(owner.get()) < 12.0D) {
            center = owner.get().blockPosition();
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    for (int y = -1; y <= 1; y++) {
                        BlockPos candidate = center.offset(x, y, z);
                        if (!this.canPlaceCraftingTableAt(level, candidate)) {
                            continue;
                        }
                        double distance = this.friend.blockPosition().distSqr(candidate);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate.immutable();
                        }
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<BlockPos> findSupplyCraftingTablePlacement(ServerLevel level, FriendTask task) {
        BlockPos center = this.expeditionSupplyChest == null
                ? (this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint)
                : this.expeditionSupplyChest;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    for (int y = -1; y <= 1; y++) {
                        BlockPos candidate = center.offset(x, y, z);
                        if (!this.canPlaceCraftingTableAt(level, candidate)) {
                            continue;
                        }
                        double distance = this.friend.blockPosition().distSqr(candidate);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate.immutable();
                        }
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean canPlaceCraftingTableAt(ServerLevel level, BlockPos pos) {
        return this.interaction.canPlaceBlockAt(level, pos);
    }

    private Optional<BlockPos> findSupplyChestPlacement(ServerLevel level, FriendTask task) {
        BlockPos center = this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint;
        Optional<ServerPlayer> owner = this.findTaskPlayer(task);
        if (this.expeditionSupplyPoint == null && owner.isPresent() && this.friend.distanceTo(owner.get()) < 12.0D) {
            center = owner.get().blockPosition();
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    for (int y = -1; y <= 1; y++) {
                        BlockPos candidate = center.offset(x, y, z);
                        if (!this.canPlaceBlockAt(level, candidate)) {
                            continue;
                        }
                        double distance = this.friend.blockPosition().distSqr(candidate);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate.immutable();
                        }
                    }
                }
            }
            if (best != null) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean canPlaceBlockAt(ServerLevel level, BlockPos pos) {
        return this.interaction.canPlaceBlockAt(level, pos);
    }

    private void setExpeditionSupplyStatus(String status) {
        this.expeditionSupplyStatus = status == null || status.isBlank() ? "idle" : status;
    }

    private void sayThrottled(String message) {
        if (this.workMessageCooldownTicks > 0) {
            return;
        }
        this.workMessageCooldownTicks = 60;
        this.say(message);
    }

    private Optional<BlockPos> findStandPositionNearBlock(ServerLevel level, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos candidate = target.offset(x, y, z);
                    if (candidate.equals(target) || candidate.below().equals(target)) {
                        continue;
                    }
                    if (!this.canStandAt(level, candidate)) {
                        continue;
                    }
                    if (!this.interaction.canReachBlockFrom(candidate, target)) {
                        continue;
                    }
                    if (!this.canNavigateToStandPosition(candidate)) {
                        continue;
                    }
                    double distanceToFriend = this.friend.blockPosition().distSqr(candidate);
                    if (distanceToFriend < bestDistance) {
                        bestDistance = distanceToFriend;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean exploreForWood(ServerLevel level) {
        if (this.woodExploreTarget == null
                || !this.canStandAt(level, this.woodExploreTarget)
                || this.friend.blockPosition().distSqr(this.woodExploreTarget) <= 9.0D
                || this.isWoodExploreMovementStalled()) {
            this.woodExploreTarget = this.findWoodExploreTarget(level).orElse(null);
            this.woodExploreLastPos = this.friend.blockPosition().immutable();
            this.woodExploreStallTicks = 0;
            if (this.woodExploreTarget == null) {
                this.sayThrottled("I do not see any reachable logs nearby and cannot find a safe place to explore toward.");
                return false;
            }
            this.sayThrottled("I do not see reachable logs nearby, so I am exploring toward "
                    + this.formatPos(this.woodExploreTarget)
                    + ".");
        }

        this.body.moveTo(this.woodExploreTarget, TASK_SPEED);
        return false;
    }

    private Optional<BlockPos> findWoodExploreTarget(ServerLevel level) {
        if (this.woodExploreOrigin == null) {
            this.woodExploreOrigin = this.friend.blockPosition().immutable();
            this.woodExploreCursor = 0;
            this.woodExploreRotation = this.friend.getRandom().nextInt(4);
        }

        for (int attempt = 0; attempt < WOOD_EXPLORE_ATTEMPTS_PER_TICK; attempt++) {
            BlockPos offset = this.nextWoodExploreOffset();
            int x = this.woodExploreOrigin.getX() + offset.getX();
            int z = this.woodExploreOrigin.getZ() + offset.getZ();
            Optional<BlockPos> stand = this.findWoodExploreStandPosition(level, x, z);
            if (stand.isPresent()) {
                return stand;
            }
        }

        // Keep the traversal local when terrain or unloaded chunks invalidate a spiral segment.
        // Otherwise the cursor keeps advancing into increasingly distant rings and never recovers.
        this.woodExploreOrigin = this.friend.blockPosition().immutable();
        this.woodExploreCursor = 0;
        for (int attempt = 0; attempt < WOOD_EXPLORE_ATTEMPTS_PER_TICK; attempt++) {
            BlockPos offset = this.nextWoodExploreOffset();
            int x = this.woodExploreOrigin.getX() + offset.getX();
            int z = this.woodExploreOrigin.getZ() + offset.getZ();
            Optional<BlockPos> stand = this.findWoodExploreStandPosition(level, x, z);
            if (stand.isPresent()) {
                return stand;
            }
        }
        return Optional.empty();
    }

    private BlockPos nextWoodExploreOffset() {
        int remaining = this.woodExploreCursor++;
        int ring = 1;
        while (remaining >= ring * 8) {
            remaining -= ring * 8;
            ring++;
        }

        int sideLength = ring * 2;
        int side = remaining / sideLength;
        int step = remaining % sideLength;
        int dx;
        int dz;
        switch (side) {
            case 0 -> {
                dx = ring;
                dz = -ring + step;
            }
            case 1 -> {
                dx = ring - step;
                dz = ring;
            }
            case 2 -> {
                dx = -ring;
                dz = ring - step;
            }
            default -> {
                dx = -ring + step;
                dz = -ring;
            }
        }

        for (int rotation = 0; rotation < this.woodExploreRotation; rotation++) {
            int rotated = dx;
            dx = -dz;
            dz = rotated;
        }
        return new BlockPos(dx * WOOD_EXPLORE_STEP, 0, dz * WOOD_EXPLORE_STEP);
    }

    private Optional<BlockPos> findWoodExploreStandPosition(ServerLevel level, int x, int z) {
        int baseY = this.woodExploreOrigin == null ? this.friend.blockPosition().getY() : this.woodExploreOrigin.getY();
        for (int yOffset = WOOD_EXPLORE_VERTICAL_UP; yOffset >= -WOOD_EXPLORE_VERTICAL_DOWN; yOffset--) {
            BlockPos candidate = new BlockPos(x, baseY + yOffset, z);
            if (!level.hasChunkAt(candidate)
                    || !this.canUseWoodExploreWaypoint(level, candidate)
                    || !this.canNavigateToStandPosition(candidate)) {
                continue;
            }
            return Optional.of(candidate.immutable());
        }
        return Optional.empty();
    }

    private boolean canUseWoodExploreWaypoint(ServerLevel level, BlockPos pos) {
        if (this.canStandAt(level, pos)) {
            return true;
        }
        if (!level.hasChunkAt(pos.above())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return feet.getFluidState().is(FluidTags.WATER)
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && head.getFluidState().isEmpty();
    }

    private boolean isWoodExploreMovementStalled() {
        BlockPos current = this.friend.blockPosition().immutable();
        if (this.woodExploreLastPos == null || !current.equals(this.woodExploreLastPos)) {
            this.woodExploreLastPos = current;
            this.woodExploreStallTicks = 0;
            return false;
        }
        this.woodExploreStallTicks++;
        return this.woodExploreStallTicks >= WOOD_EXPLORE_STALL_TICKS;
    }

    private void resetWoodExploreTarget() {
        this.woodExploreOrigin = null;
        this.woodExploreTarget = null;
        this.woodExploreLastPos = null;
        this.woodExploreStallTicks = 0;
        this.woodExploreCursor = 0;
        this.woodExploreRotation = 0;
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

    private boolean canStandAt(ServerLevel level, BlockPos pos) {
        return FriendPerception.canStandAt(level, pos);
    }

    private void attackNearbyHostile() {
        if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.combatTarget == null || !this.combatTarget.isAlive() || this.combatTarget.distanceTo(this.friend) > SEARCH_RADIUS + 4.0D) {
            this.combatTarget = this.friend.getPerception().nearestHostile(SEARCH_RADIUS).orElse(null);
            if (this.combatTarget == null) {
                this.say("I do not see any hostile mobs nearby.");
                this.friend.getFriendBrain().completeTask();
                return;
            }
            this.friend.setTarget(this.combatTarget);
        }

        if (!this.combatTarget.isAlive()) {
            this.say("The hostile mob is down.");
            this.friend.getFriendBrain().completeTask();
            return;
        }

        double distance = this.friend.distanceTo(this.combatTarget);
        if (distance > 2.4D) {
            this.body.moveTo(this.combatTarget, 1.2D);
            return;
        }

        this.body.stop();
        if (this.attackCooldownTicks <= 0 && this.body.attack(this.combatTarget)) {
            this.attackCooldownTicks = 20;
            this.friend.getHungerProvider().addExhaustion(0.1F);
        }
    }

    private void applyRememberedExpedition(FriendTask task) {
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION || !(this.friend.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Optional<ExpeditionMemory> remembered = this.findRememberedExpedition(task, this.expeditionMemoryDimension(task));
        if (remembered.isEmpty()) {
            return;
        }

        ExpeditionMemory memory = remembered.get();
        this.loadRememberedExpeditionHazards(serverLevel, memory);
        boolean reused = false;
        Optional<BlockPos> supplyPoint = this.rememberedValidPosition(
                serverLevel,
                memory.supplyPoint,
                pos -> this.canStandAt(serverLevel, pos),
                "supply point",
                task,
                expedition -> expedition.supplyPoint = null
        );
        if (supplyPoint.isPresent()) {
            this.expeditionSupplyPoint = supplyPoint.get();
            reused = true;
        }

        Optional<BlockPos> supplyChest = this.rememberedValidPosition(
                serverLevel,
                memory.supplyChest,
                pos -> this.isChestAt(serverLevel, pos),
                "supply chest",
                task,
                expedition -> expedition.supplyChest = null
        );
        if (supplyChest.isPresent()) {
            this.expeditionSupplyChest = supplyChest.get();
            reused = true;
        }

        Optional<BlockPos> supplyFurnace = this.rememberedValidPosition(
                serverLevel,
                memory.supplyFurnace,
                pos -> this.isFurnaceAt(serverLevel, pos),
                "supply furnace",
                task,
                expedition -> expedition.supplyFurnace = null
        );
        if (supplyFurnace.isPresent()) {
            this.furnaceStationTarget = supplyFurnace.get();
            reused = true;
        }

        Optional<BlockPos> supplyCraftingTable = this.rememberedValidPosition(
                serverLevel,
                memory.supplyCraftingTable,
                pos -> this.isCraftingTableAt(serverLevel, pos),
                "supply crafting table",
                task,
                expedition -> expedition.supplyCraftingTable = null
        );
        if (supplyCraftingTable.isPresent()) {
            this.craftingStationTarget = supplyCraftingTable.get();
            reused = true;
        }

        Optional<BlockPos> mineEntrance = this.rememberedValidPosition(
                serverLevel,
                memory.mineEntrance,
                pos -> this.canStandAt(serverLevel, pos),
                "mine entrance",
                task,
                expedition -> expedition.mineEntrance = null
        );
        if (mineEntrance.isPresent()) {
            this.expeditionMineEntrance = mineEntrance.get();
            this.expeditionMineEntranceFromMemory = true;
            this.expeditionReachedRememberedMineEntrance = this.friend.blockPosition().distSqr(this.expeditionMineEntrance) <= 9.0D;
            reused = true;
        }

        this.parseHorizontalDirection(memory.lastTunnelDirection).ifPresent(direction -> {
            this.expeditionDirection = direction;
            this.expeditionMainDirection = direction;
        });

        Optional<RememberedBranchRouteResume> bestRoute = this.bestUsableRememberedBranchRoute(serverLevel, task, memory);
        if (bestRoute.isPresent()) {
            this.applyRememberedBranchRouteResume(bestRoute.get());
            reused = true;
        }

        if (!reused) {
            return;
        }

        String details = "remembered route supply="
                + this.formatPos(this.expeditionSupplyPoint)
                + " chest="
                + this.formatPos(this.expeditionSupplyChest)
                + " furnace="
                + this.formatPos(this.furnaceStationTarget)
                + " table="
                + this.formatPos(this.craftingStationTarget)
                + " entrance="
                + this.formatPos(this.expeditionMineEntrance)
                + " routeTarget="
                + this.formatPos(this.expeditionRouteResumeTarget)
                + " routeType="
                + this.expeditionRouteResumeType
                + " routeAnchor="
                + this.formatPos(this.expeditionRouteResumeAnchor)
                + " routeDepth="
                + this.expeditionRouteResumeGraphDepth
                + " waypoints="
                + this.expeditionRouteResumeWaypoints.size();
        this.rememberPortableNote(task, "expedition reused " + details);
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "route_reused";
            expedition.supplyPoint = this.positionOf(this.expeditionSupplyPoint);
            expedition.supplyChest = this.positionOf(this.expeditionSupplyChest);
            expedition.supplyFurnace = this.positionOf(this.furnaceStationTarget);
            expedition.supplyCraftingTable = this.positionOf(this.craftingStationTarget);
            expedition.mineEntrance = this.positionOf(this.expeditionMineEntrance);
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addEvent(details);
        });
        this.sayThrottled("Reusing remembered expedition route for " + task.target() + ".");
    }

    private void applyRememberedBranchRouteResume(RememberedBranchRouteResume route) {
        this.expeditionRouteResumeTarget = route.target();
        this.expeditionRouteResumeAnchor = route.anchor();
        this.expeditionRouteResumeType = route.type();
        this.expeditionRouteResumeGraphDepth = route.graphDepth();
        this.expeditionRouteResumeWaypoints = new ArrayList<>(route.waypoints());
        this.expeditionRouteResumeWaypointIndex = 0;
        this.expeditionRouteResumeFromMemory = true;
        this.expeditionReachedRememberedRouteTarget = this.friend.blockPosition().distSqr(this.expeditionRouteResumeTarget) <= 9.0D;
        this.expeditionMainBranchStart = "side".equals(route.type()) && route.anchor() != null
                ? route.anchor()
                : route.target();
        this.parseHorizontalDirection(route.route().direction).ifPresent(direction -> {
            this.expeditionDirection = direction;
            this.expeditionMainDirection = direction;
        });
    }

    private boolean moveToRememberedMineEntrance(ServerLevel level, WorkStep step) {
        if (!this.expeditionMineEntranceFromMemory || this.expeditionReachedRememberedMineEntrance || this.expeditionMineEntrance == null) {
            return false;
        }
        if (!this.canStandAt(level, this.expeditionMineEntrance)) {
            this.expeditionMineEntranceFromMemory = false;
            this.expeditionReachedRememberedMineEntrance = true;
            this.rememberExpeditionInterrupted(this.friend.getCurrentTask(), "route_invalid", "remembered mine entrance is no longer standable");
            return false;
        }

        if (this.friend.blockPosition().distSqr(this.expeditionMineEntrance) > 9.0D) {
            step.running("moving to remembered mine entrance");
            this.sayThrottled("Returning to remembered mine entrance at " + this.formatPos(this.expeditionMineEntrance) + ".");
            if (this.isExpeditionMoveStalled(this.expeditionMineEntrance, "remembered entrance")) {
                this.body.stop();
                this.expeditionMineEntranceFromMemory = false;
                this.expeditionReachedRememberedMineEntrance = true;
                this.setExpeditionSupplyStatus("route reuse skipped: entrance stalled");
                this.rememberExpeditionInterrupted(this.friend.getCurrentTask(), "route_invalid", "remembered mine entrance movement stalled");
                return false;
            }
            this.body.moveTo(this.expeditionMineEntrance, TASK_SPEED);
            return true;
        }

        this.resetExpeditionMoveWatch();
        this.expeditionReachedRememberedMineEntrance = true;
        this.updateExpeditionMemory(this.friend.getCurrentTask(), expedition -> {
            expedition.status = "entrance_reached";
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.addRouteNote("reached remembered mine entrance at " + this.formatPos(this.expeditionMineEntrance));
            expedition.addEvent("remembered entrance reached");
        });
        return false;
    }

    private boolean moveToRememberedBranchRouteTarget(ServerLevel level, WorkStep step) {
        if (!this.expeditionRouteResumeFromMemory
                || this.expeditionReachedRememberedRouteTarget
                || this.expeditionRouteResumeTarget == null) {
            return false;
        }
        if (!this.canStandAt(level, this.expeditionRouteResumeTarget)) {
            FriendTask task = this.friend.getCurrentTask();
            this.invalidateRememberedBranchRoutesEndingAt(task, this.expeditionRouteResumeTarget, "selected route target is no longer standable");
            this.clearRememberedBranchRouteResume();
            this.reselectRememberedBranchRoute(level, task);
            return false;
        }
        if ("side".equals(this.expeditionRouteResumeType)
                && (this.expeditionRouteResumeAnchor == null || !this.canStandAt(level, this.expeditionRouteResumeAnchor))) {
            FriendTask task = this.friend.getCurrentTask();
            this.invalidateRememberedBranchRoutesStartingAt(task, this.expeditionRouteResumeAnchor, "selected side branch anchor is no longer standable");
            this.clearRememberedBranchRouteResume();
            this.reselectRememberedBranchRoute(level, task);
            this.rememberExpeditionInterrupted(this.friend.getCurrentTask(), "route_invalid", "remembered side branch anchor is no longer standable");
            return false;
        }

        if (this.moveAlongRememberedRouteWaypoints(level, step)) {
            return true;
        }

        if (this.friend.blockPosition().distSqr(this.expeditionRouteResumeTarget) > 9.0D) {
            step.running("moving to remembered branch route");
            this.sayThrottled("Returning to remembered branch route at " + this.formatPos(this.expeditionRouteResumeTarget) + ".");
            if (this.isExpeditionMoveStalled(this.expeditionRouteResumeTarget, "remembered route")) {
                FriendTask task = this.friend.getCurrentTask();
                this.invalidateRememberedBranchRoutesEndingAt(task, this.expeditionRouteResumeTarget, "remembered route movement stalled");
                this.clearRememberedBranchRouteResume();
                this.reselectRememberedBranchRoute(level, task);
                this.setExpeditionSupplyStatus("route reuse skipped: route stalled");
                step.running("remembered route stalled");
                return false;
            }
            this.body.moveTo(this.expeditionRouteResumeTarget, TASK_SPEED);
            return true;
        }

        this.resetExpeditionMoveWatch();
        this.expeditionReachedRememberedRouteTarget = true;
        this.expeditionLastBranchStepPos = this.friend.blockPosition().immutable();
        if ("side".equals(this.expeditionRouteResumeType) && this.expeditionRouteResumeAnchor != null) {
            this.expeditionReturningFromSideBranch = true;
            this.expeditionSideBranchAnchor = this.expeditionRouteResumeAnchor;
            this.expeditionSideBranchEnd = this.friend.blockPosition().immutable();
            this.expeditionSideStepsRemaining = 0;
            this.expeditionMainBranchStart = this.expeditionRouteResumeAnchor;
            this.expeditionBranchMainSteps = 0;
        } else {
            this.expeditionMainBranchStart = this.friend.blockPosition().immutable();
            this.expeditionBranchMainSteps = 0;
        }
        this.updateExpeditionMemory(this.friend.getCurrentTask(), expedition -> {
            expedition.status = "branch_route_reached";
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addRouteNote("reached remembered "
                    + this.expeditionRouteResumeType
                    + " branch route at "
                    + this.formatPos(this.expeditionRouteResumeTarget));
            expedition.addEvent("remembered branch route reached");
        });
        return false;
    }

    private void clearRememberedBranchRouteResume() {
        this.expeditionRouteResumeTarget = null;
        this.expeditionRouteResumeAnchor = null;
        this.expeditionRouteResumeType = "none";
        this.expeditionRouteResumeGraphDepth = 0;
        this.expeditionRouteResumeWaypoints = new ArrayList<>();
        this.expeditionRouteResumeWaypointIndex = 0;
        this.expeditionRouteResumeFromMemory = false;
        this.expeditionReachedRememberedRouteTarget = true;
        this.resetExpeditionMoveWatch();
    }

    private boolean reselectRememberedBranchRoute(ServerLevel level, FriendTask task) {
        Optional<ExpeditionMemory> remembered = this.findRememberedExpedition(task, this.expeditionMemoryDimension(task));
        if (remembered.isEmpty()) {
            return false;
        }
        Optional<RememberedBranchRouteResume> bestRoute = this.bestUsableRememberedBranchRoute(level, task, remembered.get());
        if (bestRoute.isEmpty()) {
            return false;
        }
        this.applyRememberedBranchRouteResume(bestRoute.get());
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "route_reselected";
            expedition.addRouteNote("reselected remembered route target="
                    + this.formatPos(this.expeditionRouteResumeTarget)
                    + " type="
                    + this.expeditionRouteResumeType
                    + " waypoints="
                    + this.expeditionRouteResumeWaypoints.size());
            expedition.addEvent("remembered route reselected");
        });
        return true;
    }

    private boolean moveAlongRememberedRouteWaypoints(ServerLevel level, WorkStep step) {
        while (this.expeditionRouteResumeWaypointIndex < this.expeditionRouteResumeWaypoints.size()) {
            BlockPos waypoint = this.expeditionRouteResumeWaypoints.get(this.expeditionRouteResumeWaypointIndex);
            if (waypoint == null) {
                this.expeditionRouteResumeWaypointIndex++;
                continue;
            }
            if (this.isLoaded(level, waypoint) && !this.canStandAt(level, waypoint)) {
                FriendTask task = this.friend.getCurrentTask();
                this.invalidateRememberedBranchRoutesEndingAt(task, waypoint, "route waypoint is no longer standable");
                this.clearRememberedBranchRouteResume();
                this.reselectRememberedBranchRoute(level, task);
                step.running("reselecting remembered route");
                return true;
            }
            if (!this.isLoaded(level, waypoint)) {
                step.running("moving to unloaded remembered route waypoint");
                this.sayThrottled("Moving toward remembered route waypoint "
                        + (this.expeditionRouteResumeWaypointIndex + 1)
                        + "/"
                        + this.expeditionRouteResumeWaypoints.size()
                        + " at "
                        + this.formatPos(waypoint)
                        + ".");
                if (this.isExpeditionMoveStalled(waypoint, "remembered waypoint")) {
                    FriendTask task = this.friend.getCurrentTask();
                    this.invalidateRememberedBranchRoutesEndingAt(task, waypoint, "remembered route waypoint movement stalled");
                    this.clearRememberedBranchRouteResume();
                    this.reselectRememberedBranchRoute(level, task);
                    this.setExpeditionSupplyStatus("route reuse skipped: waypoint stalled");
                    step.running("remembered waypoint stalled");
                    return true;
                }
                this.body.moveTo(waypoint, TASK_SPEED);
                return true;
            }
            if (this.friend.blockPosition().distSqr(waypoint) <= 9.0D) {
                this.resetExpeditionMoveWatch();
                this.expeditionRouteResumeWaypointIndex++;
                continue;
            }
            step.running("following remembered route waypoint");
            this.sayThrottled("Following remembered route waypoint "
                    + (this.expeditionRouteResumeWaypointIndex + 1)
                    + "/"
                    + this.expeditionRouteResumeWaypoints.size()
                    + " at "
                    + this.formatPos(waypoint)
                    + ".");
            if (this.isExpeditionMoveStalled(waypoint, "remembered waypoint")) {
                FriendTask task = this.friend.getCurrentTask();
                this.invalidateRememberedBranchRoutesEndingAt(task, waypoint, "remembered route waypoint movement stalled");
                this.clearRememberedBranchRouteResume();
                this.reselectRememberedBranchRoute(level, task);
                this.setExpeditionSupplyStatus("route reuse skipped: waypoint stalled");
                step.running("remembered waypoint stalled");
                return true;
            }
            this.body.moveTo(waypoint, TASK_SPEED);
            return true;
        }
        return false;
    }

    private Optional<RememberedBranchRouteResume> bestUsableRememberedBranchRoute(ServerLevel level, FriendTask task, ExpeditionMemory memory) {
        if (memory == null || memory.branchRoutes == null || memory.branchRoutes.isEmpty()) {
            return Optional.empty();
        }
        RememberedBranchRouteResume best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        RememberedRouteGraph routeGraph = this.rememberedRouteGraph(memory);
        for (int index = memory.branchRoutes.size() - 1; index >= 0; index--) {
            int routeIndex = index;
            ExpeditionMemory.BranchRoute route = memory.branchRoutes.get(index);
            String routeType = route == null ? "none" : this.normalizeRouteType(route.type);
            if (route == null
                    || (!"main".equals(routeType) && !"side".equals(routeType))
                    || !"completed".equalsIgnoreCase(route.status)
                    || route.completedSteps <= 0) {
                continue;
            }
            if (route.end == null) {
                this.invalidateRememberedBranchRoute(task, routeIndex, route, "missing route endpoint");
                continue;
            }
            Optional<BlockPos> target = this.rememberedPosition(level, route.end)
                    .filter(pos -> this.canStandAt(level, pos));
            if (target.isEmpty()) {
                this.rememberedPosition(level, route.end)
                        .ifPresent(pos -> this.invalidateRememberedBranchRoute(
                                task,
                                routeIndex,
                                route,
                                "route endpoint is no longer standable at " + this.formatPos(pos)
                        ));
                continue;
            }
            if (this.isNearRememberedHazard(level, memory, target.get(), EXPEDITION_HAZARD_ROUTE_AVOIDANCE_RADIUS)) {
                this.invalidateRememberedBranchRoute(
                        task,
                        routeIndex,
                        route,
                        "route endpoint is near a remembered hazard at " + this.formatPos(target.get())
                );
                continue;
            }
            Optional<BlockPos> anchor = Optional.empty();
            if ("side".equals(routeType)) {
                if (route.start == null) {
                    this.invalidateRememberedBranchRoute(task, routeIndex, route, "missing side branch anchor");
                    continue;
                }
                anchor = this.rememberedPosition(level, route.start)
                        .filter(pos -> this.canStandAt(level, pos));
                if (anchor.isEmpty()) {
                    this.rememberedPosition(level, route.start)
                            .ifPresent(pos -> this.invalidateRememberedBranchRoute(
                                    task,
                                    routeIndex,
                                    route,
                                    "side branch anchor is no longer standable at " + this.formatPos(pos)
                            ));
                    continue;
                }
                if (this.isNearRememberedHazard(level, memory, anchor.get(), EXPEDITION_HAZARD_ROUTE_AVOIDANCE_RADIUS)) {
                    this.invalidateRememberedBranchRoute(
                            task,
                            routeIndex,
                            route,
                            "side branch anchor is near a remembered hazard at " + this.formatPos(anchor.get())
                    );
                    continue;
                }
            }
            int graphDepth = routeGraph.depthByPosition().getOrDefault(this.memoryPositionKey(route.end), Math.max(0, route.completedSteps));
            List<BlockPos> waypoints = this.rememberedRouteGraphWaypoints(level, routeGraph, route, target.get());
            if (this.isAnyNearRememberedHazard(level, memory, waypoints, EXPEDITION_HAZARD_ROUTE_AVOIDANCE_RADIUS)) {
                continue;
            }
            RememberedBranchRouteResume candidate = new RememberedBranchRouteResume(route, target.get(), anchor.orElse(null), routeType, graphDepth, waypoints);
            double score = this.scoreRememberedBranchRoute(memory, candidate);
            if (best == null
                    || score > bestScore
                    || (score == bestScore && route.updatedAtEpochMillis > best.route().updatedAtEpochMillis)) {
                best = candidate;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private RememberedRouteGraph rememberedRouteGraph(ExpeditionMemory memory) {
        Map<String, Integer> depthByPosition = new HashMap<>();
        Map<String, ExpeditionMemory.BranchRoute> predecessorByEnd = new HashMap<>();
        if (memory == null || memory.branchRoutes == null || memory.branchRoutes.isEmpty()) {
            return new RememberedRouteGraph(depthByPosition, predecessorByEnd);
        }

        for (ExpeditionMemory.BranchRoute route : memory.branchRoutes) {
            if (!this.isCompletedRouteWithEndpoints(route)) {
                continue;
            }
            depthByPosition.putIfAbsent(this.memoryPositionKey(route.start), 0);
        }

        for (int pass = 0; pass < memory.branchRoutes.size(); pass++) {
            boolean changed = false;
            for (ExpeditionMemory.BranchRoute route : memory.branchRoutes) {
                if (!this.isCompletedRouteWithEndpoints(route)) {
                    continue;
                }
                String startKey = this.memoryPositionKey(route.start);
                String endKey = this.memoryPositionKey(route.end);
                int startDepth = depthByPosition.getOrDefault(startKey, 0);
                int routeDepth = startDepth + Math.max(1, route.completedSteps);
                if (routeDepth > depthByPosition.getOrDefault(endKey, -1)) {
                    depthByPosition.put(endKey, routeDepth);
                    predecessorByEnd.put(endKey, route);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return new RememberedRouteGraph(depthByPosition, predecessorByEnd);
    }

    private List<BlockPos> rememberedRouteGraphWaypoints(
            ServerLevel level,
            RememberedRouteGraph graph,
            ExpeditionMemory.BranchRoute selectedRoute,
            BlockPos selectedTarget
    ) {
        List<ExpeditionMemory.BranchRoute> reversedRoutes = new ArrayList<>();
        String key = this.memoryPositionKey(selectedRoute.end);
        int guardLimit = graph.predecessorByEnd().size() + 1;
        for (int guard = 0; guard < guardLimit; guard++) {
            ExpeditionMemory.BranchRoute route = graph.predecessorByEnd().get(key);
            if (route == null || route.start == null || route.end == null) {
                break;
            }
            reversedRoutes.add(route);
            String nextKey = this.memoryPositionKey(route.start);
            if (nextKey.equals(key)) {
                break;
            }
            key = nextKey;
        }

        List<BlockPos> waypoints = new ArrayList<>();
        for (int index = reversedRoutes.size() - 1; index >= 0; index--) {
            ExpeditionMemory.BranchRoute route = reversedRoutes.get(index);
            this.rememberedPosition(level, route.end)
                    .filter(pos -> this.canStandAt(level, pos))
                    .ifPresent(pos -> this.addDistinctWaypoint(waypoints, pos));
        }
        this.addDistinctWaypoint(waypoints, selectedTarget);
        return waypoints;
    }

    private void addDistinctWaypoint(List<BlockPos> waypoints, BlockPos waypoint) {
        if (waypoint == null) {
            return;
        }
        if (!waypoints.isEmpty() && waypoints.get(waypoints.size() - 1).equals(waypoint)) {
            return;
        }
        waypoints.add(waypoint.immutable());
    }

    private boolean isCompletedRouteWithEndpoints(ExpeditionMemory.BranchRoute route) {
        return route != null
                && route.start != null
                && route.end != null
                && "completed".equalsIgnoreCase(route.status)
                && route.completedSteps > 0;
    }

    private String memoryPositionKey(ExpeditionMemory.Position position) {
        if (position == null) {
            return "none";
        }
        return position.dimension + "|" + position.x + "|" + position.y + "|" + position.z;
    }

    private double scoreRememberedBranchRoute(ExpeditionMemory memory, RememberedBranchRouteResume candidate) {
        BlockPos origin = this.expeditionMineEntrance != null
                ? this.expeditionMineEntrance
                : (this.expeditionSupplyPoint == null ? this.friend.blockPosition() : this.expeditionSupplyPoint);
        double progress = origin.distSqr(candidate.target());
        double travelPenalty = this.friend.blockPosition().distSqr(candidate.target()) * 0.05D;
        double completedStepScore = Math.max(0, candidate.route().completedSteps) * 16.0D;
        double graphDepthScore = Math.max(0, candidate.graphDepth()) * 24.0D;
        double typeBonus = "main".equals(candidate.type()) ? 16.0D : 8.0D;
        double resourceHitScore = this.scoreRememberedRouteResourceHits(memory, candidate);
        return progress + graphDepthScore + completedStepScore + typeBonus + resourceHitScore - travelPenalty;
    }

    private double scoreRememberedRouteResourceHits(ExpeditionMemory memory, RememberedBranchRouteResume candidate) {
        if (memory == null || memory.resourceHits == null || memory.resourceHits.isEmpty() || candidate == null) {
            return 0.0D;
        }
        double radiusSqr = EXPEDITION_RESOURCE_HIT_ROUTE_RADIUS * EXPEDITION_RESOURCE_HIT_ROUTE_RADIUS;
        String dimension = this.currentDimension();
        double score = 0.0D;
        for (ExpeditionMemory.ResourceHit hit : memory.resourceHits) {
            if (hit == null || hit.position == null || !hit.position.isInDimension(dimension)) {
                continue;
            }
            BlockPos hitPos = hit.position.asBlockPos();
            double targetDistance = candidate.target().distSqr(hitPos);
            double anchorDistance = candidate.anchor() == null ? Double.MAX_VALUE : candidate.anchor().distSqr(hitPos);
            double nearestDistance = Math.min(targetDistance, anchorDistance);
            if (nearestDistance > radiusSqr) {
                continue;
            }
            double proximity = (radiusSqr - nearestDistance) / Math.max(1.0D, radiusSqr);
            double amountScore = this.resourceHitScoreAmount(hit) * 18.0D;
            double routeTypeScore = candidate.type().equalsIgnoreCase(hit.routeType) ? 24.0D : 0.0D;
            double directionScore = candidate.route().direction != null
                    && candidate.route().direction.equalsIgnoreCase(hit.direction)
                    ? 24.0D
                    : 0.0D;
            score += 120.0D * proximity + amountScore + routeTypeScore + directionScore;
        }
        return score;
    }

    private int resourceHitScoreAmount(ExpeditionMemory.ResourceHit hit) {
        return Math.min(EXPEDITION_RESOURCE_HIT_SCORE_AMOUNT_CAP, Math.max(1, hit == null ? 1 : hit.amount));
    }

    private Optional<ExpeditionMemory> findRememberedExpedition(FriendTask task, String dimension) {
        if (task == null || task.target() == null || task.target().isBlank()) {
            return Optional.empty();
        }
        if (task.playerUuid() != null) {
            String playerName = task.playerName() == null || task.playerName().isBlank() ? "unknown" : task.playerName();
            return JsonMemoryStore.findExpedition(task.playerUuid(), playerName, task.target(), dimension);
        }
        return this.friend.getOwnerPlayer()
                .flatMap(player -> JsonMemoryStore.findExpedition(
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        task.target(),
                        dimension
                ));
    }

    private Optional<BlockPos> rememberedPosition(ServerLevel level, ExpeditionMemory.Position position) {
        if (position == null || !position.isInDimension(level.dimension().location().toString())) {
            return Optional.empty();
        }
        BlockPos pos = position.asBlockPos();
        if (!level.hasChunkAt(pos)) {
            return Optional.empty();
        }
        return Optional.of(pos);
    }

    private boolean isAnyNearRememberedHazard(
            ServerLevel level,
            ExpeditionMemory memory,
            List<BlockPos> positions,
            double radius
    ) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        for (BlockPos pos : positions) {
            if (this.isNearRememberedHazard(level, memory, pos, radius)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearRememberedHazard(ServerLevel level, ExpeditionMemory memory, BlockPos pos, double radius) {
        if (memory == null || memory.hazards == null || memory.hazards.isEmpty() || pos == null) {
            return false;
        }
        String dimension = level.dimension().location().toString();
        double radiusSqr = Math.max(0.0D, radius) * Math.max(0.0D, radius);
        for (ExpeditionMemory.HazardNote hazard : memory.hazards) {
            if (hazard == null || hazard.position == null || !hazard.position.isInDimension(dimension)) {
                continue;
            }
            if (pos.distSqr(hazard.position.asBlockPos()) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearCurrentExpeditionHazard(ServerLevel level, BlockPos pos) {
        if (this.expeditionKnownHazards == null || this.expeditionKnownHazards.isEmpty() || pos == null) {
            return false;
        }
        double radiusSqr = EXPEDITION_HAZARD_ROUTE_AVOIDANCE_RADIUS * EXPEDITION_HAZARD_ROUTE_AVOIDANCE_RADIUS;
        for (BlockPos hazard : this.expeditionKnownHazards) {
            if (hazard != null && pos.distSqr(hazard) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private void loadRememberedExpeditionHazards(ServerLevel level, ExpeditionMemory memory) {
        this.expeditionKnownHazards = new ArrayList<>();
        if (memory == null || memory.hazards == null || memory.hazards.isEmpty()) {
            return;
        }
        String dimension = level.dimension().location().toString();
        for (ExpeditionMemory.HazardNote hazard : memory.hazards) {
            if (hazard == null || hazard.position == null || !hazard.position.isInDimension(dimension)) {
                continue;
            }
            this.addKnownExpeditionHazard(hazard.position.asBlockPos());
        }
    }

    private void addKnownExpeditionHazard(BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (this.expeditionKnownHazards == null) {
            this.expeditionKnownHazards = new ArrayList<>();
        }
        BlockPos immutable = pos.immutable();
        if (this.expeditionKnownHazards.stream().noneMatch(existing -> existing.equals(immutable))) {
            this.expeditionKnownHazards.add(immutable);
            while (this.expeditionKnownHazards.size() > EXPEDITION_KNOWN_HAZARD_CACHE_SIZE) {
                this.expeditionKnownHazards.remove(0);
            }
        }
    }

    private Optional<BlockPos> rememberedValidPosition(
            ServerLevel level,
            ExpeditionMemory.Position position,
            Predicate<BlockPos> validator,
            String label,
            FriendTask task,
            Consumer<ExpeditionMemory> invalidator
    ) {
        Optional<BlockPos> remembered = this.rememberedPosition(level, position);
        if (remembered.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = remembered.get();
        if (validator.test(pos)) {
            return remembered;
        }
        this.invalidateRememberedExpeditionPosition(task, label, pos, invalidator);
        return Optional.empty();
    }

    private void invalidateRememberedExpeditionPosition(
            FriendTask task,
            String label,
            BlockPos pos,
            Consumer<ExpeditionMemory> invalidator
    ) {
        this.updateExpeditionMemory(task, expedition -> {
            if (invalidator != null) {
                invalidator.accept(expedition);
            }
            expedition.status = "route_partially_invalidated";
            expedition.addRouteNote("invalidated remembered "
                    + label
                    + " at "
                    + this.formatPos(pos)
                    + " because the loaded world no longer matches it");
            expedition.addEvent("invalidated remembered " + label);
        });
    }

    private void invalidateRememberedBranchRoute(
            FriendTask task,
            int routeIndex,
            ExpeditionMemory.BranchRoute route,
            String reason
    ) {
        this.updateExpeditionMemory(task, expedition -> {
            ExpeditionMemory.BranchRoute stored = this.findStoredBranchRoute(expedition, routeIndex, route).orElse(null);
            if (stored != null) {
                stored.status = "invalidated";
                stored.updatedAtEpochMillis = System.currentTimeMillis();
            }
            expedition.status = "route_partially_invalidated";
            expedition.addRouteNote("invalidated remembered "
                    + this.describeBranchRoute(route)
                    + ": "
                    + reason);
            expedition.addEvent("invalidated branch route");
        });
    }

    private void invalidateRememberedBranchRoutesEndingAt(FriendTask task, BlockPos endpoint, String reason) {
        ExpeditionMemory.Position target = ExpeditionMemory.Position.of(this.currentDimension(), endpoint);
        this.invalidateRememberedBranchRoutesMatching(
                task,
                route -> route != null
                        && "completed".equalsIgnoreCase(route.status)
                        && this.sameMemoryPosition(route.end, target),
                reason
        );
    }

    private void invalidateRememberedBranchRoutesStartingAt(FriendTask task, BlockPos start, String reason) {
        ExpeditionMemory.Position target = ExpeditionMemory.Position.of(this.currentDimension(), start);
        this.invalidateRememberedBranchRoutesMatching(
                task,
                route -> route != null
                        && "completed".equalsIgnoreCase(route.status)
                        && this.sameMemoryPosition(route.start, target),
                reason
        );
    }

    private void invalidateRememberedBranchRoutesMatching(
            FriendTask task,
            Predicate<ExpeditionMemory.BranchRoute> matcher,
            String reason
    ) {
        this.updateExpeditionMemory(task, expedition -> {
            if (expedition.branchRoutes == null || matcher == null) {
                return;
            }
            int invalidated = 0;
            for (ExpeditionMemory.BranchRoute route : expedition.branchRoutes) {
                if (!matcher.test(route)) {
                    continue;
                }
                route.status = "invalidated";
                route.updatedAtEpochMillis = System.currentTimeMillis();
                invalidated++;
            }
            if (invalidated <= 0) {
                return;
            }
            expedition.status = "route_partially_invalidated";
            expedition.addRouteNote("invalidated "
                    + invalidated
                    + " remembered branch route(s): "
                    + reason);
            expedition.addEvent("invalidated branch route group");
        });
    }

    private Optional<ExpeditionMemory.BranchRoute> findStoredBranchRoute(
            ExpeditionMemory expedition,
            int routeIndex,
            ExpeditionMemory.BranchRoute route
    ) {
        if (expedition == null || expedition.branchRoutes == null || route == null) {
            return Optional.empty();
        }
        if (routeIndex >= 0 && routeIndex < expedition.branchRoutes.size()) {
            ExpeditionMemory.BranchRoute candidate = expedition.branchRoutes.get(routeIndex);
            if (this.sameBranchRouteIdentity(candidate, route)) {
                return Optional.of(candidate);
            }
        }
        return expedition.branchRoutes.stream()
                .filter(candidate -> this.sameBranchRouteIdentity(candidate, route))
                .findFirst();
    }

    private boolean sameBranchRouteIdentity(ExpeditionMemory.BranchRoute first, ExpeditionMemory.BranchRoute second) {
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(this.normalizeRouteType(first.type), this.normalizeRouteType(second.type))
                && Objects.equals(first.direction, second.direction)
                && this.sameMemoryPosition(first.start, second.start)
                && this.sameMemoryPosition(first.end, second.end)
                && first.plannedSteps == second.plannedSteps
                && first.completedSteps == second.completedSteps;
    }

    private boolean sameMemoryPosition(ExpeditionMemory.Position first, ExpeditionMemory.Position second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.x == second.x
                && first.y == second.y
                && first.z == second.z
                && Objects.equals(first.dimension, second.dimension);
    }

    private String describeBranchRoute(ExpeditionMemory.BranchRoute route) {
        if (route == null) {
            return "branch route";
        }
        return this.normalizeRouteType(route.type)
                + " branch route start="
                + this.describeMemoryPosition(route.start)
                + " end="
                + this.describeMemoryPosition(route.end)
                + " steps="
                + route.completedSteps
                + "/"
                + route.plannedSteps;
    }

    private String describeMemoryPosition(ExpeditionMemory.Position position) {
        return position == null ? "none" : position.summary();
    }

    private Optional<Direction> parseHorizontalDirection(String rawDirection) {
        if (rawDirection == null || rawDirection.isBlank()) {
            return Optional.empty();
        }
        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isVertical() && direction.getName().equalsIgnoreCase(rawDirection.trim())) {
                return Optional.of(direction);
            }
        }
        return Optional.empty();
    }

    private void rememberExpeditionStarted(FriendTask task) {
        Optional<MiningExpeditionPlan> parsedPlan = this.parseMiningExpeditionPlan(task);
        MiningExpeditionPlan plan = parsedPlan.orElse(null);
        String dimension = plan == null ? this.currentDimension() : plan.targetDimension;
        this.updateExpeditionMemory(task, dimension, expedition -> {
            expedition.resourceId = task.target();
            expedition.requestedAmount = Math.max(1, task.amount());
            expedition.status = "started";
            expedition.startedAtEpochMillis = System.currentTimeMillis();
            expedition.completedAtEpochMillis = 0L;
            expedition.minedAmount = 0;
            if (plan != null) {
                expedition.strategyMode = plan.strategyMode;
                expedition.targetDimension = plan.targetDimension;
                expedition.targetYMin = plan.preferredYMin;
                expedition.targetYMax = plan.preferredYMax;
                expedition.requiredTool = plan.requiredTool;
            }
            expedition.supplyPoint = this.positionOf(this.expeditionSupplyPoint);
            expedition.mineEntrance = this.positionOf(this.expeditionMineEntrance);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? null : this.expeditionDirection.getName();
            expedition.addEvent("started amount=" + Math.max(1, task.amount()) + " reason=" + task.reason());
            expedition.addRouteNote("start supply="
                    + this.formatPos(this.expeditionSupplyPoint)
                    + " entrance="
                    + this.formatPos(this.expeditionMineEntrance));
        });
    }

    private void rememberExpeditionCompleted(FriendTask task, String reason) {
        if (task == null || task.type() != FriendTaskType.MINING_EXPEDITION) {
            return;
        }
        int minedAmount = MiningTargetRegistry.find(task.target())
                .map(target -> this.friend.countInventoryItems(target.inventoryMatcher()))
                .orElse(0);
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "succeeded";
            expedition.completedAtEpochMillis = System.currentTimeMillis();
            expedition.minedAmount = Math.max(expedition.minedAmount, minedAmount);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addEvent("completed: " + reason);
        });
    }

    private void rememberExpeditionInterrupted(FriendTask task, String status, String reason) {
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = status;
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addEvent(status + ": " + reason);
        });
    }

    private void rememberExpeditionLayerReached(FriendTask task, LayerTarget target) {
        this.updateExpeditionMemory(task, target.dimension(), expedition -> {
            expedition.status = "layer_reached";
            expedition.targetDimension = target.dimension();
            expedition.targetYMin = target.minY();
            expedition.targetYMax = target.maxY();
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addRouteNote("reached target layer at " + this.formatPos(this.friend.blockPosition()));
            expedition.addEvent("reached layer " + target.minY() + ".." + target.maxY());
        });
    }

    private void rememberExpeditionMiningSuccess(FriendTask task, String resourceId, int minedTotal, int minedDelta, BlockPos hitPos) {
        BlockPos memoryPos = hitPos == null ? this.friend.blockPosition() : hitPos;
        this.updateExpeditionMemory(task, expedition -> {
            expedition.resourceId = resourceId;
            expedition.status = "target_found";
            expedition.minedAmount = Math.max(expedition.minedAmount, minedTotal);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addRouteNote("mined " + resourceId + " at " + this.formatPos(memoryPos));
            expedition.addResourceHit(ExpeditionMemory.ResourceHit.create(
                    resourceId,
                    this.currentDimension(),
                    memoryPos,
                    this.currentExpeditionRouteType(),
                    this.currentExpeditionRouteDirectionName(),
                    minedDelta
            ));
            expedition.addEvent("mined " + resourceId + " amount=" + minedDelta + " total=" + minedTotal);
        });
    }

    private void rememberExpeditionSupplyChest(FriendTask task, BlockPos chestPos, String event) {
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "supply_ready";
            expedition.supplyPoint = this.positionOf(this.expeditionSupplyPoint);
            expedition.supplyChest = this.positionOf(chestPos);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.addEvent(event + " at " + this.formatPos(chestPos));
        });
    }

    private void rememberExpeditionFurnaceReady(FriendTask task, BlockPos furnacePos, String event) {
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "furnace_ready";
            expedition.supplyFurnace = this.positionOf(furnacePos);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.addEvent(event + " at " + this.formatPos(furnacePos));
        });
    }

    private void rememberExpeditionSupplyCraftingTable(FriendTask task, BlockPos tablePos, String event) {
        this.updateExpeditionMemory(task, expedition -> {
            ExpeditionMemory.Position tablePosition = this.positionOf(tablePos);
            boolean changed = !this.sameMemoryPosition(expedition.supplyCraftingTable, tablePosition);
            expedition.status = "supply_table_ready";
            expedition.supplyCraftingTable = tablePosition;
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            if (changed) {
                expedition.addEvent(event + " at " + this.formatPos(tablePos));
            }
        });
    }

    private void rememberExpeditionResupplied(FriendTask task) {
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "resupplied";
            expedition.supplyChest = this.positionOf(this.expeditionSupplyChest);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.addEvent("unloaded inventory freeSlots=" + this.emptyInventorySlots());
        });
    }

    private void rememberExpeditionSupplySmelted(FriendTask task, String label) {
        this.updateExpeditionMemory(task, expedition -> {
            expedition.status = "supply_smelting";
            expedition.supplyFurnace = this.positionOf(this.furnaceStationTarget);
            expedition.supplyChest = this.positionOf(this.expeditionSupplyChest);
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.addEvent("supply furnace smelted " + label);
        });
    }

    private void rememberExpeditionBranchNote(String note) {
        this.rememberExpeditionRouteNote("branch_mining", note);
    }

    private void rememberExpeditionRouteNote(String status, String note) {
        this.updateExpeditionMemory(this.friend.getCurrentTask(), expedition -> {
            expedition.status = status == null || status.isBlank() ? "route_updated" : status;
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addRouteNote(note);
        });
    }

    private void rememberExpeditionBranchRoute(
            String type,
            Direction direction,
            BlockPos start,
            BlockPos end,
            int plannedSteps,
            int completedSteps,
            String status
    ) {
        if (start == null || end == null) {
            return;
        }
        String directionName = direction == null ? "unknown" : direction.getName();
        this.updateExpeditionMemory(this.friend.getCurrentTask(), expedition -> {
            expedition.status = "branch_mining";
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = directionName;
            expedition.addBranchRoute(ExpeditionMemory.BranchRoute.create(
                    type,
                    directionName,
                    this.currentDimension(),
                    start,
                    end,
                    plannedSteps,
                    completedSteps,
                    status
            ));
        });
    }

    private void rememberExpeditionTorchPlaced(BlockPos torchPos) {
        this.updateExpeditionMemory(this.friend.getCurrentTask(), expedition -> {
            expedition.status = "route_lit";
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.addRouteNote("placed torch at " + this.formatPos(torchPos));
        });
    }

    private void rememberExpeditionHazardAvoided(String note) {
        this.rememberExpeditionHazardAvoided("hazard", this.friend.blockPosition(), note);
    }

    private void rememberExpeditionHazardAvoided(String type, BlockPos pos, String note) {
        this.addKnownExpeditionHazard(pos);
        this.updateExpeditionMemory(this.friend.getCurrentTask(), expedition -> {
            expedition.status = "hazard_avoidance";
            expedition.lastKnownPosition = this.positionOf(this.friend.blockPosition());
            expedition.lastTunnelDirection = this.expeditionDirection == null ? expedition.lastTunnelDirection : this.expeditionDirection.getName();
            expedition.addRouteNote("avoided " + note);
            expedition.addHazard(ExpeditionMemory.HazardNote.create(type, this.currentDimension(), pos, note));
        });
    }

    private void updateExpeditionMemory(FriendTask task, Consumer<ExpeditionMemory> updater) {
        this.updateExpeditionMemory(task, this.expeditionMemoryDimension(task), updater);
    }

    private void updateExpeditionMemory(FriendTask task, String dimension, Consumer<ExpeditionMemory> updater) {
        if (task == null
                || task.type() != FriendTaskType.MINING_EXPEDITION
                || task.target() == null
                || task.target().isBlank()
                || updater == null) {
            return;
        }
        if (task.playerUuid() != null) {
            String playerName = task.playerName() == null || task.playerName().isBlank() ? "unknown" : task.playerName();
            JsonMemoryStore.updateExpedition(task.playerUuid(), playerName, task.target(), dimension, updater);
            return;
        }
        this.friend.getOwnerPlayer().ifPresent(player -> JsonMemoryStore.updateExpedition(
                player.getUUID(),
                player.getGameProfile().getName(),
                task.target(),
                dimension,
                updater
        ));
    }

    private String expeditionMemoryDimension(FriendTask task) {
        if (task == null) {
            return this.currentDimension();
        }
        return this.parseMiningExpeditionPlan(task)
                .map(plan -> plan.targetDimension)
                .filter(dimension -> dimension != null && !dimension.isBlank())
                .orElseGet(this::currentDimension);
    }

    private String currentDimension() {
        if (this.friend.level() instanceof ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString();
        }
        return "minecraft:overworld";
    }

    private ExpeditionMemory.Position positionOf(BlockPos pos) {
        return ExpeditionMemory.Position.of(this.currentDimension(), pos);
    }

    private Optional<ServerPlayer> findTaskPlayer(FriendTask task) {
        if (this.friend.level() instanceof ServerLevel serverLevel && task.playerUuid() != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(task.playerUuid());
            if (player != null) {
                this.friend.setOwner(player);
                return Optional.of(player);
            }
        }
        return this.friend.getOwnerPlayer();
    }

    private void rememberResourceKnowledge(String resourceId, String hint, String source) {
        FriendTask task = this.friend.getCurrentTask();
        if (task != null && task.playerUuid() != null) {
            String playerName = task.playerName() == null || task.playerName().isBlank() ? "unknown" : task.playerName();
            JsonMemoryStore.rememberResource(task.playerUuid(), playerName, resourceId, hint, source);
            return;
        }
        this.friend.getOwnerPlayer().ifPresent(player -> JsonMemoryStore.rememberResource(
                player.getUUID(),
                player.getGameProfile().getName(),
                resourceId,
                hint,
                source
        ));
    }

    private void rememberPortableNote(FriendTask task, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        if (task != null && task.playerUuid() != null) {
            String playerName = task.playerName() == null || task.playerName().isBlank() ? "unknown" : task.playerName();
            JsonMemoryStore.appendPortableNote(task.playerUuid(), playerName, note);
            return;
        }
        this.friend.getOwnerPlayer().ifPresent(player -> JsonMemoryStore.appendPortableNote(
                player.getUUID(),
                player.getGameProfile().getName(),
                note
        ));
    }

    private static boolean isBreakableLog(ServerLevel level, BlockPos pos) {
        return FriendPerception.isBreakableLog(level, pos);
    }

    private String formatPos(BlockPos pos) {
        if (pos == null) {
            return "none";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private enum ResupplyResult {
        COMPLETE,
        WORKING,
        FAILED
    }

    private enum SupplyRestockResult {
        NONE,
        RESTOCKED,
        WORKING
    }

    private enum SupplyCraftResult {
        NONE,
        CRAFTED,
        WORKING
    }

    private enum SupplyStationResult {
        READY,
        WORKING,
        UNAVAILABLE
    }

    private enum SupplySmeltResult {
        NONE,
        DONE,
        WORKING,
        SKIPPED
    }

    private enum SupplyFurnaceResult {
        READY,
        WORKING,
        UNAVAILABLE
    }

    private enum ConstructionTravelResult {
        WORKING,
        COMPLETE,
        FAILED
    }

    private record SupplySmeltTarget(
            String label,
            Predicate<ItemStack> inputMatcher,
            Predicate<ItemStack> outputMatcher,
            boolean regularFurnaceOnly
    ) {
    }

    private record RememberedBranchRouteResume(
            ExpeditionMemory.BranchRoute route,
            BlockPos target,
            BlockPos anchor,
            String type,
            int graphDepth,
            List<BlockPos> waypoints
    ) {
    }

    private record RememberedRouteGraph(
            Map<String, Integer> depthByPosition,
            Map<String, ExpeditionMemory.BranchRoute> predecessorByEnd
    ) {
    }

    private record LayerTarget(String dimension, int minY, int maxY) {
    }
}
