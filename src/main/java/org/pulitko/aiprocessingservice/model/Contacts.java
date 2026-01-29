package org.pulitko.aiprocessingservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Contacts(
        String email,
        String phone,
        String linkedin,
        String telegram
) {

}
