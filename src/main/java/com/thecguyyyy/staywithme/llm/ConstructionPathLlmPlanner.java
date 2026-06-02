package com.thecguyyyy.staywithme.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thecguyyyy.staywithme.ai.navigation.ConstructionPathSnapshot;
import com.thecguyyyy.staywithme.ai.navigation.ConstructionRoutePlan;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.util.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConstructionPathLlmPlanner {
    private static final int SNAPSHOT_HORIZONTAL_RADIUS = 8;
    private static final int SNAPSHOT_VERTICAL_RADIUS = 6;
    private static final int MIN_REQUEST_INTERVAL_MS = 10_000;
    private static final String SYSTEM_PROMPT = """
            You plan short local construction routes for a Minecraft survival companion.

            The companion is stuck because ordinary navigation has no path. Return a short sequence of adjacent feet positions.
            The server will derive and validate any required digging and floor placement. It rejects unsafe actions.

            Rules:
            - Output exactly one JSON object and nothing else.
            - Coordinates are relative to the companion origin at 0,0,0.
            - Each step must move exactly one horizontal block and may change y by at most one.
            - Use only modeled loaded cells.
            - Prefer open movement, then small amounts of digging, then supported one-block floor repair.
            - Never route through fluid, blocked/unbreakable cells, or long falls.
            - Return at most 64 steps.

            JSON schema:
            {
              "source": "llm",
              "reason": "short factual reason",
              "steps": [{"x": 1, "y": 0, "z": 0}]
            }
            """;
    private static final ConcurrentMap<UUID, Long> LAST_REQUEST_MS = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ConstructionPathSnapshot capture(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos origin,
            net.minecraft.core.BlockPos target,
            int repairBlocks
    ) {
        return ConstructionPathSnapshot.capture(
                level,
                origin,
                target,
                SNAPSHOT_HORIZONTAL_RADIUS,
                SNAPSHOT_VERTICAL_RADIUS,
                repairBlocks
        );
    }

    public CompletableFuture<Optional<ConstructionRoutePlan>> planAsync(UUID companionId, ConstructionPathSnapshot snapshot) {
        if (!StayWithMeConfig.isLlmConfigured()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long now = System.currentTimeMillis();
        long last = LAST_REQUEST_MS.getOrDefault(companionId, 0L);
        if (now - last < MIN_REQUEST_INTERVAL_MS) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        LAST_REQUEST_MS.put(companionId, now);

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
        user.addProperty("content", "Plan a safe local construction route to the target. Snapshot JSON:\n"
                + JsonUtils.toJson(snapshot));
        messages.add(user);
        payload.add("messages", messages);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(StayWithMeConfig.LLM_BASE_URL.get()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(StayWithMeConfig.LLM_TIMEOUT_SECONDS.get()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload)));
        if (!StayWithMeConfig.LLM_API_KEY.get().isBlank()) {
            request.header("Authorization", "Bearer " + StayWithMeConfig.LLM_API_KEY.get());
        }

        return this.httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return Optional.<ConstructionRoutePlan>empty();
                    }
                    ConstructionRoutePlan plan = JsonUtils.fromJsonObjectText(
                            extractContent(response.body()),
                            ConstructionRoutePlan.class
                    );
                    plan.normalize();
                    return plan.steps.isEmpty() ? Optional.<ConstructionRoutePlan>empty() : Optional.of(plan);
                })
                .exceptionally(error -> Optional.<ConstructionRoutePlan>empty());
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
