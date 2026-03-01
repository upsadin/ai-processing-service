package org.pulitko.aiprocessingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptCreateRequest(
        @NotBlank
        String ref,
        @NotBlank
        String template,
        @NotBlank
        String schemaJson
) {
}
