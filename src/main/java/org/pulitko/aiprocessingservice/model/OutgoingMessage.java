package org.pulitko.aiprocessingservice.model;

public record OutgoingMessage(
        String ref,
        String sourceId,
        String aiResult
) {
}
