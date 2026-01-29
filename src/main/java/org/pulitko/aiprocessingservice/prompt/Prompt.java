package org.pulitko.aiprocessingservice.prompt;

public record Prompt(
        String ref,
        String template,
        String schemaJson
) {
}
