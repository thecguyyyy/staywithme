package com.thecguyyyy.staywithme.playerengine;

import com.player2.playerengine.automaton.api.entity.IHungerManagerProvider;
import com.player2.playerengine.automaton.api.entity.IAutomatone;
import com.player2.playerengine.automaton.api.entity.IInteractionManagerProvider;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityHungerManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInteractionManager;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PlayerEngineFriendEntity extends FriendEntity
        implements IAutomatone, IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider, PlayerEngineProviderHost {
    private FriendAutomatoneBridge playerEngineBridge;
    private boolean initializingPlayerEngineBridge;

    public PlayerEngineFriendEntity(EntityType<? extends FriendEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public LivingEntityInventory getLivingInventory() {
        return this.bridge().getLivingInventory();
    }

    @Override
    public LivingEntityInteractionManager getInteractionManager() {
        return this.bridge().getInteractionManager();
    }

    @Override
    public LivingEntityHungerManager getHungerManager() {
        return this.bridge().getHungerManager();
    }

    @Override
    public void syncPlayerEngineStateFromFriend() {
        this.bridge().syncFromFriend();
    }

    @Override
    public void tickPlayerEngineManagers(ServerLevel level) {
        this.bridge().serverTick();
    }

    @Override
    public void syncPlayerEngineStateToFriend() {
        this.bridge().syncToFriend();
    }

    @Override
    public String playerEngineProviderStatus() {
        return this.bridge().status();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (!this.shouldUsePlayerEngineInventorySlots() || this.initializingPlayerEngineBridge) {
            return super.getItemBySlot(slot);
        }
        LivingEntityInventory inventory = this.getLivingInventory();
        if (slot == EquipmentSlot.MAINHAND && LivingEntityInventory.isValidHotbarIndex(inventory.selectedSlot)) {
            return inventory.getMainHandStack();
        }
        if (slot == EquipmentSlot.OFFHAND && !inventory.offHand.isEmpty()) {
            return inventory.offHand.get(0);
        }
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            int index = slot.getIndex();
            if (index >= 0 && index < inventory.armor.size()) {
                return inventory.armor.get(index);
            }
        }
        return super.getItemBySlot(slot);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (this.shouldUsePlayerEngineInventorySlots()) {
            LivingEntityInventory inventory = this.getLivingInventory();
            ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
            if (slot == EquipmentSlot.MAINHAND && LivingEntityInventory.isValidHotbarIndex(inventory.selectedSlot)) {
                inventory.main.set(inventory.selectedSlot, copy.copy());
            } else if (slot == EquipmentSlot.OFFHAND && !inventory.offHand.isEmpty()) {
                inventory.offHand.set(0, copy.copy());
            } else if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                int index = slot.getIndex();
                if (index >= 0 && index < inventory.armor.size()) {
                    inventory.armor.set(index, copy.copy());
                }
            }
        }
        super.setItemSlot(slot, stack);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        if (StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get() && !this.level().isClientSide) {
            this.syncPlayerEngineStateToFriend();
        }
        super.addAdditionalSaveData(tag);
        if (StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get() && !this.level().isClientSide) {
            tag.put("PlayerEngineInventory", this.getLivingInventory().writeNbt(new ListTag()));
            tag.putInt("PlayerEngineSelectedItemSlot", this.getLivingInventory().selectedSlot);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get()) {
            return;
        }
        if (tag.contains("PlayerEngineInventory", Tag.TAG_LIST)) {
            this.getLivingInventory().readNbt(tag.getList("PlayerEngineInventory", Tag.TAG_COMPOUND));
            if (tag.contains("PlayerEngineSelectedItemSlot", Tag.TAG_ANY_NUMERIC)) {
                this.getLivingInventory().selectedSlot = tag.getInt("PlayerEngineSelectedItemSlot");
            }
            this.syncPlayerEngineStateToFriend();
        } else {
            this.syncPlayerEngineStateFromFriend();
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        if (StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get() && !this.level().isClientSide) {
            this.getLivingInventory().dropAll();
            this.syncPlayerEngineStateToFriend();
        }
        super.die(damageSource);
    }

    @Override
    public ItemStack insertIntoInventory(ItemStack stack) {
        if (!StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get() || stack.isEmpty()) {
            return super.insertIntoInventory(stack);
        }
        ItemStack remainder = stack.copy();
        if (!this.getLivingInventory().insertStack(remainder)) {
            return stack.copy();
        }
        this.syncPlayerEngineStateToFriend();
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder.copy();
    }

    @Override
    public void onInventoryProviderSlotChanged(int slot, ItemStack stack) {
        if (!this.shouldUsePlayerEngineInventorySlots() || this.initializingPlayerEngineBridge) {
            return;
        }
        LivingEntityInventory inventory = this.getLivingInventory();
        if (slot >= 0 && slot < inventory.main.size()) {
            inventory.main.set(slot, stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }

    @Override
    public void onInventoryProviderSelectedSlotChanged(int selectedSlot) {
        if (!this.shouldUsePlayerEngineInventorySlots() || this.initializingPlayerEngineBridge) {
            return;
        }
        if (LivingEntityInventory.isValidHotbarIndex(selectedSlot)) {
            this.getLivingInventory().selectedSlot = selectedSlot;
        }
    }

    private boolean shouldUsePlayerEngineInventorySlots() {
        return StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get() && !this.level().isClientSide;
    }

    private FriendAutomatoneBridge bridge() {
        if (this.playerEngineBridge == null) {
            this.initializingPlayerEngineBridge = true;
            try {
                this.playerEngineBridge = new FriendAutomatoneBridge(this);
                this.playerEngineBridge.syncFromFriend();
            } finally {
                this.initializingPlayerEngineBridge = false;
            }
        }
        return this.playerEngineBridge;
    }
}
