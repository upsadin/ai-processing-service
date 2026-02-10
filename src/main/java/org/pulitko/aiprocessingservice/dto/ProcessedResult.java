package org.pulitko.aiprocessingservice.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessedResult(
        boolean matches,
        Double confidence,
        String reason,
        @JsonProperty("full_name") String fullName,
        Contacts contacts
) {
    public ProcessedResult {
        if (confidence == null) confidence = 0.0;
        if (reason == null) reason = "No reason provided";
    }
}
