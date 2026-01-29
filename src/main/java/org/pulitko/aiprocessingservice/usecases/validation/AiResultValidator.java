package org.pulitko.aiprocessingservice.usecases.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class AiResultValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public void validate(String aiResultJson, String schemaJson, String schemaKey) {
        try {
            JsonNode jsonNode = objectMapper.readTree(aiResultJson);
            JsonSchema schema = schemaCache.computeIfAbsent(schemaKey, k -> schemaFactory.getSchema(schemaJson));
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                String errorMsg = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new AiResultValidationException(schemaKey, errorMsg);
            }

            double confidence = jsonNode.get("confidence").asDouble();
            if (confidence < 0 || confidence > 1) {
                throw new AiResultValidationException(schemaKey, "Confidence out of range");
            }
        } catch (AiResultValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiResultValidationException(schemaKey, "Internal validation error", e);
        }
    }
}
