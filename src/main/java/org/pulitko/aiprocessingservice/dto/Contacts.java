package org.pulitko.aiprocessingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Contacts(
        String email,
        String phone,
        String linkedin,
        String telegram
) {

}
