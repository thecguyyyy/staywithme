package com.thecguyyyy.staywithme.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thecguyyyy.staywithme.util.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class OpenAICompatibleClient implements LlmClient {
    private final HttpClient httpClient;

    public OpenAICompatibleClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletableFuture<LlmResponse> plan(LlmRequest request) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", request.model());
        payload.addProperty("temperature", 0.2D);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", request.systemPrompt());
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", request.userPrompt());
        messages.add(user);

        payload.add("messages", messages);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(request.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload)));

        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + request.apiKey());
        }

        return this.httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + response.body());
                    }
                    String content = extractContent(response.body());
                    PlannedAction action = JsonUtils.fromJsonObjectText(content, PlannedAction.class);
                    return new LlmResponse(content, action);
                });
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
