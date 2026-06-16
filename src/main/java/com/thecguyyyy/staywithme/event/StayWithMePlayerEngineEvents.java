package com.thecguyyyy.staywithme.event;

import com.thecguyyyy.staywithme.playerengine.PlayerEngineGlobalTickBridge;
import net.minecraftforge.event.TickEvent;

public final class StayWithMePlayerEngineEvents {
    private StayWithMePlayerEngineEvents() {
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PlayerEngineGlobalTickBridge.ensureInitialized();
    }
}
