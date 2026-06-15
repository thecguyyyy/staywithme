package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class LocalInventoryFallback {
    private static final int EXPEDITION_MIN_PICKAXE_DURABILITY = 8;
    private final FriendEntity friend;

    LocalInventoryFallback(FriendEntity friend) {
        this.friend = friend;
    }

    int carriedFoodItems() {
        return this.friend.countInventoryItems(this::isExpeditionFood);
    }

    int carriedFoodUnits() {
        int total = 0;
        for (int slot = 0; slot < this.friend.getInventoryProvider().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getInventoryProvider().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            FoodProperties food = stack.getFoodProperties(this.friend);
            if (food != null) {
                total += food.getNutrition() * stack.getCount();
            }
        }
        return total;
    }

    int carriedMeatFoodUnits() {
        int total = 0;
        for (int slot = 0; slot < this.friend.getInventoryProvider().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getInventoryProvider().getItem(slot);
            if (stack.isEmpty() || !this.isMeatFood(stack)) {
                continue;
            }
            FoodProperties food = stack.getFoodProperties(this.friend);
            if (food != null) {
                total += food.getNutrition() * stack.getCount();
            }
        }
        return total;
    }

    int countCoalEquivalent() {
        return this.friend.countInventoryItems(this::isCoalEquivalent);
    }

    int countPlanks() {
        return this.friend.countInventoryItems(stack -> stack.is(ItemTags.PLANKS));
    }

    int countLogs() {
        return this.friend.countInventoryItems(stack -> stack.is(ItemTags.LOGS));
    }

    int countSticks() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.STICK));
    }

    boolean hasCraftingTable() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.CRAFTING_TABLE)) > 0;
    }

    boolean hasChest() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.CHEST)) > 0;
    }

    boolean hasFurnace() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.FURNACE)) > 0;
    }

    boolean hasBlastFurnace() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.BLAST_FURNACE)) > 0;
    }

    int countWoodenAxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.WOODEN_AXE));
    }

    int countWoodenPickaxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.WOODEN_PICKAXE));
    }

    int countStonePickaxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.STONE_PICKAXE));
    }

    int countCharcoal() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.CHARCOAL));
    }

    int countRawIron() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.RAW_IRON));
    }

    int countIronIngots() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.IRON_INGOT));
    }

    int countIronPickaxes() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.IRON_PICKAXE));
    }

    int countTorches() {
        return this.friend.countInventoryItems(stack -> stack.is(Items.TORCH));
    }

    int countSmeltOutput(String normalizedTarget) {
        return switch (normalizedTarget) {
            case "iron_ingot" -> this.countIronIngots();
            case "gold_ingot" -> this.friend.countInventoryItems(stack -> stack.is(Items.GOLD_INGOT));
            case "copper_ingot" -> this.friend.countInventoryItems(stack -> stack.is(Items.COPPER_INGOT));
            case "charcoal" -> this.countCharcoal();
            default -> 0;
        };
    }

    Block floorRepairBlockToPlace(FriendTask task) {
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

    int countConstructionRepairBlocks(FriendTask task) {
        return this.countFloorRepairItems(task, Items.COBBLESTONE)
                + this.countFloorRepairItems(task, Items.COBBLED_DEEPSLATE)
                + this.countFloorRepairItems(task, Items.DIRT)
                + this.countFloorRepairItems(task, Items.NETHERRACK);
    }

    boolean isMatchingFloorRepairBlock(ItemStack stack, Block block) {
        return !stack.isEmpty() && block != null && stack.is(block.asItem());
    }

    Block supplyFurnaceBlockToPlace() {
        if (this.hasFurnace()) {
            return Blocks.FURNACE;
        }
        if (this.hasBlastFurnace()) {
            return Blocks.BLAST_FURNACE;
        }
        return null;
    }

    int emptySlots() {
        int empty = 0;
        for (int slot = 0; slot < this.friend.getFriendInventory().getContainerSize(); slot++) {
            if (this.friend.getFriendInventory().getItem(slot).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    boolean hasUsablePickaxeForRequirement(MiningTargetRegistry.ToolRequirement requirement) {
        BlockState representative = switch (requirement) {
            case NONE -> null;
            case WOODEN_PICKAXE -> Blocks.STONE.defaultBlockState();
            case STONE_PICKAXE -> Blocks.IRON_ORE.defaultBlockState();
            case IRON_PICKAXE -> Blocks.DIAMOND_ORE.defaultBlockState();
        };
        if (representative == null) {
            return true;
        }
        for (int slot = 0; slot < this.friend.getInventoryProvider().getContainerSize(); slot++) {
            ItemStack stack = this.friend.getInventoryProvider().getItem(slot);
            if (!stack.isEmpty()
                    && stack.is(ItemTags.PICKAXES)
                    && (!stack.isDamageableItem() || this.remainingDurability(stack) > 0)
                    && stack.isCorrectToolForDrops(representative)) {
                return true;
            }
        }
        return false;
    }

    boolean hasUsableExpeditionPickaxe(FriendTask task) {
        return this.countUsableExpeditionPickaxes(task) > 0;
    }

    int countUsableExpeditionPickaxes(FriendTask task) {
        return this.friend.countInventoryItems(stack -> this.isUsableExpeditionPickaxe(task, stack));
    }

    boolean isUsableExpeditionPickaxe(FriendTask task, ItemStack stack) {
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

    int bestExpeditionPickaxeDurability() {
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

    private int remainingDurability(ItemStack stack) {
        return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
    }

    private int countFloorRepairItems(FriendTask task, Item item) {
        return this.canUseForFloorRepair(task, item)
                ? this.friend.countInventoryItems(stack -> stack.is(item))
                : 0;
    }

    private boolean canUseForFloorRepair(FriendTask task, Item item) {
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

    boolean isCoalEquivalent(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL));
    }

    boolean isSupplyFurnaceFuel(ItemStack stack) {
        return this.isCoalEquivalent(stack)
                || stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.PLANKS)
                || stack.is(Items.STICK);
    }

    boolean isExpeditionFood(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getFoodProperties(this.friend) != null
                && !this.isRiskyExpeditionFood(stack);
    }

    boolean isCookableExpeditionFood(ItemStack stack) {
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

    boolean isMeatFood(ItemStack stack) {
        return stack.is(Items.BEEF)
                || stack.is(Items.COOKED_BEEF)
                || stack.is(Items.PORKCHOP)
                || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.COOKED_CHICKEN)
                || stack.is(Items.MUTTON)
                || stack.is(Items.COOKED_MUTTON)
                || stack.is(Items.RABBIT)
                || stack.is(Items.COOKED_RABBIT);
    }

    private boolean isRiskyExpeditionFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.PUFFERFISH)
                || stack.is(Items.CHICKEN)
                || stack.is(Items.SUSPICIOUS_STEW);
    }
}
