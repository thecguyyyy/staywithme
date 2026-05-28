package com.thecguyyyy.staywithme.survival;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

public class SurvivalWorldInteractor {
    private static final double BLOCK_REACH = 4.5D;
    private static final double BLOCK_REACH_SQR = BLOCK_REACH * BLOCK_REACH;

    private final FriendEntity friend;
    private BlockPos breakingPos;
    private float breakingProgress;
    private int breakingTicks;

    public SurvivalWorldInteractor(FriendEntity friend) {
        this.friend = friend;
    }

    public void reset() {
        this.clearBreakingProgress();
    }

    public boolean canReachBlock(BlockPos pos) {
        Vec3 eye = this.friend.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        return eye.distanceToSqr(center) <= BLOCK_REACH_SQR;
    }

    public BreakResult tickBreakBlockToInventory(ServerLevel level, BlockPos pos) {
        if (!this.canReachBlock(pos)) {
            this.clearBreakingProgress();
            return BreakResult.NOT_IN_REACH;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
            this.clearBreakingProgress();
            return BreakResult.FAILED;
        }
        this.friend.getInventoryProvider().selectBestToolFor(state);
        ItemStack selectedTool = this.friend.getMainHandItem();
        if (state.requiresCorrectToolForDrops()
                && (selectedTool.isEmpty() || !selectedTool.isCorrectToolForDrops(state))) {
            this.clearBreakingProgress();
            return BreakResult.FAILED;
        }

        if (!pos.equals(this.breakingPos)) {
            this.clearBreakingProgress();
            this.breakingPos = pos.immutable();
        }

        Vec3 center = Vec3.atCenterOf(pos);
        this.friend.getLookControl().setLookAt(center.x, center.y, center.z);
        this.breakingTicks++;
        if (this.breakingTicks % 5 == 0) {
            this.friend.swing(InteractionHand.MAIN_HAND);
        }

        float delta = this.calculateDestroyProgress(level, pos, state);
        if (delta <= 0.0F) {
            this.clearBreakingProgress();
            return BreakResult.FAILED;
        }

        this.breakingProgress += delta;
        int progressStage = Math.min(9, (int) (this.breakingProgress * 10.0F));
        level.destroyBlockProgress(this.friend.getId(), pos, progressStage);

        if (this.breakingProgress < 1.0F) {
            return BreakResult.WORKING;
        }

        boolean broken = this.finishBreakIntoInventory(level, pos, state);
        this.clearBreakingProgress();
        return broken ? BreakResult.BROKEN : BreakResult.FAILED;
    }

    public boolean placeBlockFromInventory(ServerLevel level, BlockPos pos, Block block, Predicate<ItemStack> inventoryMatcher) {
        if (!this.canReachBlock(pos) || !this.canPlaceBlockAt(level, pos)) {
            return false;
        }
        int slot = this.friend.getInventoryProvider().findFirstSlot(inventoryMatcher);
        if (slot < 0) {
            return false;
        }
        this.friend.getInventoryProvider().setSelectedSlot(slot);
        if (!level.setBlock(pos, block.defaultBlockState(), 3)) {
            return false;
        }
        ItemStack removed = this.friend.getInventoryProvider().removeItem(slot, 1);
        if (removed.isEmpty()) {
            level.removeBlock(pos, false);
            return false;
        }
        this.friend.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public boolean canPlaceBlockAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        return state.canBeReplaced()
                && !below.isAir()
                && below.getFluidState().isEmpty()
                && below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private float calculateDestroyProgress(ServerLevel level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            return 0.0F;
        }

        ItemStack tool = this.friend.getMainHandItem();
        float speed = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
        if (speed < 1.0F) {
            speed = 1.0F;
        }
        boolean canHarvest = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));
        float divisor = canHarvest ? 30.0F : 100.0F;
        return speed / hardness / divisor;
    }

    private boolean finishBreakIntoInventory(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, this.friend, this.friend.getMainHandItem());
        level.levelEvent(2001, pos, Block.getId(state));

        if (!level.destroyBlock(pos, false, this.friend)) {
            return false;
        }

        for (ItemStack drop : drops) {
            ItemStack remainder = this.friend.insertIntoInventory(drop);
            if (!remainder.isEmpty()) {
                Block.popResource(level, this.friend.blockPosition(), remainder);
            }
        }
        this.damageMainHandTool();
        this.friend.getHungerProvider().addExhaustion(0.025F);
        return true;
    }

    private void damageMainHandTool() {
        ItemStack tool = this.friend.getMainHandItem();
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, this.friend, entity -> entity.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            this.friend.getFriendInventory().setChanged();
        }
    }

    private void clearBreakingProgress() {
        if (this.breakingPos != null && this.friend.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(this.friend.getId(), this.breakingPos, -1);
        }
        this.breakingPos = null;
        this.breakingProgress = 0.0F;
        this.breakingTicks = 0;
    }

    public enum BreakResult {
        NOT_IN_REACH,
        WORKING,
        BROKEN,
        FAILED
    }
}
