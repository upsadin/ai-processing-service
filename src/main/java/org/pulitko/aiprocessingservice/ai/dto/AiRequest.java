package org.pulitko.aiprocessingservice.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRequest(
        String model,
        List<AiMessage> messages,
        double temperature,
        @JsonProperty("response_format") ResponseFormat responseFormat) {

    public record ResponseFormat(
            String type,
            @JsonProperty("json_schema") JsonSchemaConfig jsonSchema
    ) {}

    public record JsonSchemaConfig(
            String name,
            boolean strict,
            @JsonProperty("schema") JsonNode schema
    ) {}
}
