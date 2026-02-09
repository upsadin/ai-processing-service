package org.pulitko.aiprocessingservice.usecases.validation;

import org.junit.jupiter.api.Test;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedResultValidatorTest {
    private final AiResultValidator validator = new AiResultValidator();

    private final String schemaJson = """
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "name": { "type": "string" },
            "confidence": { "type": "number" }
          },
          "required": ["name", "confidence"]
        }
        """;

    @Test
    void shouldValidateSuccessfully() {
        String validJson = "{\"name\": \"Ivan\", \"confidence\": 0.85}";

        assertDoesNotThrow(() ->
                validator.cleanAndValidate(validJson, schemaJson, "test-key")
        );
    }

    @Test
    void shouldCleanAndValidateSuccessfully() {
        String validJson = "Вот ваш json {\"name\": \"Ivan\", \"confidence\": 0.85} ";

        assertDoesNotThrow(() ->
                validator.cleanAndValidate(validJson, schemaJson, "test-key")
        );
        assertEquals((validator.cleanAndValidate(validJson, schemaJson, "test-key")),
                "{\"name\": \"Ivan\", \"confidence\": 0.85}");
    }

    @Test
    void shouldThrowExceptionWhenSchemaValidationFails() {
        String invalidJson = "{\"confidence\": 0.9}";

        AiResultValidationException exception = assertThrows(
                AiResultValidationException.class,
                () -> validator.cleanAndValidate(invalidJson, schemaJson, "test-key")
        );

        String message = exception.getMessage();
        assertTrue(message.contains("$.name"));
    }

    @Test
    void shouldThrowExceptionWhenConfidenceIsOutOfRange() {
        String highConfidenceJson = "{\"name\": \"Ivan\", \"confidence\": 1.5}";

        AiResultValidationException exception = assertThrows(
                AiResultValidationException.class,
                () -> validator.cleanAndValidate(highConfidenceJson, schemaJson, "test-key")
        );

        assertTrue(exception.getMessage().contains("Confidence out of range"));
    }


    @Test
    void shouldThrowExceptionOnMalformedJson() {
        String malformedJson = "{ \"name\": \"Ivan\", ";

        AiResultValidationException exception = assertThrows(
                AiResultValidationException.class,
                () -> validator.cleanAndValidate(malformedJson, schemaJson, "test-key")
        );

        assertTrue(exception.getMessage().contains("Internal validation error"));
    }
}