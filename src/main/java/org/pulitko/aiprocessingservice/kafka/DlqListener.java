package org.pulitko.aiprocessingservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;


@Slf4j
@Component
public class DlqListener {

    @KafkaListener(
            topics = "${spring.kafka.topics.processing-dlq}",
            groupId = "${spring.kafka.groups-id.dlq}-business"
    )
    public void handleBusinessError(@Payload IncomingMessage message,
                                    @Header(name = "x-sourceId", required = false) String sourceId,
                                    @Header(name = "x-reason", required = false) String reason) {
        if (message == null) {
            log.error("DLQ received NULL payload! Check serialization.");
            return;
        }
        log.warn("Manual fix needed for source={}, reason={}, ref={}",
                sourceId, reason, message.ref());
    }

    @KafkaListener(
            topics = "${spring.kafka.topics.dlq}",
            groupId = "${spring.kafka.groups-id.dlq}-system"
    )
    public void handleSystemError(ConsumerRecord<Object, Object> record) {
        log.error("Received something in System DLQ: {}", record.value());
    }

        // TODO:
        // - retry
        // - сохранение в БД
        // - алерты
}