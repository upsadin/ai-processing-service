package org.pulitko.aiprocessingservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.kafka.topics")
@Data
public class KafkaTopicsConfig {
    @NotBlank
    private String outgoing;
    @NotBlank
    private String processingDlq;
}
