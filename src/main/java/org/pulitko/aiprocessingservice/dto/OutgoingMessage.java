package org.pulitko.aiprocessingservice.dto;

public record OutgoingMessage(
        String ref,
        String sourceId,
        String aiResult
) {
}
