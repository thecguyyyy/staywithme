package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class LocalInventoryFallback {
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
