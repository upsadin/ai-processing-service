package org.pulitko.aiprocessingservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.pulitko.aiprocessingservice.model.ProcessedResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutgoingPublisher {
    @Value("${spring.kafka.topics.outgoing}")
    private String TOPIC;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(ProcessedResult message, String sourceId) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(TOPIC, sourceId, message);
        record.headers().add("x-sourceId", sourceId.getBytes());
        kafkaTemplate.send(record);
        log.info("Sent event id:{}", sourceId);
    }
}
