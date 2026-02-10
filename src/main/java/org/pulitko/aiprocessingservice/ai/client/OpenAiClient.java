package org.pulitko.aiprocessingservice.ai.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.ai.dto.AiMessage;
import org.pulitko.aiprocessingservice.ai.dto.AiRequest;
import org.pulitko.aiprocessingservice.ai.dto.AiResponse;
import org.pulitko.aiprocessingservice.exception.AiRetryableException;
import org.pulitko.aiprocessingservice.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@Primary
public class OpenAiClient implements AiClient {
    private final String apiKey;
    private final WebClient webClient;
    private final String model;

    public OpenAiClient(
            WebClient.Builder builder,
            @Value("${ai.api.base-url}") String baseUrl,
            @Value("${ai.api.key}") String apiKey,
            @Value("${ai.api.model-name}") String model) {

        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = model;
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void validateConfig() {
        log.info("Checking AI Client configuration...");

        if (apiKey == null || apiKey.isBlank() || apiKey.contains("placeholder")) {
            log.error("ERROR: OpenAI API Key is missing or invalid! Set the OPENAI_API_KEY environment variable.");
        } else {
            log.info("AI Client configured successfully. Key starts with: {}***", apiKey.substring(0, 5));
        }
    }

    @Override
    public String analyze(String systemInstruction, String userContent) {
        AiRequest request = new AiRequest(model,
                List.of(
                        new AiMessage("system", systemInstruction),
                        new AiMessage("user", userContent)
                ), 0.1);

        AiResponse response = webClient.post()
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.value() == 429 || status.is5xxServerError(), resp ->
                        resp.bodyToMono(String.class).map(body ->
                                new AiRetryableException("AI API temporary unavailable: " + body, null))
                )
                .onStatus(status -> status.isError(), resp ->
                        resp.bodyToMono(String.class).map(body ->
                                new RuntimeException("Fatal AI Error: " + body))
                )
                .bodyToMono(AiResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(throwable -> throwable instanceof AiRetryableException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()) // Пробрасываем ошибку дальше, если попытки кончились
                )
                .block();
        if (response != null && response.usage() != null) {
            AiResponse.Usage usage = response.usage();
            log.info("AI Usage: Model: {}, Prompt: {}, Completion: {}, Total: {}",
                    model, usage.promptTokens(), usage.completionTokens(), usage.totalTokens());
        }
        if (response == null || response.choices().isEmpty()) {
            throw new AiServiceException("AI return empty choices");
        }

        return response.choices().get(0).message().content();

    }
}
