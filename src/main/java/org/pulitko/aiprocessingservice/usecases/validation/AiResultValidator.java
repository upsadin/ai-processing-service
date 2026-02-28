package org.pulitko.aiprocessingservice.usecases.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AiResultValidator {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public String validate(String rawResponse, String schemaJson, String ref) {
        try {
            JsonNode jsonNode = objectMapper.readTree(rawResponse);
            JsonSchema schema = schemaCache.computeIfAbsent(ref, k -> schemaFactory.getSchema(schemaJson));
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            if (!errors.isEmpty()) {
                log.error("Schema validation failed for ref {}: {}", ref, errors);
                String errorMsg = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "));
                throw new AiResultValidationException(ref, errorMsg);
            }

            double confidence = jsonNode.get("confidence").asDouble();
            if (confidence < 0 || confidence > 1) {
                throw new AiResultValidationException(ref, "Confidence out of range");
            }
        } catch (AiResultValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new AiResultValidationException(ref, "Internal validation error", e);
        }
        return rawResponse;
    }
}
