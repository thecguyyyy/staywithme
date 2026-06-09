package com.thecguyyyy.staywithme.embodied;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

public interface EmbodiedController {
    default void tick() {
    }

    default String name() {
        return this.getClass().getSimpleName();
    }

    default String status() {
        return this.name();
    }

    void stop();

    void moveTo(double x, double y, double z, double speed);

    void moveTo(Entity target, double speed);

    default boolean followPlayer(String playerName, double followDistance) {
        return false;
    }

    default boolean returnToEntity(Entity target, double closeEnoughDistance) {
        return false;
    }

    default boolean hasReturnToEntityFinished(Entity target, double closeEnoughDistance) {
        return false;
    }

    default void moveTo(BlockPos pos, double speed) {
        this.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, speed);
    }

    default boolean goToBlock(BlockPos pos, double closeEnoughDistance) {
        this.moveTo(pos, 1.0D);
        return true;
    }

    default boolean hasGoToBlockFinished(BlockPos pos, double closeEnoughDistance) {
        return false;
    }

    default boolean placeBlockAt(BlockPos pos, String blockTarget) {
        return false;
    }

    default boolean hasPlaceBlockAtFinished(BlockPos pos, String blockTarget) {
        return false;
    }

    default void moveToNearby(BlockPos pos, double speed) {
        this.moveTo(pos, speed);
    }

    void say(String message);

    boolean attack(Entity target);

    default boolean attackTarget(Entity target) {
        return this.attack(target);
    }

    default boolean hasAttackTargetFinished(Entity target) {
        return target == null || !target.isAlive();
    }

    default boolean protectPlayer() {
        return false;
    }

    default boolean retreatFromHostiles(double distance) {
        return false;
    }

    default boolean hasRetreatFromHostilesFinished(double distance) {
        return false;
    }

    default boolean retreatFromCreepers(double distance) {
        return false;
    }

    default boolean hasRetreatFromCreepersFinished(double distance) {
        return false;
    }

    default boolean dodgeProjectiles(double horizontalDistance, double verticalDistance) {
        return false;
    }

    default boolean hasDodgeProjectilesFinished(double horizontalDistance, double verticalDistance) {
        return false;
    }

    default boolean projectileProtectionWall() {
        return false;
    }

    default boolean hasProjectileProtectionWallFinished() {
        return false;
    }

    boolean breakBlock(BlockPos pos);

    default boolean mineBlocks(int count, Block... blocks) {
        return false;
    }

    default boolean isMining() {
        return false;
    }

    default boolean acquireItem(String catalogueName, int count) {
        return false;
    }

    default boolean collectBuildingMaterials(int count) {
        return false;
    }

    default boolean hasBuildingMaterialsCollectionFinished(int count) {
        return false;
    }

    default boolean pickupDroppedItem(String itemName, int count) {
        return false;
    }

    default boolean hasPickupDroppedItemFinished(String itemName, int count) {
        return false;
    }

    default boolean giveItemToPlayer(String playerName, String catalogueName, int count) {
        return false;
    }

    default boolean hasGiveItemFinished(String playerName, String catalogueName, int count) {
        return false;
    }

    default boolean depositInventory() {
        return false;
    }

    default boolean hasDepositInventoryFinished() {
        return false;
    }

    default boolean isAcquiringItem() {
        return false;
    }

    default boolean hasAcquisitionFinished(String catalogueName, int count) {
        return false;
    }

    default boolean collectFood(int foodUnits) {
        return false;
    }

    default boolean hasFoodCollectionFinished(int foodUnits) {
        return false;
    }

    default boolean collectMeat(int foodUnits) {
        return false;
    }

    default boolean hasMeatCollectionFinished(int foodUnits) {
        return false;
    }

    default boolean collectFuel(int fuelItems) {
        return false;
    }

    default boolean hasFuelCollectionFinished(int fuelItems) {
        return false;
    }

    default boolean smeltItem(String target, int count) {
        return false;
    }

    default boolean hasSmeltItemFinished(String target, int count) {
        return false;
    }

    default boolean fish() {
        return false;
    }

    default boolean farm(int range) {
        return false;
    }

    default boolean explore(double distance) {
        return false;
    }

    default boolean hasExploreFinished(double distance) {
        return false;
    }

    default boolean sleepThroughNight() {
        return false;
    }

    default boolean hasSleepThroughNightFinished() {
        return false;
    }

    default boolean getOutOfWater() {
        return false;
    }

    default boolean hasGetOutOfWaterFinished() {
        return false;
    }

    default boolean escapeLava() {
        return false;
    }

    default boolean hasEscapeLavaFinished() {
        return false;
    }

    default boolean putOutFire(BlockPos firePosition) {
        return false;
    }

    default boolean hasPutOutFireFinished(BlockPos firePosition) {
        return false;
    }

    default boolean equipArmor(String target) {
        return false;
    }

    default boolean hasArmorEquipmentFinished(String target) {
        return false;
    }

    default boolean canUseHighLevelAcquisition() {
        return false;
    }

    default String highLevelAcquisitionStatus() {
        return "unavailable";
    }
}
