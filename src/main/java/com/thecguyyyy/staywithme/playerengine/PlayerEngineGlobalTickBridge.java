package com.thecguyyyy.staywithme.playerengine;

import com.thecguyyyy.staywithme.StayWithMeMod;

public final class PlayerEngineGlobalTickBridge {
    private static final String PLAYER_ENGINE_CONTROLLER_CLASS = "com.player2.playerengine.PlayerEngineController";
    private static boolean initialized;
    private static boolean disabled;

    private PlayerEngineGlobalTickBridge() {
    }

    public static void ensureInitialized() {
        if (initialized || disabled || !PlayerEngineCompat.isAvailable()) {
            return;
        }

        try {
            Class.forName(PLAYER_ENGINE_CONTROLLER_CLASS, true, PlayerEngineGlobalTickBridge.class.getClassLoader());
            initialized = true;
            StayWithMeMod.LOGGER.info("PlayerEngine global server tick hook initialized.");
        } catch (ClassNotFoundException | LinkageError | RuntimeException error) {
            disabled = true;
            StayWithMeMod.LOGGER.warn(
                    "PlayerEngine global server tick hook could not be initialized: {}: {}",
                    error.getClass().getSimpleName(),
                    error.getMessage()
            );
        }
    }
}
