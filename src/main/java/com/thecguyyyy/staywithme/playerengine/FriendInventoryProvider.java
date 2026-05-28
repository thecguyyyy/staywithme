package com.thecguyyyy.staywithme.playerengine;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class FriendInventoryProvider {
    private final FriendEntity friend;
    private final SimpleContainer inventory;
    private int selectedSlot;

    public FriendInventoryProvider(FriendEntity friend, SimpleContainer inventory) {
        this.friend = friend;
        this.inventory = inventory;
        this.selectedSlot = 0;
    }

    public int getContainerSize() {
        return this.inventory.getContainerSize();
    }

    public ItemStack getItem(int slot) {
        if (!this.isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return this.inventory.getItem(slot);
    }

    public void setItem(int slot, ItemStack stack) {
        if (!this.isValidSlot(slot)) {
            return;
        }
        this.inventory.setItem(slot, stack == null ? ItemStack.EMPTY : stack);
        this.inventory.setChanged();
    }

    public ItemStack addItem(ItemStack stack) {
        return this.friend.insertIntoInventory(stack);
    }

    public ItemStack removeItem(int slot, int amount) {
        if (!this.isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = this.inventory.removeItem(slot, amount);
        this.inventory.setChanged();
        return removed;
    }

    public int count(Predicate<ItemStack> predicate) {
        return this.friend.countInventoryItems(predicate);
    }

    public boolean consume(Predicate<ItemStack> predicate, int amount) {
        return this.friend.consumeInventoryItems(predicate, amount);
    }

    public int getSelectedSlot() {
        return this.selectedSlot;
    }

    public void setSelectedSlot(int selectedSlot) {
        if (this.isValidSlot(selectedSlot)) {
            this.selectedSlot = selectedSlot;
        }
    }

    public ItemStack getMainHandStack() {
        return this.getItem(this.selectedSlot);
    }

    public void setMainHandStack(ItemStack stack) {
        this.setItem(this.selectedSlot, stack);
    }

    public int findFirstSlot(Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    public boolean selectFirst(Predicate<ItemStack> predicate) {
        int slot = this.findFirstSlot(predicate);
        if (slot < 0) {
            return false;
        }
        this.setSelectedSlot(slot);
        return true;
    }

    public int selectBestToolFor(BlockState state) {
        int bestSlot = this.selectedSlot;
        ItemStack selected = this.getMainHandStack();
        float bestSpeed = selected.isEmpty() ? 1.0F : selected.getDestroySpeed(state);
        boolean bestCanHarvest = !state.requiresCorrectToolForDrops()
                || (!selected.isEmpty() && selected.isCorrectToolForDrops(state));

        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            boolean canHarvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
            if ((canHarvest && !bestCanHarvest) || (canHarvest == bestCanHarvest && speed > bestSpeed)) {
                bestSlot = slot;
                bestSpeed = speed;
                bestCanHarvest = canHarvest;
            }
        }

        this.setSelectedSlot(bestSlot);
        return bestSlot;
    }

    public String summary() {
        List<String> entries = new ArrayList<>();
        for (int slot = 0; slot < this.inventory.getContainerSize() && entries.size() < 8; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty()) {
                entries.add("#" + slot + " " + stack.getHoverName().getString() + " x" + stack.getCount());
            }
        }
        String selected = this.getMainHandStack().isEmpty()
                ? "selected=#" + this.selectedSlot + " empty"
                : "selected=#" + this.selectedSlot + " " + this.getMainHandStack().getHoverName().getString();
        return entries.isEmpty() ? selected + "; empty" : selected + "; " + String.join(", ", entries);
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < this.inventory.getContainerSize();
    }
}
