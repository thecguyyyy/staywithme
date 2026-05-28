package com.thecguyyyy.staywithme.item;

import com.thecguyyyy.staywithme.StayWithMeMod;
import com.thecguyyyy.staywithme.entity.ModEntities;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, StayWithMeMod.MOD_ID);

    public static final RegistryObject<Item> FRIEND_SPAWN_EGG = ITEMS.register("friend_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.FRIEND, 0x4ECDC4, 0x2B2D42, new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(FRIEND_SPAWN_EGG);
        }
    }
}
