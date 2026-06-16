package com.thecguyyyy.staywithme.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class StayWithMeConfig {
    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.BooleanValue LLM_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> LLM_BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> LLM_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> LLM_MODEL;
    public static final ForgeConfigSpec.IntValue LLM_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue LLM_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue USE_PLAYERENGINE_CONTROLLER;
    public static final ForgeConfigSpec.BooleanValue USE_SMARTBRAINLIB_BEHAVIORS;
    public static final ForgeConfigSpec.BooleanValue USE_BARITONE_WHEN_AVAILABLE;
    public static final ForgeConfigSpec.BooleanValue AUTO_SUMMON_COMPANION;
    public static final ForgeConfigSpec.BooleanValue DISMISS_AUTO_SUMMONED_COMPANIONS_ON_LOGOUT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("llm");
        LLM_ENABLED = builder
                .comment("Enable OpenAI-compatible LLM planning. If false, local fallback rules are used.")
                .define("enabled", false);
        LLM_BASE_URL = builder
                .comment("OpenAI-compatible base URL. Examples: https://api.openai.com/v1, https://api.deepseek.com, http://localhost:11434/v1")
                .define("baseUrl", "https://api.openai.com/v1");
        LLM_API_KEY = builder
                .comment("API key. Leave blank to force local fallback planning.")
                .define("apiKey", "");
        LLM_MODEL = builder
                .comment("LLM model name.")
                .define("model", "gpt-4o-mini");
        LLM_TIMEOUT_SECONDS = builder
                .comment("HTTP timeout for LLM requests.")
                .defineInRange("timeoutSeconds", 20, 3, 120);
        LLM_COOLDOWN_SECONDS = builder
                .comment("Per-player cooldown between LLM requests. Cooldown hits fall back to local parsing.")
                .defineInRange("cooldownSeconds", 5, 0, 300);
        builder.pop();

        builder.push("companion");
        AUTO_SUMMON_COMPANION = builder
                .comment("Player2NPC-style lifecycle: automatically ensure a companion exists near the player when they join.")
                .define("autoSummonCompanion", true);
        DISMISS_AUTO_SUMMONED_COMPANIONS_ON_LOGOUT = builder
                .comment("Dismiss companions created by the automatic join lifecycle when the owner logs out. Manually spawned companions are not dismissed by this option.")
                .define("dismissAutoSummonedCompanionsOnLogout", true);
        builder.pop();

        builder.push("integrations");
        USE_PLAYERENGINE_CONTROLLER = builder
                .comment("Prefer PlayerEngine/TaskCatalogue for broad survival tasks when the PlayerEngine mod is loaded. Forge-native execution remains the fallback.")
                .define("usePlayerEngineController", true);
        USE_SMARTBRAINLIB_BEHAVIORS = builder
                .comment("Reserved for a later SmartBrainLib behavior-tree implementation. Current MVP still uses LocalBehaviorController.")
                .define("useSmartBrainLibBehaviors", false);
        USE_BARITONE_WHEN_AVAILABLE = builder
                .comment("Reserved for a later direct Baritone adapter. PlayerEngine already exposes an Automatone/Baritone API when enabled.")
                .define("useBaritoneWhenAvailable", false);
        builder.pop();

        SERVER_SPEC = builder.build();
    }

    private StayWithMeConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "staywithme-server.toml");
    }

    public static boolean isLlmConfigured() {
        return LLM_ENABLED.get() && !LLM_API_KEY.get().isBlank();
    }
}
