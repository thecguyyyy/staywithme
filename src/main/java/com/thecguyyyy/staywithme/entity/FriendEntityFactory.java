package com.thecguyyyy.staywithme.entity;

import com.thecguyyyy.staywithme.StayWithMeMod;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;

public final class FriendEntityFactory {
    private static final String PLAYERENGINE_ENTITY_CLASS =
            "com.thecguyyyy.staywithme.playerengine.PlayerEngineFriendEntity";
    private static boolean playerEngineEntityFailed;

    private FriendEntityFactory() {
    }

    public static FriendEntity create(EntityType<FriendEntity> type, Level level) {
        if (IntegrationStatus.isPlayerEngineLoaded() && !playerEngineEntityFailed) {
            try {
                Class<?> entityClass = Class.forName(PLAYERENGINE_ENTITY_CLASS);
                Constructor<?> constructor = entityClass.getConstructor(EntityType.class, Level.class);
                return (FriendEntity) constructor.newInstance(type, level);
            } catch (ReflectiveOperationException | LinkageError error) {
                playerEngineEntityFailed = true;
                StayWithMeMod.LOGGER.warn(
                        "PlayerEngine companion entity could not be created; using Forge fallback entity",
                        error
                );
            }
        }
        return new FriendEntity(type, level);
    }
}
