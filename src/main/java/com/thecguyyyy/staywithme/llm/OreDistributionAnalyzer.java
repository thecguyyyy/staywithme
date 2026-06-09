package com.thecguyyyy.staywithme.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.util.JsonUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class OreDistributionAnalyzer {
    public static final String SYSTEM_PROMPT = """
            You are the mining-knowledge analyst for a Minecraft embodied AI companion.

            Output rules:
            - Output exactly one JSON object and nothing else.
            - Do not output markdown.
            - Do not invent world-specific coordinates.
            - For vanilla Minecraft 1.20.1, use known ore generation and survival constraints.
            - Treat any validated Minecraft 1.20.1 distribution supplied in the user prompt as authoritative.
            - For modded resources, state uncertainty clearly and suggest how to learn from datapacks, JEI/EMI, tags, and observed mining results.
            - Prefer strategies that a survival-mode player-like NPC can execute through pathing, mining, crafting, smelting, and safety checks.

            JSON schema:
            {
              "resourceId": "item or block id",
              "oreBlockId": "likely ore block id or unknown",
              "dimension": "dimension hint",
              "yLevelHint": "short y-level/range hint",
              "miningStrategy": "short actionable strategy",
              "requiredTool": "tool/mining-level hint",
              "safetyNotes": "short safety note",
              "confidence": "low | medium | high",
              "steps": ["short step 1", "short step 2"]
            }
            """;

    private static final Map<UUID, Long> LAST_ANALYSIS_MS = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CompletableFuture<OreDistributionAnalysis> analyzeAsync(ServerPlayer player, String resourceId) {
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        if (!StayWithMeConfig.isLlmConfigured()) {
            return CompletableFuture.completedFuture(this.localFallback(resourceId, "LLM disabled or API key is empty."));
        }

        long now = System.currentTimeMillis();
        long cooldownMs = StayWithMeConfig.LLM_COOLDOWN_SECONDS.get() * 1000L;
        long last = LAST_ANALYSIS_MS.getOrDefault(player.getUUID(), 0L);
        if (cooldownMs > 0 && now - last < cooldownMs) {
            return CompletableFuture.completedFuture(this.localFallback(resourceId, "LLM cooldown active."));
        }
        LAST_ANALYSIS_MS.put(player.getUUID(), now);

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
        user.addProperty("content", this.buildUserPrompt(resourceId, player, memory));
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
                    OreDistributionAnalysis analysis = JsonUtils.fromJsonObjectText(extractContent(response.body()), OreDistributionAnalysis.class);
                    analysis.normalize(resourceId, "llm");
                    return analysis;
                })
                .exceptionally(error -> this.localFallback(resourceId, "LLM ore analysis failed: " + error.getMessage()));
    }

    private OreDistributionAnalysis localFallback(String resourceId, String reason) {
        Optional<MiningTargetRegistry.MiningTarget> knownTarget = MiningTargetRegistry.find(resourceId);
        if (knownTarget.isPresent()) {
            MiningTargetRegistry.MiningTarget target = knownTarget.get();
            MiningTargetRegistry.ExplorationProfile profile = target.explorationProfile();
            OreDistributionAnalysis analysis = OreDistributionAnalysis.fallback(
                    resourceId,
                    "Search visible exposed sources first, then use a survival tunnel in the validated practical abundance band."
            );
            if (target.sourceBlocks().length > 0) {
                analysis.oreBlockId = BuiltInRegistries.BLOCK.getKey(target.sourceBlocks()[0]).toString();
            }
            analysis.dimension = profile.dimension();
            analysis.yLevelHint = "practical band Y="
                    + profile.preferredYMin()
                    + ".."
                    + profile.preferredYMax()
                    + "; "
                    + profile.distributionHint();
            analysis.requiredTool = target.requiredToolHint();
            analysis.confidence = "high";
            analysis.source = "local_vanilla_1_20_1_registry";
            return analysis;
        }
        return OreDistributionAnalysis.fallback(resourceId, reason + " Unknown or modded resource; learn from observed blocks, loot, tags, recipes, and pack-specific plugins.");
    }

    private String buildUserPrompt(String resourceId, ServerPlayer player, FriendMemory memory) {
        String validatedDistribution = MiningTargetRegistry.find(resourceId)
                .map(target -> {
                    MiningTargetRegistry.ExplorationProfile profile = target.explorationProfile();
                    return "dimension="
                            + profile.dimension()
                            + "; practical abundance band Y="
                            + profile.preferredYMin()
                            + ".."
                            + profile.preferredYMax()
                            + "; required tool="
                            + target.requiredToolHint()
                            + "; "
                            + profile.distributionHint();
                })
                .orElse("unknown or modded resource");
        return """
                Analyze how this companion should learn and obtain the resource.

                Resource id: %s
                Minecraft version: 1.20.1
                Current dimension: %s
                Player Y: %.1f
                Validated Minecraft 1.20.1 distribution: %s
                Existing portable memory JSON:
                %s

                Return strict JSON using the schema.
                """.formatted(
                resourceId,
                player.serverLevel().dimension().location(),
                player.getY(),
                validatedDistribution,
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
