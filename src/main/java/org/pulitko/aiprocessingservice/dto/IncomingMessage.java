package org.pulitko.aiprocessingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.NonNull;

public record IncomingMessage(
        @NotBlank(message = "type must not be blank") String type,
        @NotBlank(message = "ref must not be blank") String ref,
        @NotBlank(message = "text must not be blank")
        @Size(max = 100_000, message = "text is too large")String payload
) {
}
