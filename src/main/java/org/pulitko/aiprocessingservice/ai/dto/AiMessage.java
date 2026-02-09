package org.pulitko.aiprocessingservice.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiMessage (
        String role,
        String content
) {
}
