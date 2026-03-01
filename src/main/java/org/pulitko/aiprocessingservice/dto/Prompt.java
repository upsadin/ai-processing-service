package org.pulitko.aiprocessingservice.dto;

public record Prompt(
        String ref,
        String template,
        String schemaJson
) {
}
