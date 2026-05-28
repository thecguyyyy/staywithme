package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.crafting.RecipeCatalog;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class CraftingActionAdapter {
    private final FriendEntity friend;
    private String lastRecipe = "none";
    private String lastResult = "idle";

    public CraftingActionAdapter(FriendEntity friend) {
        this.friend = friend;
    }

    public CraftResult craftOne(ServerLevel level, Predicate<ItemStack> resultMatcher, int gridSize) {
        int safeGridSize = Math.max(2, Math.min(3, gridSize));
        return this.craftOneInternal(level, resultMatcher, safeGridSize, null);
    }

    public CraftResult craftOneAtTable(ServerLevel level, BlockPos tablePos, Predicate<ItemStack> resultMatcher) {
        if (tablePos == null || !level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
            this.lastRecipe = "none";
            this.lastResult = "missing_crafting_table";
            return CraftResult.MISSING_STATION;
        }
        return this.craftOneInternal(level, resultMatcher, 3, tablePos);
    }

    public CraftResult craftOneWithBestContext(ServerLevel level, Predicate<ItemStack> resultMatcher, BlockPos tablePos) {
        if (this.requiresCraftingTable(level, resultMatcher)) {
            return this.craftOneAtTable(level, tablePos, resultMatcher);
        }
        return this.craftOne(level, resultMatcher, 2);
    }

    public boolean requiresCraftingTable(ServerLevel level, Predicate<ItemStack> resultMatcher) {
        return RecipeCatalog.requiresCraftingTable(level, resultMatcher);
    }

    private CraftResult craftOneInternal(ServerLevel level, Predicate<ItemStack> resultMatcher, int safeGridSize, BlockPos tablePos) {
        for (CraftingRecipe recipe : RecipeCatalog.findCraftingRecipes(level, resultMatcher, safeGridSize)) {
            Optional<CraftPlan> plan = this.planRecipe(level, recipe, safeGridSize);
            if (plan.isEmpty()) {
                continue;
            }

            if (!this.canApply(plan.get())) {
                this.lastRecipe = recipe.getId().toString();
                this.lastResult = "inventory_full";
                return CraftResult.INVENTORY_FULL;
            }

            this.apply(plan.get());
            this.lastRecipe = recipe.getId().toString();
            String station = tablePos == null ? "inventory" : "table@" + tablePos.toShortString();
            this.lastResult = "crafted " + plan.get().result().getHoverName().getString() + " x" + plan.get().result().getCount() + " via " + station;
            return CraftResult.CRAFTED;
        }

        this.lastRecipe = "none";
        this.lastResult = "no_matching_recipe";
        return CraftResult.NO_MATCHING_RECIPE;
    }

    public String status() {
        return "craft=" + this.lastResult + ",recipe=" + this.lastRecipe;
    }

    private Optional<CraftPlan> planRecipe(ServerLevel level, CraftingRecipe recipe, int gridSize) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        NonNullList<ItemStack> grid = NonNullList.withSize(gridSize * gridSize, ItemStack.EMPTY);
        Map<Integer, Integer> consumed = new HashMap<>();

        if (recipe instanceof IShapedRecipe<?> shaped) {
            int recipeWidth = shaped.getRecipeWidth();
            int recipeHeight = shaped.getRecipeHeight();
            if (recipeWidth > gridSize || recipeHeight > gridSize) {
                return Optional.empty();
            }

            for (int y = 0; y < recipeHeight; y++) {
                for (int x = 0; x < recipeWidth; x++) {
                    int ingredientIndex = x + y * recipeWidth;
                    if (ingredientIndex >= ingredients.size()) {
                        continue;
                    }
                    Ingredient ingredient = ingredients.get(ingredientIndex);
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    int gridIndex = x + y * gridSize;
                    if (!this.assignIngredient(ingredient, consumed).map(stack -> {
                        grid.set(gridIndex, stack.copyWithCount(1));
                        return true;
                    }).orElse(false)) {
                        return Optional.empty();
                    }
                }
            }
        } else {
            int gridIndex = 0;
            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                if (gridIndex >= grid.size()) {
                    return Optional.empty();
                }
                int targetIndex = gridIndex;
                if (!this.assignIngredient(ingredient, consumed).map(stack -> {
                    grid.set(targetIndex, stack.copyWithCount(1));
                    return true;
                }).orElse(false)) {
                    return Optional.empty();
                }
                gridIndex++;
            }
        }

        CraftingContainer container = new TransientCraftingContainer(new DummyCraftingMenu(), gridSize, gridSize, grid);
        if (!recipe.matches(container, level)) {
            return Optional.empty();
        }

        ItemStack result = recipe.assemble(container, level.registryAccess());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        NonNullList<ItemStack> remainingItems = recipe.getRemainingItems(container);
        return Optional.of(new CraftPlan(consumed, result, remainingItems));
    }

    private Optional<ItemStack> assignIngredient(Ingredient ingredient, Map<Integer, Integer> consumed) {
        SimpleContainer inventory = this.friend.getFriendInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }
            int alreadyConsumed = consumed.getOrDefault(slot, 0);
            if (alreadyConsumed >= stack.getCount()) {
                continue;
            }
            consumed.put(slot, alreadyConsumed + 1);
            return Optional.of(stack);
        }
        return Optional.empty();
    }

    private boolean canApply(CraftPlan plan) {
        NonNullList<ItemStack> simulated = NonNullList.withSize(this.friend.getFriendInventory().getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < simulated.size(); slot++) {
            simulated.set(slot, this.friend.getFriendInventory().getItem(slot).copy());
        }

        for (Map.Entry<Integer, Integer> entry : plan.consumed().entrySet()) {
            ItemStack stack = simulated.get(entry.getKey());
            stack.shrink(entry.getValue());
            if (stack.isEmpty()) {
                simulated.set(entry.getKey(), ItemStack.EMPTY);
            }
        }

        if (!this.insertIntoSimulated(simulated, plan.result().copy())) {
            return false;
        }
        for (ItemStack remaining : plan.remainingItems()) {
            if (!remaining.isEmpty() && !this.insertIntoSimulated(simulated, remaining.copy())) {
                return false;
            }
        }
        return true;
    }

    private void apply(CraftPlan plan) {
        SimpleContainer inventory = this.friend.getFriendInventory();
        for (Map.Entry<Integer, Integer> entry : plan.consumed().entrySet()) {
            ItemStack stack = inventory.getItem(entry.getKey());
            stack.shrink(entry.getValue());
            if (stack.isEmpty()) {
                inventory.setItem(entry.getKey(), ItemStack.EMPTY);
            }
        }
        inventory.setChanged();

        this.friend.insertIntoInventory(plan.result().copy());
        for (ItemStack remaining : plan.remainingItems()) {
            if (!remaining.isEmpty()) {
                this.friend.insertIntoInventory(remaining.copy());
            }
        }
    }

    private boolean insertIntoSimulated(NonNullList<ItemStack> inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        ItemStack remainder = stack.copy();
        for (ItemStack existing : inventory) {
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
                continue;
            }
            int moved = Math.min(remainder.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (moved <= 0) {
                continue;
            }
            existing.grow(moved);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                return true;
            }
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.get(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            ItemStack inserted = remainder.copyWithCount(moved);
            inventory.set(slot, inserted);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public enum CraftResult {
        CRAFTED,
        NO_MATCHING_RECIPE,
        INVENTORY_FULL,
        MISSING_STATION
    }

    private record CraftPlan(Map<Integer, Integer> consumed, ItemStack result, NonNullList<ItemStack> remainingItems) {
    }

    private static class DummyCraftingMenu extends AbstractContainerMenu {
        protected DummyCraftingMenu() {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }
    }
}
