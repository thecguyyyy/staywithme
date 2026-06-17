package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.ai.FriendBrain;
import com.thecguyyyy.staywithme.ai.FriendState;
import com.thecguyyyy.staywithme.ai.FriendTask;
import com.thecguyyyy.staywithme.memory.CompanionCharacterProfile;
import com.thecguyyyy.staywithme.perception.FriendPerception;
import com.thecguyyyy.staywithme.playerengine.FriendHungerProvider;
import com.thecguyyyy.staywithme.playerengine.FriendInteractionProvider;
import com.thecguyyyy.staywithme.playerengine.FriendInventoryProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class FriendEntity extends PathfinderMob {
    public static final int MAX_SAFE_FALL_DISTANCE = 6;
    private static final float PLAYER2NPC_STEP_HEIGHT = 0.6F;
    private static final double PLAYER2NPC_MOVEMENT_SPEED = 0.4D;
    private static final EntityDataAccessor<String> DATA_COMPANION_SKIN_URL = SynchedEntityData.defineId(FriendEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> DATA_MAIN_HAND_STACK = SynchedEntityData.defineId(FriendEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final int INVENTORY_SIZE = 36;
    private static final int RECOVERED_TASK_OWNER_REMINDER_TICKS = 20 * 60;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private final FriendInventoryProvider inventoryProvider;
    private final FriendInteractionProvider interactionProvider;
    private final FriendHungerProvider hungerProvider;
    private final FriendBrain friendBrain;
    private final FriendPerception perception;
    private UUID ownerUuid;
    private FriendState friendState = FriendState.IDLE;
    private FriendTask currentTask;
    private FriendTask pendingRecoveredTask;
    private int pendingRecoveredTaskTicks;
    private CompanionCharacterProfile companionProfile = CompanionCharacterProfile.empty();

    public FriendEntity(EntityType<? extends FriendEntity> entityType, Level level) {
        super(entityType, level);
        this.inventoryProvider = new FriendInventoryProvider(this, this.inventory);
        this.interactionProvider = new FriendInteractionProvider(this);
        this.hungerProvider = new FriendHungerProvider();
        this.perception = new FriendPerception(this);
        this.friendBrain = new FriendBrain(this);
        this.setMaxUpStep(PLAYER2NPC_STEP_HEIGHT);
        this.getNavigation().setCanFloat(true);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
        this.setPersistenceRequired();
        this.inventory.addListener(container -> this.setPersistenceRequired());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, PLAYER2NPC_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_COMPANION_SKIN_URL, "");
        this.entityData.define(DATA_MAIN_HAND_STACK, ItemStack.EMPTY);
    }

    @Override
    public int getMaxFallDistance() {
        return MAX_SAFE_FALL_DISTANCE;
    }

    @Override
    public Component getDisplayName() {
        CompanionCharacterProfile profile = this.getCompanionProfile();
        if (profile.hasIdentity()) {
            return Component.literal(profile.displayName());
        }
        return super.getDisplayName();
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isLeftHanded() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.restorePendingTaskIfNeeded();
            this.tickItemPickup();
            this.hungerProvider.tick(this);
            this.perception.tick();
            this.friendBrain.tick();
            this.syncVisibleEquipmentData();
        }
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return List.of(this.inventoryProvider.getMainHandStack(), super.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            if (this.level().isClientSide) {
                return this.entityData.get(DATA_MAIN_HAND_STACK);
            }
            return this.inventoryProvider.getMainHandStack();
        }
        if (slot == EquipmentSlot.OFFHAND) {
            return super.getItemBySlot(EquipmentSlot.OFFHAND);
        }
        return super.getItemBySlot(slot);
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            this.inventoryProvider.setMainHandStack(stack);
            this.syncVisibleEquipmentData();
            return;
        }
        if (slot == EquipmentSlot.OFFHAND) {
            super.setItemSlot(EquipmentSlot.OFFHAND, stack);
            return;
        }
        super.setItemSlot(slot, stack);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        if (this.ownerUuid == null) {
            this.setOwner(serverPlayer);
        }
        if (!this.isOwnedBy(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.literal("This companion belongs to another player."));
            return InteractionResult.CONSUME;
        }

        if (serverPlayer.isShiftKeyDown()) {
            this.stopTask();
            serverPlayer.sendSystemMessage(Component.literal("Companion stopped. State=" + this.friendState.name()));
        } else {
            this.startTask(FriendTask.follow(serverPlayer.getUUID(), serverPlayer.getGameProfile().getName(), "Player interaction"));
            serverPlayer.sendSystemMessage(Component.literal("Companion is following you. Sneak-right-click to stop."));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        tag.put("CompanionProfile", saveCompanionProfile(this.companionProfile));
        tag.putString("FriendState", this.friendState.name());
        tag.putInt("SelectedItemSlot", this.inventoryProvider.getSelectedSlot());
        FriendTask taskToSave = this.currentTask != null ? this.currentTask : this.pendingRecoveredTask;
        if (taskToSave != null) {
            tag.put("CurrentTask", taskToSave.save());
            CompoundTag controllerTag = new CompoundTag();
            this.friendBrain.saveControllerState(controllerTag);
            tag.put("ControllerState", controllerTag);
        }
        tag.put("FriendInventory", this.inventory.createTag());
        CompoundTag hungerTag = new CompoundTag();
        this.hungerProvider.save(hungerTag);
        tag.put("FriendHunger", hungerTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.ownerUuid = tag.getUUID("Owner");
        }
        if (tag.contains("CompanionProfile", Tag.TAG_COMPOUND)) {
            this.companionProfile = loadCompanionProfile(tag.getCompound("CompanionProfile"));
        }
        this.syncCompanionProfileData();
        this.applyCompanionProfileName();
        if (tag.contains("FriendState")) {
            try {
                this.friendState = FriendState.valueOf(tag.getString("FriendState"));
            } catch (IllegalArgumentException ignored) {
                this.friendState = FriendState.IDLE;
            }
        }
        if (tag.contains("FriendInventory", Tag.TAG_LIST)) {
            ListTag inventoryTag = tag.getList("FriendInventory", Tag.TAG_COMPOUND);
            this.inventory.fromTag(inventoryTag);
        }
        if (tag.contains("SelectedItemSlot")) {
            this.inventoryProvider.setSelectedSlot(tag.getInt("SelectedItemSlot"));
        }
        this.syncVisibleEquipmentData();
        if (tag.contains("FriendHunger", Tag.TAG_COMPOUND)) {
            this.hungerProvider.load(tag.getCompound("FriendHunger"));
        }
        if (tag.contains("CurrentTask", Tag.TAG_COMPOUND)) {
            this.pendingRecoveredTask = FriendTask.load(tag.getCompound("CurrentTask")).orElse(null);
            this.pendingRecoveredTaskTicks = 0;
            if (this.pendingRecoveredTask != null && tag.contains("ControllerState", Tag.TAG_COMPOUND)) {
                this.friendBrain.loadControllerState(tag.getCompound("ControllerState"));
            }
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!this.level().isClientSide) {
            Containers.dropContents(this.level(), this, this.inventory);
            this.inventory.clearContent();
        }
        super.die(damageSource);
    }

    public FriendBrain getFriendBrain() {
        return this.friendBrain;
    }

    public FriendPerception getPerception() {
        return this.perception;
    }

    public FriendInventoryProvider getInventoryProvider() {
        return this.inventoryProvider;
    }

    public FriendInteractionProvider getInteractionProvider() {
        return this.interactionProvider;
    }

    public FriendHungerProvider getHungerProvider() {
        return this.hungerProvider;
    }

    public void startTask(FriendTask task) {
        this.friendBrain.startTask(task);
    }

    public void stopTask() {
        this.pendingRecoveredTask = null;
        this.pendingRecoveredTaskTicks = 0;
        this.friendBrain.stopTask();
    }

    public FriendState getFriendState() {
        return this.friendState;
    }

    public void setFriendState(FriendState friendState) {
        this.friendState = friendState;
    }

    public FriendTask getCurrentTask() {
        return this.currentTask;
    }

    public String getTaskSummary() {
        if (this.currentTask != null) {
            return this.currentTask.summary();
        }
        if (this.pendingRecoveredTask != null) {
            return "pending recovery: " + this.pendingRecoveredTask.summary();
        }
        return "none";
    }

    public void setCurrentTask(FriendTask currentTask) {
        this.currentTask = currentTask;
        this.setPersistenceRequired();
        if (currentTask != null) {
            this.pendingRecoveredTask = null;
            this.pendingRecoveredTaskTicks = 0;
        }
    }

    public void setOwner(ServerPlayer player) {
        this.ownerUuid = player.getUUID();
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    public void setCompanionProfile(CompanionCharacterProfile profile) {
        this.companionProfile = profile == null ? CompanionCharacterProfile.empty() : profile;
        this.syncCompanionProfileData();
        this.applyCompanionProfileName();
        this.setPersistenceRequired();
    }

    public CompanionCharacterProfile getCompanionProfile() {
        return this.companionProfile == null ? CompanionCharacterProfile.empty() : this.companionProfile;
    }

    public String getCompanionProfileKey() {
        return this.getCompanionProfile().key();
    }

    public String getCompanionSkinUrl() {
        return this.entityData.get(DATA_COMPANION_SKIN_URL);
    }

    public boolean matchesCompanionProfile(CompanionCharacterProfile profile) {
        if (profile == null) {
            return false;
        }
        CompanionCharacterProfile current = this.getCompanionProfile();
        if (current.hasIdentity()) {
            return current.key().equals(profile.key());
        }
        String currentName = this.getCustomName() == null ? "" : this.getCustomName().getString();
        return !currentName.isBlank()
                && (currentName.equalsIgnoreCase(profile.displayName())
                || currentName.equalsIgnoreCase(profile.name())
                || currentName.equalsIgnoreCase(profile.shortName()));
    }

    public Optional<ServerPlayer> getOwnerPlayer() {
        if (this.ownerUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return Optional.ofNullable(serverLevel.getServer().getPlayerList().getPlayer(this.ownerUuid));
    }

    public SimpleContainer getFriendInventory() {
        return this.inventory;
    }

    public ItemStack insertIntoInventory(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack existing = this.inventory.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, remainder)) {
                continue;
            }
            int max = Math.min(existing.getMaxStackSize(), this.inventory.getMaxStackSize());
            int moved = Math.min(remainder.getCount(), max - existing.getCount());
            if (moved <= 0) {
                continue;
            }
            existing.grow(moved);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                this.inventory.setChanged();
                return ItemStack.EMPTY;
            }
        }

        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            if (!this.inventory.getItem(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(remainder.getCount(), Math.min(remainder.getMaxStackSize(), this.inventory.getMaxStackSize()));
            ItemStack inserted = remainder.copy();
            inserted.setCount(moved);
            this.inventory.setItem(slot, inserted);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                this.inventory.setChanged();
                return ItemStack.EMPTY;
            }
        }

        this.inventory.setChanged();
        return remainder;
    }

    public int countInventoryItems(Predicate<ItemStack> predicate) {
        int count = 0;
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean consumeInventoryItems(Predicate<ItemStack> predicate, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.countInventoryItems(predicate) < amount) {
            return false;
        }
        int remaining = amount;
        for (int slot = 0; slot < this.inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack.isEmpty() || !predicate.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) {
                this.inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        this.inventory.setChanged();
        return true;
    }

    public String getInventorySummary() {
        return this.inventoryProvider.summary();
    }

    public void syncVisibleEquipmentData() {
        if (this.level().isClientSide) {
            return;
        }
        ItemStack currentMainHand = this.inventoryProvider.getMainHandStack();
        if (!ItemStack.matches(this.entityData.get(DATA_MAIN_HAND_STACK), currentMainHand)) {
            this.entityData.set(DATA_MAIN_HAND_STACK, currentMainHand.copy());
        }
    }

    private void restorePendingTaskIfNeeded() {
        if (this.pendingRecoveredTask == null || this.currentTask != null) {
            return;
        }
        FriendTask recovered = this.pendingRecoveredTask;
        if (this.shouldWaitForRecoveredTaskOwner(recovered)) {
            this.waitForRecoveredTaskOwner(recovered);
            return;
        }
        Optional<String> restoreProblem = this.validateRecoveredTask(recovered);
        if (restoreProblem.isPresent()) {
            this.failRecoveredTask(restoreProblem.get());
            return;
        }
        this.pendingRecoveredTask = null;
        this.pendingRecoveredTaskTicks = 0;
        if (this.ownerUuid == null && recovered.playerUuid() != null) {
            this.ownerUuid = recovered.playerUuid();
        }
        this.friendBrain.startTask(recovered);
        this.friendBrain.say("Resumed saved task after reload: " + recovered.summary());
    }

    private Optional<String> validateRecoveredTask(FriendTask recovered) {
        if (recovered == null) {
            return Optional.of("the saved task data is missing");
        }
        if (recovered.playerUuid() != null) {
            if (this.ownerUuid != null && !this.ownerUuid.equals(recovered.playerUuid())) {
                return Optional.of("the saved task owner does not match this companion");
            }
            if (!(this.level() instanceof ServerLevel serverLevel)) {
                return Optional.of("the server world is not available yet");
            }
            if (serverLevel.getServer().getPlayerList().getPlayer(recovered.playerUuid()) == null) {
                return Optional.of("I cannot find the task owner");
            }
        }
        return this.friendBrain.validateRecoveredTask(recovered);
    }

    private boolean shouldWaitForRecoveredTaskOwner(FriendTask recovered) {
        if (recovered == null || recovered.playerUuid() == null) {
            return false;
        }
        if (this.ownerUuid != null && !this.ownerUuid.equals(recovered.playerUuid())) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(recovered.playerUuid()) == null;
    }

    private void waitForRecoveredTaskOwner(FriendTask recovered) {
        if (this.pendingRecoveredTaskTicks == 0
                || this.pendingRecoveredTaskTicks % RECOVERED_TASK_OWNER_REMINDER_TICKS == 0) {
            this.friendBrain.say("I am waiting for my task owner to come online before resuming: " + recovered.summary());
            this.setPersistenceRequired();
        }
        this.pendingRecoveredTaskTicks++;
        this.friendState = FriendState.WAITING_FOR_OWNER;
    }

    private void failRecoveredTask(String reason) {
        this.pendingRecoveredTask = null;
        this.pendingRecoveredTaskTicks = 0;
        this.currentTask = null;
        this.friendState = FriendState.ERROR;
        this.setPersistenceRequired();
        this.friendBrain.failTask("I could not resume my saved task because " + reason + ".");
    }

    private void tickItemPickup() {
        if (this.level() instanceof ServerLevel serverLevel
                && !serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        AABB pickupArea = this.getBoundingBox().inflate(3.0D);
        List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, pickupArea,
                item -> item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay());

        for (ItemEntity item : items) {
            if (item.hasPickUpDelay()) {
                continue;
            }
            ItemStack stack = item.getItem();
            ItemStack remainder = this.insertIntoInventory(stack);
            if (remainder.getCount() == stack.getCount()) {
                continue;
            }
            this.take(item, stack.getCount() - remainder.getCount());
            if (remainder.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remainder);
            }
        }
    }

    private void applyCompanionProfileName() {
        CompanionCharacterProfile profile = this.getCompanionProfile();
        if (profile.hasIdentity()) {
            this.setCustomName(Component.literal(profile.displayName()));
        }
    }

    private void syncCompanionProfileData() {
        this.entityData.set(DATA_COMPANION_SKIN_URL, this.getCompanionProfile().skinUrl());
    }

    private static CompoundTag saveCompanionProfile(CompanionCharacterProfile profile) {
        CompanionCharacterProfile safe = profile == null ? CompanionCharacterProfile.empty() : profile;
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", safe.id());
        tag.putString("Name", safe.name());
        tag.putString("ShortName", safe.shortName());
        tag.putString("GreetingInfo", safe.greetingInfo());
        tag.putString("Description", safe.description());
        tag.putString("SkinUrl", safe.skinUrl());
        ListTag voices = new ListTag();
        for (String voiceId : safe.voiceIds()) {
            voices.add(StringTag.valueOf(voiceId));
        }
        tag.put("VoiceIds", voices);
        return tag;
    }

    private static CompanionCharacterProfile loadCompanionProfile(CompoundTag tag) {
        List<String> voiceIds = new ArrayList<>();
        if (tag.contains("VoiceIds", Tag.TAG_LIST)) {
            ListTag voices = tag.getList("VoiceIds", Tag.TAG_STRING);
            for (int i = 0; i < voices.size(); i++) {
                voiceIds.add(voices.getString(i));
            }
        }
        return new CompanionCharacterProfile(
                tag.getString("Id"),
                tag.getString("Name"),
                tag.getString("ShortName"),
                tag.getString("GreetingInfo"),
                tag.getString("Description"),
                tag.getString("SkinUrl"),
                voiceIds
        );
    }
}
