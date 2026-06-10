package com.thecguyyyy.staywithme.survival;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class SurvivalWorldInteractor {
    private static final double BLOCK_REACH = 4.5D;
    private static final double BLOCK_REACH_SQR = BLOCK_REACH * BLOCK_REACH;

    private final FriendEntity friend;
    private BlockPos breakingPos;
    private float breakingProgress;
    private int breakingTicks;
    private ToolBreakEvent pendingToolBreakEvent;

    public SurvivalWorldInteractor(FriendEntity friend) {
        this.friend = friend;
    }

    public void reset() {
        this.clearBreakingProgress();
    }

    public Optional<ToolBreakEvent> consumeToolBreakEvent() {
        ToolBreakEvent event = this.pendingToolBreakEvent;
        this.pendingToolBreakEvent = null;
        return Optional.ofNullable(event);
    }

    public boolean canReachBlock(BlockPos pos) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return false;
        }
        if (pos.equals(this.friend.blockPosition().below())) {
            return false;
        }
        return this.canReachBlockFromEye(level, this.friend.getEyePosition(), pos);
    }

    public boolean canReachBlockFrom(BlockPos feetPos, BlockPos target) {
        if (!(this.friend.level() instanceof ServerLevel level)) {
            return false;
        }
        double eyeOffset = Math.max(0.1D, this.friend.getEyePosition().y - this.friend.getY());
        Vec3 eye = Vec3.atBottomCenterOf(feetPos).add(0.0D, eyeOffset, 0.0D);
        return this.canReachBlockFromEye(level, eye, target);
    }

    public BreakResult tickBreakBlockToInventory(ServerLevel level, BlockPos pos) {
        return this.tickBreakBlockToInventory(level, pos, false);
    }

    public BreakResult tickBreakBlockBelowToInventory(ServerLevel level, BlockPos pos) {
        if (!this.canSafelyBreakBlockBelow(level, pos)) {
            this.clearBreakingProgress();
            return BreakResult.NOT_IN_REACH;
        }
        return this.tickBreakBlockToInventory(level, pos, true);
    }

    private BreakResult tickBreakBlockToInventory(ServerLevel level, BlockPos pos, boolean allowBlockBelow) {
        boolean reachable = allowBlockBelow
                ? this.canReachBlockFromEye(level, this.friend.getEyePosition(), pos)
                : this.canReachBlock(pos);
        if (!level.hasChunkAt(pos) || !reachable) {
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
        if (!level.hasChunkAt(pos) || !this.canReachBlock(pos) || !this.canPlaceBlockAt(level, pos)) {
            return false;
        }
        return this.finishPlaceBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean placeBlockReplacingLiquidFromInventory(
            ServerLevel level,
            BlockPos pos,
            Block block,
            Predicate<ItemStack> inventoryMatcher
    ) {
        if (!level.hasChunkAt(pos)
                || !this.canReachBlock(pos)
                || !this.canReplaceLiquidAt(level, pos)) {
            return false;
        }
        return this.finishPlaceBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean placePillarBlockFromInventory(
            ServerLevel level,
            BlockPos pos,
            Block block,
            Predicate<ItemStack> inventoryMatcher
    ) {
        BlockPos feet = this.friend.blockPosition();
        if (!level.hasChunkAt(pos)
                || (!pos.equals(feet) && !pos.equals(feet.below()))
                || this.friend.getY() < pos.getY() + 0.9D
                || !this.canReachBlockFromEye(level, this.friend.getEyePosition(), pos)
                || !this.canPlaceBlockAt(level, pos)) {
            return false;
        }
        return this.finishPlaceBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean placeBridgeBlockFromInventory(
            ServerLevel level,
            BlockPos pos,
            BlockPos fromFeet,
            Block block,
            Predicate<ItemStack> inventoryMatcher
    ) {
        if (!this.canPlaceBridgeBlockAt(level, pos, fromFeet)
                || !this.canReachBridgePlacement(level, pos, fromFeet)) {
            return false;
        }
        return this.finishPlaceBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    private boolean finishPlaceBlockFromInventory(
            ServerLevel level,
            BlockPos pos,
            Block block,
            Predicate<ItemStack> inventoryMatcher
    ) {
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

    private boolean canSafelyBreakBlockBelow(ServerLevel level, BlockPos pos) {
        BlockPos feet = this.friend.blockPosition();
        if (pos == null
                || !pos.equals(feet.below())
                || !level.hasChunkAt(pos)
                || !level.hasChunkAt(pos.below())
                || !level.hasChunkAt(pos.above())) {
            return false;
        }
        BlockState target = level.getBlockState(pos);
        BlockState support = level.getBlockState(pos.below());
        return !target.getCollisionShape(level, pos).isEmpty()
                && !target.hasBlockEntity()
                && target.getDestroySpeed(level, pos) >= 0.0F
                && !(target.getBlock() instanceof FallingBlock)
                && target.getFluidState().isEmpty()
                && !support.isAir()
                && support.getFluidState().isEmpty()
                && !(support.getBlock() instanceof FallingBlock)
                && support.isFaceSturdy(level, pos.below(), Direction.UP)
                && !this.hasAdjacentFluid(level, pos)
                && !this.hasAdjacentFluid(level, pos.below());
    }

    private boolean hasAdjacentFluid(ServerLevel level, BlockPos center) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = center.relative(direction);
            if (!level.hasChunkAt(adjacent) || !level.getFluidState(adjacent).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean canPlaceBlockAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        return state.canBeReplaced()
                && state.getFluidState().isEmpty()
                && !below.isAir()
                && below.getFluidState().isEmpty()
                && below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private boolean canReplaceLiquidAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (state.getFluidState().is(FluidTags.WATER)
                || state.getFluidState().is(FluidTags.LAVA))
                && !state.hasBlockEntity()
                && this.hasAdjacentPlacementFace(level, pos);
    }

    private boolean hasAdjacentPlacementFace(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = pos.relative(direction);
            if (!level.hasChunkAt(supportPos)) {
                continue;
            }
            BlockState support = level.getBlockState(supportPos);
            if (support.getFluidState().isEmpty()
                    && support.isFaceSturdy(level, supportPos, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    public boolean canPlaceBridgeBlockAt(ServerLevel level, BlockPos pos, BlockPos fromFeet) {
        if (pos == null
                || fromFeet == null
                || !this.friend.blockPosition().equals(fromFeet)
                || !level.hasChunkAt(pos)
                || !level.hasChunkAt(fromFeet.below())) {
            return false;
        }
        BlockPos nextFeet = pos.above();
        int horizontalDelta = Math.abs(nextFeet.getX() - fromFeet.getX())
                + Math.abs(nextFeet.getZ() - fromFeet.getZ());
        if (horizontalDelta != 1 || nextFeet.getY() != fromFeet.getY()) {
            return false;
        }

        BlockState target = level.getBlockState(pos);
        BlockPos supportPos = fromFeet.below();
        BlockState support = level.getBlockState(supportPos);
        return target.canBeReplaced()
                && !target.hasBlockEntity()
                && !target.getFluidState().is(FluidTags.LAVA)
                && !support.isAir()
                && support.getFluidState().isEmpty()
                && !(support.getBlock() instanceof FallingBlock)
                && support.isFaceSturdy(level, supportPos, Direction.UP);
    }

    private boolean canReachBridgePlacement(ServerLevel level, BlockPos pos, BlockPos fromFeet) {
        BlockPos supportPos = fromFeet.below();
        int dx = pos.getX() - supportPos.getX();
        int dz = pos.getZ() - supportPos.getZ();
        Direction face = Direction.fromDelta(dx, 0, dz);
        if (face == null) {
            return false;
        }
        Vec3 supportFace = Vec3.atCenterOf(supportPos).add(
                face.getStepX() * 0.5D,
                0.0D,
                face.getStepZ() * 0.5D
        );
        return this.canHitTargetBlock(level, this.friend.getEyePosition(), supportFace, supportPos);
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
            Item brokenItem = tool.getItem();
            String brokenName = tool.getHoverName().getString();
            int countBeforeDamage = tool.getCount();
            tool.hurtAndBreak(1, this.friend, entity -> entity.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            if (tool.isEmpty() || tool.getCount() < countBeforeDamage) {
                this.pendingToolBreakEvent = new ToolBreakEvent(brokenItem, brokenName);
            }
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

    private boolean canReachBlockFromEye(ServerLevel level, Vec3 eye, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.canBeReplaced() && state.getCollisionShape(level, pos).isEmpty()) {
            return this.canReachOpenPositionFromEye(level, eye, Vec3.atCenterOf(pos));
        }

        Vec3 center = Vec3.atCenterOf(pos);
        if (this.canHitTargetBlock(level, eye, center, pos)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            Vec3 faceCenter = center.add(
                    direction.getStepX() * 0.5D,
                    direction.getStepY() * 0.5D,
                    direction.getStepZ() * 0.5D
            );
            if (this.canHitTargetBlock(level, eye, faceCenter, pos)) {
                return true;
            }
        }
        return false;
    }

    private boolean canReachOpenPositionFromEye(ServerLevel level, Vec3 eye, Vec3 target) {
        if (eye.distanceToSqr(target) > BLOCK_REACH_SQR) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                this.friend
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private boolean canHitTargetBlock(ServerLevel level, Vec3 eye, Vec3 target, BlockPos pos) {
        if (eye.distanceToSqr(target) > BLOCK_REACH_SQR) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                this.friend
        ));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    public enum BreakResult {
        NOT_IN_REACH,
        WORKING,
        BROKEN,
        FAILED
    }

    public record ToolBreakEvent(Item item, String displayName) {
    }
}
