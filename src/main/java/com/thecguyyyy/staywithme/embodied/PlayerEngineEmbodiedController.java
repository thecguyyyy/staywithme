package com.thecguyyyy.staywithme.embodied;

import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.playerengine.FriendPlayerEngineController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

public class PlayerEngineEmbodiedController implements EmbodiedController {
    private final FriendEntity friend;
    private final DummyEmbodiedController fallback;
    private final FriendPlayerEngineController playerEngine;

    public PlayerEngineEmbodiedController(FriendEntity friend) {
        this.friend = friend;
        this.fallback = new DummyEmbodiedController(friend);
        this.playerEngine = new FriendPlayerEngineController(friend);
    }

    @Override
    public void tick() {
        this.playerEngine.tick();
    }

    @Override
    public String name() {
        return this.playerEngine.canUseHighLevelAcquisition() ? "playerengine_first" : "forge_native";
    }

    @Override
    public String status() {
        return this.playerEngine.status() + ", fallback=forge_native";
    }

    @Override
    public void stop() {
        this.playerEngine.stop();
        this.fallback.stop();
    }

    @Override
    public void moveTo(double x, double y, double z, double speed) {
        if (!this.playerEngine.moveTo(x, y, z)) {
            this.fallback.moveTo(x, y, z, speed);
        }
    }

    @Override
    public void moveTo(Entity target, double speed) {
        if (!this.playerEngine.follow(target)) {
            this.fallback.moveTo(target, speed);
        }
    }

    @Override
    public boolean followPlayer(String playerName, double followDistance) {
        return this.playerEngine.followPlayer(playerName, followDistance);
    }

    @Override
    public boolean returnToEntity(Entity target, double closeEnoughDistance) {
        return this.playerEngine.returnToEntity(target, closeEnoughDistance);
    }

    @Override
    public boolean hasReturnToEntityFinished(Entity target, double closeEnoughDistance) {
        return this.playerEngine.hasReturnToEntityFinished(target, closeEnoughDistance);
    }

    @Override
    public boolean goToBlock(BlockPos pos, double closeEnoughDistance) {
        return this.playerEngine.goToBlock(pos, closeEnoughDistance);
    }

    @Override
    public boolean hasGoToBlockFinished(BlockPos pos, double closeEnoughDistance) {
        return this.playerEngine.hasGoToBlockFinished(pos, closeEnoughDistance);
    }

    @Override
    public boolean goToYLevel(int yLevel) {
        return this.playerEngine.goToYLevel(yLevel);
    }

    @Override
    public boolean hasGoToYLevelFinished(int yLevel) {
        return this.playerEngine.hasGoToYLevelFinished(yLevel);
    }

    @Override
    public boolean placeBlockAt(BlockPos pos, String blockTarget) {
        return this.playerEngine.placeBlockAt(pos, blockTarget);
    }

    @Override
    public boolean hasPlaceBlockAtFinished(BlockPos pos, String blockTarget) {
        return this.playerEngine.hasPlaceBlockAtFinished(pos, blockTarget);
    }

    @Override
    public void moveToNearby(BlockPos pos, double speed) {
        if (!this.playerEngine.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)) {
            this.fallback.moveToNearby(pos, speed);
        }
    }

    @Override
    public void say(String message) {
        this.fallback.say(message);
    }

    @Override
    public boolean attack(Entity target) {
        if (target instanceof LivingEntity livingEntity) {
            this.friend.setTarget(livingEntity);
        }
        return this.fallback.attack(target);
    }

    @Override
    public boolean attackTarget(Entity target) {
        if (target instanceof LivingEntity livingEntity) {
            this.friend.setTarget(livingEntity);
        }
        return this.playerEngine.attackTarget(target);
    }

    @Override
    public boolean hasAttackTargetFinished(Entity target) {
        return this.playerEngine.hasAttackTargetFinished(target);
    }

    @Override
    public boolean protectPlayer() {
        return this.playerEngine.protectPlayer();
    }

    @Override
    public boolean retreatFromHostiles(double distance) {
        return this.playerEngine.retreatFromHostiles(distance);
    }

    @Override
    public boolean hasRetreatFromHostilesFinished(double distance) {
        return this.playerEngine.hasRetreatFromHostilesFinished(distance);
    }

    @Override
    public boolean retreatFromCreepers(double distance) {
        return this.playerEngine.retreatFromCreepers(distance);
    }

    @Override
    public boolean hasRetreatFromCreepersFinished(double distance) {
        return this.playerEngine.hasRetreatFromCreepersFinished(distance);
    }

    @Override
    public boolean dodgeProjectiles(double horizontalDistance, double verticalDistance) {
        return this.playerEngine.dodgeProjectiles(horizontalDistance, verticalDistance);
    }

    @Override
    public boolean hasDodgeProjectilesFinished(double horizontalDistance, double verticalDistance) {
        return this.playerEngine.hasDodgeProjectilesFinished(horizontalDistance, verticalDistance);
    }

    @Override
    public boolean projectileProtectionWall() {
        return this.playerEngine.projectileProtectionWall();
    }

    @Override
    public boolean hasProjectileProtectionWallFinished() {
        return this.playerEngine.hasProjectileProtectionWallFinished();
    }

    @Override
    public boolean breakBlock(BlockPos pos) {
        return this.fallback.breakBlock(pos);
    }

    @Override
    public boolean mineBlocks(int count, Block... blocks) {
        return this.playerEngine.mineBlocks(count, blocks);
    }

    @Override
    public boolean isMining() {
        return this.playerEngine.isMining();
    }

    @Override
    public boolean acquireItem(String catalogueName, int count) {
        return this.playerEngine.acquireItem(catalogueName, count);
    }

    @Override
    public boolean collectBuildingMaterials(int count) {
        return this.playerEngine.collectBuildingMaterials(count);
    }

    @Override
    public boolean hasBuildingMaterialsCollectionFinished(int count) {
        return this.playerEngine.hasBuildingMaterialsCollectionFinished(count);
    }

    @Override
    public boolean pickupDroppedItem(String itemName, int count) {
        return this.playerEngine.pickupDroppedItem(itemName, count);
    }

    @Override
    public boolean hasPickupDroppedItemFinished(String itemName, int count) {
        return this.playerEngine.hasPickupDroppedItemFinished(itemName, count);
    }

    @Override
    public boolean giveItemToPlayer(String playerName, String catalogueName, int count) {
        return this.playerEngine.giveItemToPlayer(playerName, catalogueName, count);
    }

    @Override
    public boolean hasGiveItemFinished(String playerName, String catalogueName, int count) {
        return this.playerEngine.hasGiveItemFinished(playerName, catalogueName, count);
    }

    @Override
    public boolean depositInventory() {
        return this.playerEngine.depositInventory();
    }

    @Override
    public boolean hasDepositInventoryFinished() {
        return this.playerEngine.hasDepositInventoryFinished();
    }

    @Override
    public boolean isAcquiringItem() {
        return this.playerEngine.isAcquiringItem();
    }

    @Override
    public boolean hasAcquisitionFinished(String catalogueName, int count) {
        return this.playerEngine.hasAcquisitionFinished(catalogueName, count);
    }

    @Override
    public boolean collectFood(int foodUnits) {
        return this.playerEngine.collectFood(foodUnits);
    }

    @Override
    public boolean hasFoodCollectionFinished(int foodUnits) {
        return this.playerEngine.hasFoodCollectionFinished(foodUnits);
    }

    @Override
    public boolean collectMeat(int foodUnits) {
        return this.playerEngine.collectMeat(foodUnits);
    }

    @Override
    public boolean hasMeatCollectionFinished(int foodUnits) {
        return this.playerEngine.hasMeatCollectionFinished(foodUnits);
    }

    @Override
    public boolean collectFuel(int fuelItems) {
        return this.playerEngine.collectFuel(fuelItems);
    }

    @Override
    public boolean hasFuelCollectionFinished(int fuelItems) {
        return this.playerEngine.hasFuelCollectionFinished(fuelItems);
    }

    @Override
    public boolean smeltItem(String target, int count) {
        return this.playerEngine.smeltItem(target, count);
    }

    @Override
    public boolean hasSmeltItemFinished(String target, int count) {
        return this.playerEngine.hasSmeltItemFinished(target, count);
    }

    @Override
    public boolean fish() {
        return this.playerEngine.fish();
    }

    @Override
    public boolean farm(int range) {
        return this.playerEngine.farm(range);
    }

    @Override
    public boolean explore(double distance) {
        return this.playerEngine.explore(distance);
    }

    @Override
    public boolean hasExploreFinished(double distance) {
        return this.playerEngine.hasExploreFinished(distance);
    }

    @Override
    public boolean sleepThroughNight() {
        return this.playerEngine.sleepThroughNight();
    }

    @Override
    public boolean hasSleepThroughNightFinished() {
        return this.playerEngine.hasSleepThroughNightFinished();
    }

    @Override
    public boolean getOutOfWater() {
        return this.playerEngine.getOutOfWater();
    }

    @Override
    public boolean hasGetOutOfWaterFinished() {
        return this.playerEngine.hasGetOutOfWaterFinished();
    }

    @Override
    public boolean escapeLava() {
        return this.playerEngine.escapeLava();
    }

    @Override
    public boolean hasEscapeLavaFinished() {
        return this.playerEngine.hasEscapeLavaFinished();
    }

    @Override
    public boolean clearLiquid(BlockPos liquidPosition) {
        return this.playerEngine.clearLiquid(liquidPosition);
    }

    @Override
    public boolean hasClearLiquidFinished(BlockPos liquidPosition) {
        return this.playerEngine.hasClearLiquidFinished(liquidPosition);
    }

    @Override
    public boolean putOutFire(BlockPos firePosition) {
        return this.playerEngine.putOutFire(firePosition);
    }

    @Override
    public boolean hasPutOutFireFinished(BlockPos firePosition) {
        return this.playerEngine.hasPutOutFireFinished(firePosition);
    }

    @Override
    public boolean equipArmor(String target) {
        return this.playerEngine.equipArmor(target);
    }

    @Override
    public boolean hasArmorEquipmentFinished(String target) {
        return this.playerEngine.hasArmorEquipmentFinished(target);
    }

    @Override
    public boolean canUseHighLevelAcquisition() {
        return this.playerEngine.canUseHighLevelAcquisition();
    }

    @Override
    public String highLevelAcquisitionStatus() {
        return this.playerEngine.acquisitionStatus();
    }
}
