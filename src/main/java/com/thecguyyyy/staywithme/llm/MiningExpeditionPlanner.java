package com.thecguyyyy.staywithme.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thecguyyyy.staywithme.ai.WorldSnapshot;
import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.util.JsonUtils;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MiningExpeditionPlanner {
    public static final String SYSTEM_PROMPT = """
            You are the mining expedition strategist for StayWithMe, a Minecraft 1.20.1 embodied AI companion.

            Your job is to output a high-level, survival-safe mining expedition plan as JSON.
            You do not directly control movement, camera, block breaking, attacking, or ticks.
            The local controller and PlayerEngine/Baritone adapters execute deterministic actions.

            Output rules:
            - Output exactly one JSON object and nothing else.
            - Do not output markdown.
            - Do not invent exact coordinates.
            - Choose strategyMode from: SURFACE_SEARCH, CAVE_SEARCH, DESCEND_TO_LAYER, BRANCH_MINE, RETURN_AND_RESUPPLY, ABORT_UNSAFE.
            - executionActions must use high-level verbs only, not per-tick commands.
            - Include explicit safetyRules and resupplyTriggers.
            - Include resupply triggers for low health, nearly full inventory, hostile pressure, low food without carried food, low torches, and low usable pickaxe durability when applicable.
            - Prefer survival-feasible plans: prepare tools first, keep food/torches/spare tools available, avoid lava, avoid straight-down mining, keep a route home.

            JSON schema:
            {
              "resourceId": "item id",
              "amount": 1,
              "targetDimension": "dimension id",
              "preferredYMin": -59,
              "preferredYMax": -53,
              "strategyMode": "BRANCH_MINE",
              "requiredTool": "tool hint",
              "preparation": "short preparation plan",
              "executionActions": ["PREPARE_REQUIRED_TOOL", "DESCEND_TO_TARGET_LAYER", "MINE_TARGET_RESOURCE", "RETURN_TO_OWNER_OR_SUPPLY_POINT"],
              "safetyRules": ["short safety rule"],
              "resupplyTriggers": ["short trigger"],
              "reason": "short factual reason",
              "confidence": "low | medium | high",
              "source": "llm"
            }
            """;

    private static final Map<UUID, Long> LAST_PLAN_MS = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CompletableFuture<MiningExpeditionPlan> planAsync(ServerPlayer player, Optional<FriendEntity> friend, String resourceId, int amount) {
        String normalized = MiningTargetRegistry.normalize(resourceId);
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        if (!StayWithMeConfig.isLlmConfigured()) {
            return CompletableFuture.completedFuture(this.localFallback(normalized, amount, "LLM disabled or API key is empty."));
        }

        long now = System.currentTimeMillis();
        long cooldownMs = StayWithMeConfig.LLM_COOLDOWN_SECONDS.get() * 1000L;
        long last = LAST_PLAN_MS.getOrDefault(player.getUUID(), 0L);
        if (cooldownMs > 0 && now - last < cooldownMs) {
            return CompletableFuture.completedFuture(this.localFallback(normalized, amount, "LLM cooldown active."));
        }
        LAST_PLAN_MS.put(player.getUUID(), now);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", StayWithMeConfig.LLM_MODEL.get());
        payload.addProperty("temperature", 0.1D);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", SYSTEM_PROMPT);
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", this.buildUserPrompt(player, friend, normalized, amount, memory));
        messages.add(user);
        payload.add("messages", messages);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(StayWithMeConfig.LLM_BASE_URL.get()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(StayWithMeConfig.LLM_TIMEOUT_SECONDS.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload)));

        if (!StayWithMeConfig.LLM_API_KEY.get().isBlank()) {
            builder.header("Authorization", "Bearer " + StayWithMeConfig.LLM_API_KEY.get());
        }

        return this.httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + response.body());
                    }
                    MiningExpeditionPlan plan = JsonUtils.fromJsonObjectText(extractContent(response.body()), MiningExpeditionPlan.class);
                    plan.normalize(normalized, amount, "llm");
                    return plan;
                })
                .exceptionally(error -> this.localFallback(normalized, amount, "LLM mining expedition planning failed: " + error.getMessage()));
    }

    private MiningExpeditionPlan localFallback(String resourceId, int amount, String reason) {
        String normalized = resourceId.toLowerCase(Locale.ROOT);
        MiningExpeditionPlan plan = MiningExpeditionPlan.fallback(resourceId, amount, reason);
        if (normalized.contains("coal")) {
            plan.preferredYMin = 48;
            plan.preferredYMax = 136;
            plan.strategyMode = "CAVE_SEARCH";
            plan.requiredTool = "wooden pickaxe or better";
            plan.preparation = "Prepare a wooden pickaxe and keep inventory space for coal.";
            plan.confidence = "medium";
        } else if (normalized.contains("raw_iron") || normalized.contains("iron")) {
            plan.preferredYMin = 0;
            plan.preferredYMax = 80;
            plan.strategyMode = "CAVE_SEARCH";
            plan.requiredTool = "stone pickaxe or better";
            plan.preparation = "Prepare a stone pickaxe before mining iron ore.";
            plan.confidence = "medium";
        } else if (normalized.contains("diamond") || normalized.contains("redstone")) {
            plan.preferredYMin = -59;
            plan.preferredYMax = -53;
            plan.strategyMode = "BRANCH_MINE";
            plan.requiredTool = "iron pickaxe or better";
            plan.preparation = "Prepare an iron pickaxe before mining deep deepslate ores.";
            plan.confidence = "medium";
        } else if (normalized.contains("lapis")) {
            plan.preferredYMin = -32;
            plan.preferredYMax = 32;
            plan.strategyMode = "BRANCH_MINE";
            plan.requiredTool = "stone pickaxe or better";
            plan.preparation = "Prepare a stone pickaxe and mine around mid-to-low Overworld layers.";
            plan.confidence = "medium";
        } else if (normalized.contains("quartz")) {
            plan.targetDimension = "minecraft:the_nether";
            plan.preferredYMin = 10;
            plan.preferredYMax = 118;
            plan.strategyMode = "SURFACE_SEARCH";
            plan.requiredTool = "wooden pickaxe or better";
            plan.preparation = "Only execute after the player provides Nether access; avoid lava and hostile areas.";
            plan.confidence = "medium";
        }
        plan.source = "local_vanilla_fallback";
        plan.normalize(resourceId, amount, "local_vanilla_fallback");
        return plan;
    }

    private String buildUserPrompt(ServerPlayer player, Optional<FriendEntity> friend, String resourceId, int amount, FriendMemory memory) {
        String worldSnapshot = friend
                .map(entity -> JsonUtils.toJson(WorldSnapshot.capture(player, entity)))
                .orElse("No companion entity nearby.");
        return """
                Create a high-level mining expedition plan.

                Resource id: %s
                Amount: %d
                Minecraft version: 1.20.1
                Current dimension: %s
                Player position: %d,%d,%d
                World snapshot JSON:
                %s
                Portable memory JSON:
                %s

                Important: return strategy JSON only. The local controller will translate high-level actions into survival movement/mining.
                """.formatted(
                resourceId,
                Math.max(1, amount),
                player.serverLevel().dimension().location(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ(),
                worldSnapshot,
                JsonUtils.toJson(memory)
        );
    }

    private static String extractContent(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM response did not contain choices.");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IllegalStateException("LLM response did not contain message.content.");
        }
        return message.get("content").getAsString();
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null || value.isBlank() ? "https://api.openai.com/v1" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
