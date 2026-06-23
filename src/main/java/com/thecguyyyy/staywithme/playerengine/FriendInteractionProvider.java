package com.thecguyyyy.staywithme.playerengine;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.survival.SurvivalWorldInteractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.Optional;

public class FriendInteractionProvider {
    private static final String PLAYERENGINE_INTERACTION_PROVIDER_CLASS =
            "com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider";

    private final FriendEntity friend;
    private final SurvivalWorldInteractor fallback;
    private String lastBackend = "fallback_survival";
    private BlockPos playerEngineBreakingPos;
    private Item playerEngineBreakingToolItem;
    private String playerEngineBreakingToolName;
    private int playerEngineBreakingToolCount;
    private SurvivalWorldInteractor.ToolBreakEvent pendingPlayerEngineToolBreakEvent;

    public FriendInteractionProvider(FriendEntity friend) {
        this.friend = friend;
        this.fallback = new SurvivalWorldInteractor(friend);
    }

    public boolean canReachBlock(BlockPos pos) {
        return this.fallback.canReachBlock(pos);
    }

    public boolean canReachBlockFrom(BlockPos feetPos, BlockPos target) {
        return this.fallback.canReachBlockFrom(feetPos, target);
    }

    public SurvivalWorldInteractor.BreakResult tickBreakBlock(ServerLevel level, BlockPos pos) {
        Optional<SurvivalWorldInteractor.BreakResult> playerEngineResult = this.tryPlayerEngineTickBreakBlock(level, pos);
        if (playerEngineResult.isPresent()) {
            return playerEngineResult.get();
        }
        return this.fallback.tickBreakBlockToInventory(level, pos);
    }

    public SurvivalWorldInteractor.BreakResult tickBreakBlockBelow(ServerLevel level, BlockPos pos) {
        this.cancelPlayerEngineBreaking(level);
        return this.fallback.tickBreakBlockBelowToInventory(level, pos);
    }

    public void cancelBreakBlock() {
        if (this.friend.level() instanceof ServerLevel serverLevel) {
            this.cancelPlayerEngineBreaking(serverLevel);
        }
        this.fallback.reset();
    }

    public Optional<SurvivalWorldInteractor.ToolBreakEvent> consumeToolBreakEvent() {
        SurvivalWorldInteractor.ToolBreakEvent playerEngineEvent = this.pendingPlayerEngineToolBreakEvent;
        if (playerEngineEvent != null) {
            this.pendingPlayerEngineToolBreakEvent = null;
            return Optional.of(playerEngineEvent);
        }
        return this.fallback.consumeToolBreakEvent();
    }

    public boolean placeBlock(ServerLevel level, BlockPos pos, Block block, Predicate<ItemStack> inventoryMatcher) {
        return this.fallback.placeBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean placeBlockReplacingLiquid(
            ServerLevel level,
            BlockPos pos,
            Block block,
            Predicate<ItemStack> inventoryMatcher
    ) {
        return this.fallback.placeBlockReplacingLiquidFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean placePillarBlock(ServerLevel level, BlockPos pos, Block block, Predicate<ItemStack> inventoryMatcher) {
        return this.fallback.placePillarBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean placeBridgeBlock(
            ServerLevel level,
            BlockPos pos,
            BlockPos fromFeet,
            Block block,
            Predicate<ItemStack> inventoryMatcher
    ) {
        return this.fallback.placeBridgeBlockFromInventory(level, pos, fromFeet, block, inventoryMatcher);
    }

    public boolean canPlaceBlockAt(ServerLevel level, BlockPos pos) {
        return this.fallback.canPlaceBlockAt(level, pos);
    }

    public boolean attackEntity(Entity target) {
        return this.friend.attackAsCompanion(target);
    }

    public void swingMainHand() {
        this.friend.swing(InteractionHand.MAIN_HAND);
    }

    public InteractionResult useItemOnBlock(Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            Optional<InteractionResult> playerEngineResult = this.tryPlayerEngineInteractBlock(
                    serverLevel,
                    stack,
                    hand,
                    hitResult
            );
            if (playerEngineResult.isPresent()) {
                return playerEngineResult.get();
            }
        }
        return InteractionResult.PASS;
    }

    public String status() {
        return "interaction=" + this.lastBackend;
    }

    private Optional<InteractionResult> tryPlayerEngineInteractBlock(
            ServerLevel level,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        Optional<Object> manager = this.playerEngineInteractionManager(level);
        if (manager.isEmpty()) {
            return Optional.empty();
        }

        try {
            Method interactBlock = manager.get().getClass().getMethod(
                    "interactBlock",
                    LivingEntity.class,
                    Level.class,
                    ItemStack.class,
                    InteractionHand.class,
                    BlockHitResult.class
            );
            Object result = interactBlock.invoke(manager.get(), this.friend, level, stack, hand, hitResult);
            if (result instanceof InteractionResult interactionResult) {
                this.lastBackend = "playerengine_interaction_manager";
                return Optional.of(interactionResult);
            }
            this.lastBackend = "playerengine_interaction_invalid_result";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            this.lastBackend = "playerengine_interaction_failed(" + error.getClass().getSimpleName() + ")";
        }
        return Optional.empty();
    }

    private Optional<SurvivalWorldInteractor.BreakResult> tryPlayerEngineTickBreakBlock(
            ServerLevel level,
            BlockPos pos
    ) {
        Optional<Object> manager = this.playerEngineInteractionManager(level);
        if (manager.isEmpty()) {
            return Optional.empty();
        }
        if (!this.canPlayerEngineBreakBlock(level, pos)) {
            return Optional.empty();
        }

        try {
            if (this.playerEngineBreakingPos != null && !this.playerEngineBreakingPos.equals(pos)) {
                this.cancelPlayerEngineBreaking(level);
            }

            Method processBlockBreakingAction = manager.get().getClass().getMethod(
                    "processBlockBreakingAction",
                    BlockPos.class,
                    Action.class,
                    Direction.class,
                    int.class,
                    int.class
            );

            if (!pos.equals(this.playerEngineBreakingPos)) {
                this.playerEngineBreakingPos = pos.immutable();
                this.snapshotPlayerEngineBreakingTool();
                processBlockBreakingAction.invoke(
                        manager.get(),
                        pos,
                        Action.START_DESTROY_BLOCK,
                        Direction.UP,
                        level.getMaxBuildHeight(),
                        0
                );
            }

            Vec3 center = Vec3.atCenterOf(pos);
            this.friend.getLookControl().setLookAt(center.x, center.y, center.z);
            this.friend.swing(InteractionHand.MAIN_HAND);

            if (level.getBlockState(pos).isAir()) {
                this.finishPlayerEngineBreaking();
                return Optional.of(SurvivalWorldInteractor.BreakResult.BROKEN);
            }

            int progress = this.playerEngineBreakingProgress(manager.get());
            if (progress >= 7) {
                processBlockBreakingAction.invoke(
                        manager.get(),
                        pos,
                        Action.STOP_DESTROY_BLOCK,
                        Direction.UP,
                        level.getMaxBuildHeight(),
                        0
                );
                if (level.getBlockState(pos).isAir()) {
                    this.finishPlayerEngineBreaking();
                    return Optional.of(SurvivalWorldInteractor.BreakResult.BROKEN);
                }
            }

            this.lastBackend = "playerengine_interaction_manager_breaking";
            return Optional.of(SurvivalWorldInteractor.BreakResult.WORKING);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            this.lastBackend = "playerengine_break_failed(" + error.getClass().getSimpleName() + ")";
            this.cancelPlayerEngineBreaking(level);
            return Optional.empty();
        }
    }

    private Optional<Object> playerEngineInteractionManager(ServerLevel level) {
        if (!IntegrationStatus.isPlayerEngineLoaded() || !StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get()) {
            this.lastBackend = "fallback_survival";
            return Optional.empty();
        }

        try {
            Class<?> providerClass = Class.forName(PLAYERENGINE_INTERACTION_PROVIDER_CLASS);
            if (!providerClass.isInstance(this.friend)) {
                this.lastBackend = "fallback_survival";
                return Optional.empty();
            }

            Object manager = providerClass
                    .getMethod("getInteractionManager")
                    .invoke(this.friend);
            if (manager == null) {
                this.lastBackend = "playerengine_interaction_unavailable";
                return Optional.empty();
            }

            Method setWorld = manager.getClass().getMethod("setWorld", ServerLevel.class);
            setWorld.invoke(manager, level);
            return Optional.of(manager);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            this.lastBackend = "playerengine_interaction_failed(" + error.getClass().getSimpleName() + ")";
        }
        return Optional.empty();
    }

    private boolean canPlayerEngineBreakBlock(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos) || !this.canReachBlock(pos)) {
            this.cancelPlayerEngineBreaking(level);
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
            this.cancelPlayerEngineBreaking(level);
            return false;
        }

        this.friend.getInventoryProvider().selectBestToolFor(state);
        ItemStack selectedTool = this.friend.getMainHandItem();
        if (state.requiresCorrectToolForDrops()
                && (selectedTool.isEmpty() || !selectedTool.isCorrectToolForDrops(state))) {
            this.cancelPlayerEngineBreaking(level);
            return false;
        }
        return true;
    }

    private int playerEngineBreakingProgress(Object manager) throws ReflectiveOperationException {
        Object progress = manager.getClass().getMethod("getBlockBreakingProgress").invoke(manager);
        if (progress instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    private void cancelPlayerEngineBreaking(ServerLevel level) {
        if (this.playerEngineBreakingPos != null) {
            Optional<Object> manager = this.playerEngineInteractionManager(level);
            if (manager.isPresent()) {
                try {
                    Method processBlockBreakingAction = manager.get().getClass().getMethod(
                            "processBlockBreakingAction",
                            BlockPos.class,
                            Action.class,
                            Direction.class,
                            int.class,
                            int.class
                    );
                    processBlockBreakingAction.invoke(
                            manager.get(),
                            this.playerEngineBreakingPos,
                            Action.ABORT_DESTROY_BLOCK,
                            Direction.UP,
                            level.getMaxBuildHeight(),
                            0
                    );
                } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                    this.lastBackend = "playerengine_break_cancel_failed";
                }
            }
            level.destroyBlockProgress(this.friend.getId(), this.playerEngineBreakingPos, -1);
        }
        this.playerEngineBreakingPos = null;
        this.playerEngineBreakingToolItem = null;
        this.playerEngineBreakingToolName = null;
        this.playerEngineBreakingToolCount = 0;
    }

    private void snapshotPlayerEngineBreakingTool() {
        ItemStack tool = this.friend.getMainHandItem();
        if (tool.isEmpty()) {
            this.playerEngineBreakingToolItem = null;
            this.playerEngineBreakingToolName = null;
            this.playerEngineBreakingToolCount = 0;
            return;
        }
        this.playerEngineBreakingToolItem = tool.getItem();
        this.playerEngineBreakingToolName = tool.getHoverName().getString();
        this.playerEngineBreakingToolCount = tool.getCount();
    }

    private void finishPlayerEngineBreaking() {
        this.detectPlayerEngineToolBreak();
        this.friend.getHungerProvider().addExhaustion(0.025F);
        this.playerEngineBreakingPos = null;
        this.playerEngineBreakingToolItem = null;
        this.playerEngineBreakingToolName = null;
        this.playerEngineBreakingToolCount = 0;
        this.lastBackend = "playerengine_interaction_manager_broke";
    }

    private void detectPlayerEngineToolBreak() {
        if (this.playerEngineBreakingToolItem == null) {
            return;
        }
        ItemStack currentTool = this.friend.getMainHandItem();
        if (currentTool.isEmpty()
                || currentTool.getItem() != this.playerEngineBreakingToolItem
                || currentTool.getCount() < this.playerEngineBreakingToolCount) {
            this.pendingPlayerEngineToolBreakEvent = new SurvivalWorldInteractor.ToolBreakEvent(
                    this.playerEngineBreakingToolItem,
                    this.playerEngineBreakingToolName == null ? this.playerEngineBreakingToolItem.getDescriptionId() : this.playerEngineBreakingToolName
            );
        }
    }
}
