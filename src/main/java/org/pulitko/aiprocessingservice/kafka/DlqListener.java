package org.pulitko.aiprocessingservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@KafkaListener(
        topics = "${spring.kafka.topics.dlq}",
        groupId = "${spring.kafka.groups-id.dlq}"
)
@Slf4j
@Component
public class DlqListener {

    @KafkaHandler
    public void handle(
            @Payload String payload,
            @Headers Map<String, Object> headers
    ) {
        log.error(
                "DLQ message received. headers={}, payload={}",
                headers,
                payload
        );

        // TODO:
        // - retry
        // - сохранение в БД
        // - алерты
    }
}