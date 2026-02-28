package org.pulitko.aiprocessingservice.kafka;

import lombok.RequiredArgsConstructor;
import org.pulitko.aiprocessingservice.config.KafkaTopicsConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqPublisher {

    private final KafkaTopicsConfig topicsConfig;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(IncomingMessage message, String sourceId, String reason) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                topicsConfig.getProcessingDlq(), message
        );

        record.headers().add(new RecordHeader("x-sourceId", sourceId.getBytes()));
        record.headers().add(new RecordHeader("x-reason", reason.getBytes()));

        kafkaTemplate.send(record);
        log.info("Sent event id:{}", sourceId);
    }


}