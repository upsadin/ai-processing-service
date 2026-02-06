package org.pulitko.aiprocessingservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Table("deserialization_errors")
public record DeserializationErrorEntity(
        @Id Long id,
        String payload,
        String errorMessage,
        String topic,
        LocalDateTime createdAt
) {
    // Статический метод для удобного создания
    public static DeserializationErrorEntity of(String payload, String errorMessage, String topic) {
        return new DeserializationErrorEntity(null, payload, errorMessage, topic, LocalDateTime.now());
    }
}