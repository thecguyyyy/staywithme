package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.StayWithMeMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StayWithMeMod.MOD_ID);

    public static final RegistryObject<EntityType<FriendEntity>> FRIEND = ENTITIES.register("friend",
            () -> EntityType.Builder.<FriendEntity>of(FriendEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(StayWithMeMod.MOD_ID + ":friend"));

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FRIEND.get(), FriendEntity.createAttributes().build());
    }
}
