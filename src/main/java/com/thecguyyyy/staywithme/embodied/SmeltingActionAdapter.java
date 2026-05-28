package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

public class SmeltingActionAdapter {
    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;

    private final FriendEntity friend;
    private String lastRecipe = "none";
    private String lastResult = "idle";

    public SmeltingActionAdapter(FriendEntity friend) {
        this.friend = friend;
    }

    public SmeltResult smeltOneAtFurnace(
            ServerLevel level,
            BlockPos furnacePos,
            Predicate<ItemStack> resultMatcher,
            Predicate<ItemStack> fuelMatcher
    ) {
        if (furnacePos == null || !this.isSupportedFurnace(level, furnacePos)) {
            this.lastRecipe = "none";
            this.lastResult = "missing_furnace";
            return SmeltResult.MISSING_STATION;
        }
        if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) {
            this.lastRecipe = "none";
            this.lastResult = "missing_furnace_block_entity";
            return SmeltResult.MISSING_STATION;
        }

        SmeltResult collected = this.tryCollectOutput(level, furnacePos, furnace, resultMatcher);
        if (collected != SmeltResult.WORKING) {
            return collected;
        }

        SmeltResult inputResult = this.ensureInput(level, furnacePos, furnace, resultMatcher);
        if (inputResult != SmeltResult.WORKING) {
            return inputResult;
        }

        SmeltResult fuelResult = this.ensureFuel(level, furnacePos, furnace, fuelMatcher);
        if (fuelResult != SmeltResult.WORKING) {
            return fuelResult;
        }

        furnace.setChanged();
        this.markFurnaceChanged(level, furnacePos, furnace);
        this.lastResult = "waiting_for_real_furnace_tick";
        return SmeltResult.WORKING;
    }

    public String status() {
        return "smelt=" + this.lastResult + ",recipe=" + this.lastRecipe;
    }

    private SmeltResult tryCollectOutput(ServerLevel level, BlockPos furnacePos, AbstractFurnaceBlockEntity furnace, Predicate<ItemStack> resultMatcher) {
        ItemStack output = furnace.getItem(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return SmeltResult.WORKING;
        }
        if (!resultMatcher.test(output)) {
            this.lastResult = "furnace_output_blocked";
            return SmeltResult.STATION_BLOCKED;
        }

        ItemStack toCollect = output.copyWithCount(1);
        ItemStack remainder = this.friend.insertIntoInventory(toCollect);
        if (!remainder.isEmpty()) {
            this.lastResult = "inventory_full";
            return SmeltResult.INVENTORY_FULL;
        }

        this.faceAndSwing(furnace.getBlockPos());
        furnace.removeItem(OUTPUT_SLOT, 1);
        this.markFurnaceChanged(level, furnacePos, furnace);
        this.lastRecipe = "furnace_block_entity";
        this.lastResult = "collected " + toCollect.getHoverName().getString();
        return SmeltResult.SMELTED;
    }

    private SmeltResult ensureInput(ServerLevel level, BlockPos furnacePos, AbstractFurnaceBlockEntity furnace, Predicate<ItemStack> resultMatcher) {
        ItemStack furnaceInput = furnace.getItem(INPUT_SLOT);
        if (!furnaceInput.isEmpty()) {
            Optional<String> existingRecipe = this.findMatchingRecipeId(level, furnacePos, furnaceInput, resultMatcher);
            if (existingRecipe.isEmpty()) {
                this.lastRecipe = "none";
                this.lastResult = "furnace_input_blocked";
                return SmeltResult.STATION_BLOCKED;
            }
            this.lastRecipe = existingRecipe.get();
            return SmeltResult.WORKING;
        }

        Optional<SmeltInputPlan> plan = this.findInputInInventory(level, furnacePos, resultMatcher);
        if (plan.isEmpty()) {
            this.lastRecipe = "none";
            this.lastResult = "missing_input";
            return SmeltResult.MISSING_INPUT;
        }

        ItemStack removed = this.friend.getInventoryProvider().removeItem(plan.get().inventorySlot(), 1);
        if (removed.isEmpty()) {
            this.lastRecipe = plan.get().recipeId();
            this.lastResult = "missing_input";
            return SmeltResult.MISSING_INPUT;
        }
        this.faceAndSwing(furnace.getBlockPos());
        furnace.setItem(INPUT_SLOT, removed.copyWithCount(1));
        this.markFurnaceChanged(level, furnacePos, furnace);
        this.lastRecipe = plan.get().recipeId();
        this.lastResult = "inserted_input " + removed.getHoverName().getString();
        return SmeltResult.WORKING;
    }

    private SmeltResult ensureFuel(ServerLevel level, BlockPos furnacePos, AbstractFurnaceBlockEntity furnace, Predicate<ItemStack> fuelMatcher) {
        ItemStack furnaceFuel = furnace.getItem(FUEL_SLOT);
        if (!furnaceFuel.isEmpty()) {
            if (!AbstractFurnaceBlockEntity.isFuel(furnaceFuel)) {
                this.lastResult = "furnace_fuel_slot_blocked";
                return SmeltResult.STATION_BLOCKED;
            }
            return SmeltResult.WORKING;
        }
        if (this.isLit(level, furnacePos)) {
            this.lastResult = "furnace_burning";
            return SmeltResult.WORKING;
        }

        SimpleContainer inventory = this.friend.getFriendInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !fuelMatcher.test(stack) || !AbstractFurnaceBlockEntity.isFuel(stack)) {
                continue;
            }
            ItemStack removed = this.friend.getInventoryProvider().removeItem(slot, 1);
            if (removed.isEmpty()) {
                this.lastResult = "missing_fuel";
                return SmeltResult.MISSING_FUEL;
            }
            this.faceAndSwing(furnace.getBlockPos());
            furnace.setItem(FUEL_SLOT, removed.copyWithCount(1));
            this.markFurnaceChanged(level, furnacePos, furnace);
            this.lastResult = "inserted_fuel " + removed.getHoverName().getString();
            return SmeltResult.WORKING;
        }
        this.lastResult = "missing_fuel";
        return SmeltResult.MISSING_FUEL;
    }

    private Optional<SmeltInputPlan> findInputInInventory(ServerLevel level, BlockPos furnacePos, Predicate<ItemStack> resultMatcher) {
        SimpleContainer inventory = this.friend.getFriendInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Optional<String> recipeId = this.findMatchingRecipeId(level, furnacePos, stack, resultMatcher);
            if (recipeId.isPresent()) {
                return Optional.of(new SmeltInputPlan(slot, recipeId.get()));
            }
        }
        return Optional.empty();
    }

    private boolean isSupportedFurnace(ServerLevel level, BlockPos furnacePos) {
        return level.getBlockState(furnacePos).is(Blocks.FURNACE)
                || level.getBlockState(furnacePos).is(Blocks.BLAST_FURNACE);
    }

    private boolean isBlastFurnace(ServerLevel level, BlockPos furnacePos) {
        return level.getBlockState(furnacePos).is(Blocks.BLAST_FURNACE);
    }

    private boolean isLit(ServerLevel level, BlockPos furnacePos) {
        if (furnacePos == null) {
            return false;
        }
        var state = level.getBlockState(furnacePos);
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    private void markFurnaceChanged(ServerLevel level, BlockPos furnacePos, AbstractFurnaceBlockEntity furnace) {
        furnace.setChanged();
        level.sendBlockUpdated(furnacePos, level.getBlockState(furnacePos), level.getBlockState(furnacePos), 3);
    }

    private void faceAndSwing(BlockPos furnacePos) {
        if (furnacePos == null) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(furnacePos);
        this.friend.getLookControl().setLookAt(center.x, center.y, center.z);
        this.friend.swing(InteractionHand.MAIN_HAND);
    }

    private Optional<String> findMatchingRecipeId(ServerLevel level, BlockPos furnacePos, ItemStack input, Predicate<ItemStack> resultMatcher) {
        SimpleContainer singleInput = new SimpleContainer(input.copyWithCount(1));
        if (this.isBlastFurnace(level, furnacePos)) {
            for (BlastingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING)) {
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (!result.isEmpty() && resultMatcher.test(result) && recipe.matches(singleInput, level)) {
                    return Optional.of(recipe.getId().toString());
                }
            }
            return Optional.empty();
        }
        for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.isEmpty() && resultMatcher.test(result) && recipe.matches(singleInput, level)) {
                return Optional.of(recipe.getId().toString());
            }
        }
        return Optional.empty();
    }

    public enum SmeltResult {
        SMELTED,
        WORKING,
        NO_MATCHING_RECIPE,
        MISSING_INPUT,
        MISSING_FUEL,
        INVENTORY_FULL,
        MISSING_STATION,
        STATION_BLOCKED
    }

    private record SmeltInputPlan(int inventorySlot, String recipeId) {
    }
}
