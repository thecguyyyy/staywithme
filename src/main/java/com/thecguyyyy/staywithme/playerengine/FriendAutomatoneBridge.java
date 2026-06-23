package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInteractionManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FriendAutomatoneBridge implements IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider {
    private final FriendEntity friend;
    private final LivingEntityInventory livingInventory;
    private final LivingEntityInteractionManager interactionManager;
    private final LivingEntityHungerManager hungerManager;
    private final List<ItemStack> armorSnapshot = new ArrayList<>();
    private final List<ItemStack> offhandSnapshot = new ArrayList<>();
    private boolean initialized;
    private String lastError = "";

    public FriendAutomatoneBridge(FriendEntity friend) {
        this.friend = friend;
        this.livingInventory = new LivingEntityInventory(friend);
        this.interactionManager = new LivingEntityInteractionManager(friend);
        this.hungerManager = new LivingEntityHungerManager();
        this.initialized = true;
        this.syncFromFriend();
    }

    @Override
    public LivingEntityInventory getLivingInventory() {
        return this.livingInventory;
    }

    @Override
    public LivingEntityInteractionManager getInteractionManager() {
        return this.interactionManager;
    }

    @Override
    public LivingEntityHungerManager getHungerManager() {
        return this.hungerManager;
    }

    public void serverTick() {
        if (!(this.friend.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        try {
            this.interactionManager.setWorld(serverLevel);
            this.interactionManager.update();
            this.livingInventory.updateItems();
            this.syncFromFriendHunger();
        } catch (RuntimeException error) {
            this.initialized = false;
            this.lastError = error.getClass().getSimpleName() + ": " + error.getMessage();
            throw error;
        }
    }

    public void syncFromFriend() {
        int size = Math.min(this.friend.getInventoryProvider().getContainerSize(), this.livingInventory.main.size());
        for (int slot = 0; slot < size; slot++) {
            this.livingInventory.main.set(slot, this.friend.getInventoryProvider().getItem(slot).copy());
        }
        for (int slot = size; slot < this.livingInventory.main.size(); slot++) {
            this.livingInventory.main.set(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < this.livingInventory.armor.size(); slot++) {
            EquipmentSlot equipmentSlot = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, slot);
            ItemStack stack = this.friend.getItemBySlot(equipmentSlot).copy();
            this.livingInventory.armor.set(slot, stack.copy());
            this.setSnapshot(this.armorSnapshot, slot, stack);
        }
        this.syncFromFriendOffhand();
        this.livingInventory.selectedSlot = this.friend.getInventoryProvider().getSelectedSlot();
        this.syncFromFriendHunger();
    }

    public void syncToFriend() {
        int size = Math.min(this.friend.getInventoryProvider().getContainerSize(), this.livingInventory.main.size());
        for (int slot = 0; slot < size; slot++) {
            this.friend.getInventoryProvider().setItem(slot, this.livingInventory.main.get(slot).copy());
        }
        for (int slot = 0; slot < this.livingInventory.armor.size(); slot++) {
            EquipmentSlot equipmentSlot = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, slot);
            ItemStack entityStack = this.friend.getItemBySlot(equipmentSlot);
            ItemStack bridgeStack = this.livingInventory.armor.get(slot);
            ItemStack previousStack = this.snapshotAt(this.armorSnapshot, slot);
            if (!ItemStack.matches(bridgeStack, previousStack)) {
                this.friend.setItemSlot(equipmentSlot, bridgeStack.copy());
                this.setSnapshot(this.armorSnapshot, slot, bridgeStack);
            } else if (!ItemStack.matches(entityStack, previousStack)) {
                this.livingInventory.armor.set(slot, entityStack.copy());
                this.setSnapshot(this.armorSnapshot, slot, entityStack);
            }
        }
        this.syncToFriendOffhand();
        this.friend.getInventoryProvider().setSelectedSlot(this.livingInventory.selectedSlot);
        this.syncToFriendHunger();
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public String status() {
        if (!this.initialized) {
            return "bridge=failed(" + this.lastError + ")";
        }
        return "bridge=ready, peInventory=" + this.livingInventory.getContainerSize()
                + ", selected=" + this.livingInventory.selectedSlot
                + ", peHunger=" + this.hungerManager.getFoodLevel() + "/" + Math.round(this.hungerManager.getSaturationLevel() * 10.0F) / 10.0F;
    }

    private void syncFromFriendHunger() {
        this.hungerManager.setFoodLevel(this.friend.getHungerProvider().getFoodLevel());
        this.hungerManager.setSaturationLevel(this.friend.getHungerProvider().getSaturationLevel());
        this.hungerManager.setExhaustion(this.friend.getHungerProvider().getExhaustion());
    }

    private void syncFromFriendOffhand() {
        for (int slot = 0; slot < this.livingInventory.offHand.size(); slot++) {
            ItemStack stack = slot == 0
                    ? this.friend.getItemBySlot(EquipmentSlot.OFFHAND).copy()
                    : ItemStack.EMPTY;
            this.livingInventory.offHand.set(slot, stack.copy());
            this.setSnapshot(this.offhandSnapshot, slot, stack);
        }
    }

    private void syncToFriendOffhand() {
        if (this.livingInventory.offHand.isEmpty()) {
            return;
        }
        ItemStack bridgeStack = this.livingInventory.offHand.get(0);
        ItemStack entityStack = this.friend.getItemBySlot(EquipmentSlot.OFFHAND);
        ItemStack previousStack = this.snapshotAt(this.offhandSnapshot, 0);
        if (!ItemStack.matches(bridgeStack, previousStack)) {
            this.friend.setItemSlot(EquipmentSlot.OFFHAND, bridgeStack.copy());
            this.setSnapshot(this.offhandSnapshot, 0, bridgeStack);
        } else if (!ItemStack.matches(entityStack, previousStack)) {
            this.livingInventory.offHand.set(0, entityStack.copy());
            this.setSnapshot(this.offhandSnapshot, 0, entityStack);
        }
    }

    private void syncToFriendHunger() {
        this.friend.getHungerProvider().setFoodLevel(this.hungerManager.getFoodLevel());
        this.friend.getHungerProvider().setSaturationLevel(this.hungerManager.getSaturationLevel());
        this.friend.getHungerProvider().setExhaustion(this.hungerManager.getExhaustion());
    }

    private ItemStack snapshotAt(List<ItemStack> snapshots, int slot) {
        this.ensureSnapshotSize(snapshots, slot + 1);
        return snapshots.get(slot);
    }

    private void setSnapshot(List<ItemStack> snapshots, int slot, ItemStack stack) {
        this.ensureSnapshotSize(snapshots, slot + 1);
        snapshots.set(slot, stack == null ? ItemStack.EMPTY : stack.copy());
    }

    private void ensureSnapshotSize(List<ItemStack> snapshots, int size) {
        while (snapshots.size() < size) {
            snapshots.add(ItemStack.EMPTY);
        }
    }
}
