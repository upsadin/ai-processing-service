package org.pulitko.aiprocessingservice.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRequest(
        String model,
        List<AiMessage> messages,
        double temperature) {
}
