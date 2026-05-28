package com.thecguyyyy.staywithme.crafting;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class RecipeCatalog {
    private RecipeCatalog() {
    }

    public static List<CraftingRecipe> findCraftingRecipes(ServerLevel level, Predicate<ItemStack> resultMatcher, int gridSize) {
        int safeGridSize = Math.max(2, Math.min(3, gridSize));
        List<CraftingRecipe> recipes = new ArrayList<>();
        for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = safeResult(level, recipe);
            if (!result.isEmpty() && resultMatcher.test(result) && recipe.canCraftInDimensions(safeGridSize, safeGridSize)) {
                recipes.add(recipe);
            }
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        return recipes;
    }

    public static boolean requiresCraftingTable(ServerLevel level, Predicate<ItemStack> resultMatcher) {
        boolean sawMatchingThreeByThreeRecipe = false;
        for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = safeResult(level, recipe);
            if (result.isEmpty() || !resultMatcher.test(result)) {
                continue;
            }
            if (recipe.canCraftInDimensions(2, 2)) {
                return false;
            }
            if (recipe.canCraftInDimensions(3, 3)) {
                sawMatchingThreeByThreeRecipe = true;
            }
        }
        return sawMatchingThreeByThreeRecipe;
    }

    public static String summary(ServerLevel level) {
        Map<String, Integer> byType = new TreeMap<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            byType.merge(typeId(recipe), 1, Integer::sum);
        }

        List<String> entries = byType.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .limit(10)
                .toList();
        int crafting = byType.getOrDefault("minecraft:crafting", 0);
        return "recipes total=" + level.getRecipeManager().getRecipes().size()
                + ", crafting=" + crafting
                + ", types=" + String.join(", ", entries);
    }

    public static List<String> describeMatching(ServerLevel level, String rawQuery, int limit) {
        String query = normalize(rawQuery);
        List<Recipe<?>> matches = level.getRecipeManager().getRecipes().stream()
                .filter(recipe -> matches(level, recipe, query))
                .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                .limit(Math.max(1, limit))
                .toList();

        List<String> descriptions = new ArrayList<>();
        for (Recipe<?> recipe : matches) {
            descriptions.add(describe(level, recipe));
        }
        return descriptions;
    }

    public static String describe(ServerLevel level, Recipe<?> recipe) {
        ItemStack result = safeResult(level, recipe);
        StringBuilder builder = new StringBuilder()
                .append(recipe.getId())
                .append(" type=").append(typeId(recipe))
                .append(" serializer=").append(serializerId(recipe))
                .append(" result=").append(itemId(result)).append(" x").append(result.getCount());

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            builder.append(" grid=").append(gridDescription(craftingRecipe));
        }

        NonNullList<Ingredient> ingredients = safeIngredients(recipe);
        if (!ingredients.isEmpty()) {
            builder.append(" ingredients=");
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < ingredients.size() && parts.size() < 9; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (ingredient.isEmpty()) {
                    continue;
                }
                parts.add("#" + i + "[" + describeIngredient(ingredient) + "]");
            }
            builder.append(String.join(" ", parts));
        }

        return trim(builder.toString(), 480);
    }

    private static boolean matches(ServerLevel level, Recipe<?> recipe, String query) {
        if (query.isBlank()) {
            return true;
        }
        if (normalize(recipe.getId().toString()).contains(query)
                || normalize(typeId(recipe)).contains(query)
                || normalize(serializerId(recipe)).contains(query)) {
            return true;
        }

        ItemStack result = safeResult(level, recipe);
        if (normalize(itemId(result)).contains(query)
                || normalize(result.getHoverName().getString()).contains(query)) {
            return true;
        }

        for (Ingredient ingredient : safeIngredients(recipe)) {
            if (normalize(describeIngredient(ingredient)).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static String gridDescription(CraftingRecipe recipe) {
        String size = recipe.canCraftInDimensions(2, 2) ? "2x2" : recipe.canCraftInDimensions(3, 3) ? "3x3" : "unknown";
        if (recipe instanceof IShapedRecipe<?> shaped) {
            return shaped.getRecipeWidth() + "x" + shaped.getRecipeHeight() + "/" + size;
        }
        return "shapeless/" + size;
    }

    private static NonNullList<Ingredient> safeIngredients(Recipe<?> recipe) {
        try {
            return recipe.getIngredients();
        } catch (RuntimeException ignored) {
            return NonNullList.create();
        }
    }

    private static ItemStack safeResult(ServerLevel level, Recipe<?> recipe) {
        try {
            return recipe.getResultItem(level.registryAccess());
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String describeIngredient(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            return "empty";
        }
        List<String> alternatives = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                alternatives.add(itemId(stack));
            }
            if (alternatives.size() >= 5) {
                break;
            }
        }
        if (stacks.length > alternatives.size()) {
            alternatives.add("+" + (stacks.length - alternatives.size()) + " more");
        }
        return String.join("|", alternatives);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? stack.getItem().toString() : key.toString();
    }

    private static String typeId(Recipe<?> recipe) {
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return key == null ? recipe.getType().toString() : key.toString();
    }

    private static String serializerId(Recipe<?> recipe) {
        ResourceLocation key = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        return key == null ? recipe.getSerializer().toString() : key.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
