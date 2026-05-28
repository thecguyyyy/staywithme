package com.thecguyyyy.staywithme.llm;

import java.util.concurrent.CompletableFuture;

public interface LlmClient {
    CompletableFuture<LlmResponse> plan(LlmRequest request);
}
