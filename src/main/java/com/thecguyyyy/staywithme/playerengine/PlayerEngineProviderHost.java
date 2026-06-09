package com.thecguyyyy.staywithme.playerengine;

import net.minecraft.server.level.ServerLevel;

public interface PlayerEngineProviderHost {
    void syncPlayerEngineStateFromFriend();

    void tickPlayerEngineManagers(ServerLevel level);

    void syncPlayerEngineStateToFriend();

    String playerEngineProviderStatus();
}
