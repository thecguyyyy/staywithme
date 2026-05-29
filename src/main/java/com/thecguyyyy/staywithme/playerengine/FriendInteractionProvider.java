package com.thecguyyyy.staywithme.playerengine;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.survival.SurvivalWorldInteractor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Predicate;

public class FriendInteractionProvider {
    private final FriendEntity friend;
    private final SurvivalWorldInteractor fallback;

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
        return this.fallback.tickBreakBlockToInventory(level, pos);
    }

    public void cancelBreakBlock() {
        this.fallback.reset();
    }

    public boolean placeBlock(ServerLevel level, BlockPos pos, Block block, Predicate<ItemStack> inventoryMatcher) {
        return this.fallback.placeBlockFromInventory(level, pos, block, inventoryMatcher);
    }

    public boolean canPlaceBlockAt(ServerLevel level, BlockPos pos) {
        return this.fallback.canPlaceBlockAt(level, pos);
    }

    public boolean attackEntity(Entity target) {
        return this.friend.doHurtTarget(target);
    }

    public void swingMainHand() {
        this.friend.swing(InteractionHand.MAIN_HAND);
    }

    public InteractionResult useItemOnBlock(Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    public String status() {
        return "interaction=fallback_survival";
    }
}
