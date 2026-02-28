package org.pulitko.aiprocessingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder(setterPrefix = "with")
public record OutgoingMessage(
        String ref,
        String sourceId,
        JsonNode aiResult
) {
}
