package org.pulitko.aiprocessingservice.dto;

import java.time.Instant;

public record PromptFullResponse(
        Long id,
        String ref,
        String template,
        String schemaJson,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
