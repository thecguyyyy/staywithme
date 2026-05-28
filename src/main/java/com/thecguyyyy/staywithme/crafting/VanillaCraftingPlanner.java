package com.thecguyyyy.staywithme.crafting;

import com.thecguyyyy.staywithme.ai.workflow.LongTaskWorkflow;
import com.thecguyyyy.staywithme.ai.workflow.WorkStep;
import com.thecguyyyy.staywithme.ai.workflow.WorkStepType;
import com.thecguyyyy.staywithme.ai.workflow.WorkflowFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class VanillaCraftingPlanner {
    private static final int MAX_DEPTH = 8;

    private VanillaCraftingPlanner() {
    }

    public static Optional<LongTaskWorkflow> plan(ServerLevel level, ResourceLocation target, int amount) {
        PlannerRun run = new PlannerRun(level);
        if (!run.appendCraftTarget(target.toString(), Math.max(1, amount), new HashSet<>(), 0, false)) {
            return Optional.empty();
        }
        return Optional.of(run.toWorkflow(target, Math.max(1, amount)));
    }

    private static final class PlannerRun {
        private final ServerLevel level;
        private final List<MutableStep> steps = new ArrayList<>();
        private boolean craftingStationPlanned;

        private PlannerRun(ServerLevel level) {
            this.level = level;
        }

        private LongTaskWorkflow toWorkflow(ResourceLocation target, int amount) {
            List<WorkStep> immutableSteps = this.steps.stream()
                    .map(step -> new WorkStep(step.type, step.target, step.amount))
                    .toList();
            String workflowId = "craft_" + target.toString().replace(':', '_') + "_" + amount;
            return new LongTaskWorkflow(workflowId, immutableSteps);
        }

        private boolean appendCraftTarget(String target, int amount, Set<String> visiting, int depth, boolean allowExistingInventoryOnly) {
            if (amount <= 0) {
                return true;
            }
            if (WorkflowFactory.LOGS.equals(target)) {
                this.addStep(WorkStepType.ACQUIRE_ITEM, WorkflowFactory.LOGS, amount);
                return true;
            }
            if (depth > MAX_DEPTH || !visiting.add(target)) {
                return false;
            }

            Optional<CraftingRecipe> recipe = this.findRecipeForTarget(target);
            if (recipe.isEmpty()) {
                visiting.remove(target);
                if (allowExistingInventoryOnly) {
                    this.addStep(WorkStepType.ACQUIRE_ITEM, target, amount);
                    return true;
                }
                return false;
            }

            ItemStack result = safeResult(this.level, recipe.get());
            int resultCount = Math.max(1, result.getCount());
            int craftsNeeded = divideRoundingUp(amount, resultCount);
            Map<String, Integer> requirements = this.groupRequirements(recipe.get(), craftsNeeded, visiting, depth);
            if (requirements.isEmpty() && !recipe.get().getIngredients().isEmpty()) {
                visiting.remove(target);
                return false;
            }

            for (Map.Entry<String, Integer> requirement : requirements.entrySet()) {
                if (!this.appendCraftTarget(requirement.getKey(), requirement.getValue(), visiting, depth + 1, true)) {
                    visiting.remove(target);
                    return false;
                }
            }

            if (!recipe.get().canCraftInDimensions(2, 2) && recipe.get().canCraftInDimensions(3, 3)) {
                if (!this.ensureCraftingStation(visiting, depth + 1)) {
                    visiting.remove(target);
                    return false;
                }
            }

            this.addStep(WorkStepType.CRAFT_ITEM, target, amount);
            visiting.remove(target);
            return true;
        }

        private boolean ensureCraftingStation(Set<String> visiting, int depth) {
            if (this.craftingStationPlanned) {
                return true;
            }
            this.craftingStationPlanned = true;
            if (!this.appendCraftTarget(WorkflowFactory.CRAFTING_TABLE, 1, visiting, depth, true)) {
                return false;
            }
            this.addStep(WorkStepType.PLACE_BLOCK, WorkflowFactory.CRAFTING_TABLE, 1);
            return true;
        }

        private Map<String, Integer> groupRequirements(CraftingRecipe recipe, int craftsNeeded, Set<String> visiting, int depth) {
            Map<String, Integer> requirements = new LinkedHashMap<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                Optional<String> target = this.targetForIngredient(ingredient, visiting, depth);
                if (target.isEmpty()) {
                    return Map.of();
                }
                requirements.merge(target.get(), craftsNeeded, Integer::sum);
            }
            return requirements;
        }

        private Optional<String> targetForIngredient(Ingredient ingredient, Set<String> visiting, int depth) {
            ItemStack[] alternatives = ingredient.getItems();
            if (alternatives.length == 0) {
                return Optional.empty();
            }

            for (ItemStack stack : alternatives) {
                if (!stack.isEmpty() && stack.is(ItemTags.LOGS)) {
                    return Optional.of(WorkflowFactory.LOGS);
                }
            }
            for (ItemStack stack : alternatives) {
                if (!stack.isEmpty() && stack.is(ItemTags.PLANKS)) {
                    return Optional.of(WorkflowFactory.PLANKS);
                }
            }

            Optional<String> preferredVanillaTarget = this.preferredVanillaTarget(ingredient);
            if (preferredVanillaTarget.isPresent()) {
                return preferredVanillaTarget;
            }

            for (ItemStack stack : alternatives) {
                if (stack.isEmpty()) {
                    continue;
                }
                String itemId = itemId(stack);
                if (!visiting.contains(itemId) && depth < MAX_DEPTH && this.findRecipeForTarget(itemId).isPresent()) {
                    return Optional.of(itemId);
                }
            }

            for (ItemStack stack : alternatives) {
                if (!stack.isEmpty()) {
                    return Optional.of(itemId(stack));
                }
            }
            return Optional.empty();
        }

        private Optional<String> preferredVanillaTarget(Ingredient ingredient) {
            if (ingredient.test(new ItemStack(Items.STICK))) {
                return Optional.of(itemId(new ItemStack(Items.STICK)));
            }
            if (ingredient.test(new ItemStack(Items.CRAFTING_TABLE))) {
                return Optional.of(WorkflowFactory.CRAFTING_TABLE);
            }
            if (ingredient.test(new ItemStack(Items.CHEST))) {
                return Optional.of(itemId(new ItemStack(Items.CHEST)));
            }
            return Optional.empty();
        }

        private Optional<CraftingRecipe> findRecipeForTarget(String target) {
            Predicate<ItemStack> resultMatcher = resultMatcherFor(target);
            return this.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(recipe -> recipe.canCraftInDimensions(3, 3))
                    .filter(recipe -> resultMatcher.test(safeResult(this.level, recipe)))
                    .sorted(Comparator.comparing(recipe -> recipe.getId().toString()))
                    .findFirst();
        }

        private void addStep(WorkStepType type, String target, int amount) {
            for (MutableStep step : this.steps) {
                if (step.type == type && step.target.equals(target)) {
                    step.amount += amount;
                    return;
                }
            }
            this.steps.add(new MutableStep(type, target, amount));
        }
    }

    private static Predicate<ItemStack> resultMatcherFor(String target) {
        if (WorkflowFactory.PLANKS.equals(target)) {
            return stack -> !stack.isEmpty() && stack.is(ItemTags.PLANKS);
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) {
            return stack -> false;
        }
        return stack -> !stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static ItemStack safeResult(ServerLevel level, CraftingRecipe recipe) {
        try {
            return recipe.getResultItem(level.registryAccess());
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? stack.getItem().toString() : key.toString();
    }

    private static int divideRoundingUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static final class MutableStep {
        private final WorkStepType type;
        private final String target;
        private int amount;

        private MutableStep(WorkStepType type, String target, int amount) {
            this.type = type;
            this.target = target;
            this.amount = amount;
        }
    }
}
