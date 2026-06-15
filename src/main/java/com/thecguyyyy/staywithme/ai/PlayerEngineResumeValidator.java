package com.thecguyyyy.staywithme.ai;

import com.thecguyyyy.staywithme.embodied.EmbodiedController;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

final class PlayerEngineResumeValidator {
    private final EmbodiedController body;
    private final FriendEntity friend;
    private final LocalInventoryFallback inventory;
    private final LocalHazardEscapeFallback hazardFallback;
    private final LocalThreatSafetyFallback threatFallback;
    private final LocalArmorEquipFallback armorFallback;
    private final ToIntFunction<FriendTask> requiredFuelItems;
    private final Predicate<FriendTask> hasFuelFallbackWorkflow;
    private final Predicate<FriendTask> smeltItemSatisfied;
    private final Predicate<FriendTask> pickupDroppedItemSatisfied;
    private final Predicate<FriendTask> buildingMaterialsSatisfied;

    PlayerEngineResumeValidator(
            EmbodiedController body,
            FriendEntity friend,
            LocalInventoryFallback inventory,
            LocalHazardEscapeFallback hazardFallback,
            LocalThreatSafetyFallback threatFallback,
            LocalArmorEquipFallback armorFallback,
            ToIntFunction<FriendTask> requiredFuelItems,
            Predicate<FriendTask> hasFuelFallbackWorkflow,
            Predicate<FriendTask> smeltItemSatisfied,
            Predicate<FriendTask> pickupDroppedItemSatisfied,
            Predicate<FriendTask> buildingMaterialsSatisfied
    ) {
        this.body = body;
        this.friend = friend;
        this.inventory = inventory;
        this.hazardFallback = hazardFallback;
        this.threatFallback = threatFallback;
        this.armorFallback = armorFallback;
        this.requiredFuelItems = requiredFuelItems;
        this.hasFuelFallbackWorkflow = hasFuelFallbackWorkflow;
        this.smeltItemSatisfied = smeltItemSatisfied;
        this.pickupDroppedItemSatisfied = pickupDroppedItemSatisfied;
        this.buildingMaterialsSatisfied = buildingMaterialsSatisfied;
    }

    Optional<String> validate(FriendTask task) {
        if (task == null || this.body.canUseHighLevelAcquisition()) {
            return Optional.empty();
        }

        return switch (task.type()) {
            case COLLECT_FOOD -> this.validateFood(task);
            case COLLECT_MEAT -> this.validateMeat(task);
            case COLLECT_FUEL -> this.validateFuel(task);
            case SMELT_ITEM -> this.validateSmelting(task);
            case GET_OUT_OF_WATER -> this.validateWaterEscape();
            case ESCAPE_LAVA -> this.validateLavaEscape();
            case RETREAT_FROM_HOSTILES -> this.validateHostileRetreat(task);
            case DODGE_PROJECTILES -> this.validateProjectileDodge(task);
            case PROJECTILE_PROTECTION_WALL -> this.validateProjectileWall(task);
            case PICKUP_DROPPED_ITEM -> this.validatePickup(task);
            case COLLECT_BUILDING_MATERIALS -> this.validateBuildingMaterials(task);
            case FISH, FARM, SLEEP_THROUGH_NIGHT, GIVE_ITEM, DEPOSIT_INVENTORY, PROTECT_PLAYER ->
                    Optional.of("the saved task needs PlayerEngine, but PlayerEngine is not available");
            case EQUIP_ARMOR -> this.validateArmorEquip(task);
            default -> Optional.empty();
        };
    }

    private Optional<String> validateFood(FriendTask task) {
        if (this.inventory.carriedFoodUnits() >= Math.max(1, task.amount())) {
            return Optional.empty();
        }
        return Optional.of("the saved food collection task needs PlayerEngine, but PlayerEngine is not available");
    }

    private Optional<String> validateMeat(FriendTask task) {
        if (this.inventory.carriedMeatFoodUnits() >= Math.max(1, task.amount())) {
            return Optional.empty();
        }
        return Optional.of("the saved meat collection task needs PlayerEngine, but PlayerEngine is not available");
    }

    private Optional<String> validateFuel(FriendTask task) {
        if (this.inventory.countCoalEquivalent() >= this.requiredFuelItems.applyAsInt(task)
                || this.hasFuelFallbackWorkflow.test(task)) {
            return Optional.empty();
        }
        return Optional.of("the saved fuel collection task needs PlayerEngine, but no charcoal fallback workflow is available");
    }

    private Optional<String> validateSmelting(FriendTask task) {
        if (this.smeltItemSatisfied.test(task)) {
            return Optional.empty();
        }
        return Optional.of("the saved smelting task needs PlayerEngine, but PlayerEngine is not available");
    }

    private Optional<String> validateWaterEscape() {
        if (!this.friend.isInWater() && this.friend.onGround()) {
            return Optional.empty();
        }
        if (this.friend.level() instanceof ServerLevel serverLevel
                && this.hazardFallback.findDryStandPosition(serverLevel, 12).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("the saved water escape task needs PlayerEngine, but PlayerEngine is not available");
    }

    private Optional<String> validateLavaEscape() {
        if (!this.friend.isInLava() && !this.friend.isOnFire()) {
            return Optional.empty();
        }
        if (this.friend.level() instanceof ServerLevel serverLevel
                && this.hazardFallback.findLavaSafeStandPosition(serverLevel, 12).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("the saved lava escape task needs PlayerEngine, but no local lava-safe stand position is reachable");
    }

    private Optional<String> validateHostileRetreat(FriendTask task) {
        int distance = Math.max(4, task.amount() <= 0 ? 16 : task.amount());
        Optional<LivingEntity> hostile = this.friend.getPerception().nearestHostile(distance);
        if (hostile.isEmpty()) {
            return Optional.empty();
        }
        if (this.friend.level() instanceof ServerLevel serverLevel
                && this.threatFallback.findThreatRetreatStandPos(serverLevel, hostile.get(), distance).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("the saved hostile-retreat task needs PlayerEngine, but no local retreat point is reachable");
    }

    private Optional<String> validateProjectileDodge(FriendTask task) {
        int horizontalDistance = Math.max(1, task.amount() <= 0 ? 4 : task.amount());
        int verticalDistance = 3;
        if (!this.threatFallback.hasProjectileDodgeThreat(horizontalDistance, verticalDistance)
                || this.threatFallback.findProjectileDodgeTarget(horizontalDistance, verticalDistance).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("the saved projectile-dodge task needs PlayerEngine or a reachable local dodge target");
    }

    private Optional<String> validateProjectileWall(FriendTask task) {
        int range = Math.max(4, task.amount() <= 0 ? 16 : task.amount());
        Optional<Skeleton> skeleton = this.threatFallback.nearestSkeletonThreat(range);
        if (skeleton.isEmpty()) {
            return Optional.empty();
        }
        if (this.friend.level() instanceof ServerLevel serverLevel
                && this.threatFallback.findProjectileWallPlaceTarget(task, serverLevel, skeleton.get()).isPresent()) {
            return Optional.empty();
        }
        return Optional.of("the saved projectile-wall task needs PlayerEngine or a reachable local wall placement with a carried throwaway block");
    }

    private Optional<String> validatePickup(FriendTask task) {
        if (this.pickupDroppedItemSatisfied.test(task)) {
            return Optional.empty();
        }
        return Optional.of("the saved pickup task needs PlayerEngine, but PlayerEngine is not available");
    }

    private Optional<String> validateBuildingMaterials(FriendTask task) {
        if (this.buildingMaterialsSatisfied.test(task)) {
            return Optional.empty();
        }
        return Optional.of("the saved building-material task needs PlayerEngine, but PlayerEngine is not available");
    }

    private Optional<String> validateArmorEquip(FriendTask task) {
        String target = task.target() == null || task.target().isBlank() ? "iron" : task.target();
        if (this.armorFallback.canEquip(target)) {
            return Optional.empty();
        }
        return Optional.of("the saved armor equip task needs PlayerEngine to obtain missing armor");
    }
}
