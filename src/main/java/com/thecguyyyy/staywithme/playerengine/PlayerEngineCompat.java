package com.thecguyyyy.staywithme.playerengine;

import com.thecguyyyy.staywithme.integration.IntegrationStatus;

public final class PlayerEngineCompat {
    private PlayerEngineCompat() {
    }

    public static boolean isAvailable() {
        return IntegrationStatus.isPlayerEngineLoaded();
    }

    public static String availabilitySummary() {
        return isAvailable() ? "playerengine=loaded" : "playerengine=missing";
    }
}
