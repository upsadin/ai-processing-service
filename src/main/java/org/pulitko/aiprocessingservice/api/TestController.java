package org.pulitko.aiprocessingservice.api;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    @Value("${spring.kafka.topics.incoming}")
    private String topic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/send")
    public String send(@RequestBody String message) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, message);
        record.headers().add("x-sourceId", "x1".getBytes());
        if (kafkaTemplate.isTransactional()) {
            kafkaTemplate.executeInTransaction(t -> t.send(record));
        } else {
            kafkaTemplate.send(record);
        }
        return "Sent to Kafka!";
    }
}