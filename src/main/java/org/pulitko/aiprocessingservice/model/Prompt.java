package org.pulitko.aiprocessingservice.model;

public record Prompt(
        String ref,
        String template,
        String schemaJson
) {
}
