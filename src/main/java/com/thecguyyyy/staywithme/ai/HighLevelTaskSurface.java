package com.thecguyyyy.staywithme.ai;

public final class HighLevelTaskSurface {
    public static final String ENTRY_POINTS = "FOLLOW_PLAYER, GO_TO_POSITION, PLACE_BLOCK, RETURN_TO_PLAYER, GET_ITEM, PICKUP_DROPPED_ITEM, GIVE_ITEM, DEPOSIT_INVENTORY, CRAFT_ITEM, SMELT_ITEM, COLLECT_WOOD, COLLECT_BUILDING_MATERIALS, COLLECT_FOOD, COLLECT_MEAT, COLLECT_FUEL, FISH, FARM, EXPLORE, SLEEP_THROUGH_NIGHT, GET_OUT_OF_WATER, ESCAPE_LAVA, CLEAR_LIQUID, PUT_OUT_FIRE, EQUIP_ARMOR, MINE_RESOURCE, MINING_EXPEDITION, ATTACK_NEARBY_HOSTILE, PROTECT_PLAYER, RETREAT_FROM_HOSTILES, RETREAT_FROM_CREEPERS, DODGE_PROJECTILES, PROJECTILE_PROTECTION_WALL, SAY, STOP";
    public static final String PLAYERENGINE_FIRST_SUMMARY = "FollowPlayerTask/GetToEntityTask/GetToBlockTask/GetToYTask/TimeoutWanderTask, PlaceBlockTask/PlaceStructureBlockTask, TaskCatalogue get, SmeltInFurnaceTask, PickupDroppedItemTask, GetBuildingMaterialsTask, GiveItemToPlayerTask, StoreInAnyContainerTask, KillEntityTask/HeroTask/RunAwayFromHostilesTask/RunAwayFromCreepersTask/DodgeProjectilesTask/ProjectileProtectionWallTask, SleepThroughNightTask/GetOutOfWaterTask/EscapeFromLavaTask/ClearLiquidTask/PutOutFireTask, CollectFoodTask/CollectMeatTask/CollectFuelTask, fish/farm/equip, movement/pathing/mining where available";

    private HighLevelTaskSurface() {
    }
}
