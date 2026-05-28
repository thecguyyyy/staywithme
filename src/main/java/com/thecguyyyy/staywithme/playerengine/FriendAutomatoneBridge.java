package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInteractionManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class FriendAutomatoneBridge implements IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider {
    private final FriendEntity friend;
    private final LivingEntityInventory livingInventory;
    private final LivingEntityInteractionManager interactionManager;
    private final LivingEntityHungerManager hungerManager;
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
            this.livingInventory.armor.set(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < this.livingInventory.offHand.size(); slot++) {
            this.livingInventory.offHand.set(slot, ItemStack.EMPTY);
        }
        this.livingInventory.selectedSlot = this.friend.getInventoryProvider().getSelectedSlot();
        this.syncFromFriendHunger();
    }

    public void syncToFriend() {
        int size = Math.min(this.friend.getInventoryProvider().getContainerSize(), this.livingInventory.main.size());
        for (int slot = 0; slot < size; slot++) {
            this.friend.getInventoryProvider().setItem(slot, this.livingInventory.main.get(slot).copy());
        }
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

    private void syncToFriendHunger() {
        this.friend.getHungerProvider().setFoodLevel(this.hungerManager.getFoodLevel());
        this.friend.getHungerProvider().setSaturationLevel(this.hungerManager.getSaturationLevel());
        this.friend.getHungerProvider().setExhaustion(this.hungerManager.getExhaustion());
    }
}
