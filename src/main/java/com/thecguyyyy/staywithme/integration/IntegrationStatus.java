package com.thecguyyyy.staywithme.integration;

import net.minecraftforge.fml.ModList;

import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationStatus {
    private IntegrationStatus() {
    }

    public static boolean isArchitecturyLoaded() {
        return isLoaded(ExternalModIds.ARCHITECTURY);
    }

    public static boolean isPlayerEngineLoaded() {
        return isLoaded(ExternalModIds.PLAYER_ENGINE);
    }

    public static boolean isSmartBrainLibLoaded() {
        return isLoaded(ExternalModIds.SMART_BRAIN_LIB);
    }

    public static boolean isBaritoneLoaded() {
        return isLoaded(ExternalModIds.BARITONE);
    }

    public static Map<String, Boolean> snapshot() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        status.put(ExternalModIds.ARCHITECTURY, isArchitecturyLoaded());
        status.put(ExternalModIds.PLAYER_ENGINE, isPlayerEngineLoaded());
        status.put(ExternalModIds.SMART_BRAIN_LIB, isSmartBrainLibLoaded());
        status.put(ExternalModIds.BARITONE, isBaritoneLoaded());
        return status;
    }

    public static String describe() {
        StringBuilder builder = new StringBuilder("Integrations:");
        snapshot().forEach((id, loaded) -> builder
                .append("\n- ")
                .append(id)
                .append(": ")
                .append(loaded ? "loaded" : "not loaded"));
        builder.append("\nCurrent behavior: Forge-native MVP controller is active unless integrations.usePlayerEngineController is true and PlayerEngine is loaded.");
        return builder.toString();
    }

    private static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
