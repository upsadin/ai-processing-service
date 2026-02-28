package org.pulitko.aiprocessingservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.config.KafkaTopicsConfig;
import org.pulitko.aiprocessingservice.dto.OutgoingMessage;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutgoingPublisher {
    private final KafkaTopicsConfig topicsConfig;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(OutgoingMessage message) {
        try {
            kafkaTemplate.send(
                            topicsConfig.getOutgoing(),
                            message.sourceId(),
                            message)
                    .get(5, TimeUnit.SECONDS);
            log.info("Sent event id:{}", message.sourceId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaException("Thread interrupted while sending to Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            log.error("Technical error sending to Kafka", e);
            throw new KafkaException("Failed to publish message to Kafka", e);
        }
    }
}
