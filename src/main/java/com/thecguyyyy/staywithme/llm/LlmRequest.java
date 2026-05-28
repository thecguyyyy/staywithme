package com.thecguyyyy.staywithme.llm;

public record LlmRequest(
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        String systemPrompt,
        String userPrompt
) {
}
