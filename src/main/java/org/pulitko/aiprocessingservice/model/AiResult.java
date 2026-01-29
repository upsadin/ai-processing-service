package org.pulitko.aiprocessingservice.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiResult(
        boolean matches,
        double confidence,
        String reason,
        @JsonProperty("full_name") String fullName,
        Contacts contacts
) {}
