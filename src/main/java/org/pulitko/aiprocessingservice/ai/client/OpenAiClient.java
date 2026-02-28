package org.pulitko.aiprocessingservice.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.ai.dto.AiMessage;
import org.pulitko.aiprocessingservice.ai.dto.AiRequest;
import org.pulitko.aiprocessingservice.ai.dto.AiResponse;
import org.pulitko.aiprocessingservice.exception.AiConfigurationException;
import org.pulitko.aiprocessingservice.exception.AiRetryableException;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MeterRegistry meterRegistry;

    @Value("${ai.api.temperature:0.1}")
    private double temperature;

    public OpenAiClient(
            WebClient.Builder builder,
            @Value("${ai.api.base-url}") String baseUrl,
            @Value("${ai.api.key}") String apiKey,
            @Value("${ai.api.model-name}") String model,
            MeterRegistry meterRegistry) {

        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.model = model;
        this.apiKey = apiKey;
        this.meterRegistry = meterRegistry;
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
    public String analyze(String systemInstruction, String userContent, String schemaJson, String ref) {
        String isSuccess = "success";
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            JsonNode schemaNode = parseSchemaNode(schemaJson, ref);
            String safeName = ref.replaceAll("[^a-zA-Z0-9_-]", "_");
            if (safeName.length() > 64) safeName = safeName.substring(0, 64);
            AiRequest.ResponseFormat responseFormat = new AiRequest.ResponseFormat(
                    "json_schema",
                    new AiRequest.JsonSchemaConfig(safeName, true, schemaNode)
            );

            AiRequest request = new AiRequest(
                    model,
                    List.of(
                            new AiMessage("system", systemInstruction),
                            new AiMessage("user", userContent)
                    ),
                    temperature,
                    responseFormat);

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
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure())
                    )
                    .block();
            sample.stop(meterRegistry.timer("ai.request.duration", "ref", ref));

            if (response != null && response.usage() != null) {
                meterRegistry.counter("ai.tokens.used",
                        "ref", ref, "type", "prompt").increment(response.usage().promptTokens());
                meterRegistry.counter("ai.tokens.used",
                        "ref", ref, "type", "completion").increment(response.usage().completionTokens());
                meterRegistry.counter("ai.requests.total", "ref", ref, "status", "success").increment();
            }
            if (response == null || response.choices().isEmpty()) {
                throw new RuntimeException("OpenAI returned empty response for ref: " + ref);
            }

            return response.choices().get(0).message().content();
        } catch (AiConfigurationException e) {
            isSuccess = "error";
            throw e;
        } catch (Exception e) {
            isSuccess = "error";
            log.error("Technical error during AI communication for ref: {}", ref, e);
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("ai.request.duration", "ref", ref, "status", isSuccess));
            meterRegistry.counter("ai.requests.total", "ref", ref, "status", isSuccess).increment();
        }
    }

    private JsonNode parseSchemaNode(String schemaJson, String ref) {
        try {
            return objectMapper.readTree(schemaJson);
        } catch (JsonProcessingException e) {
            throw new AiConfigurationException(ref, "Invalid JSON Schema configuration", e);
        }
    }
}
