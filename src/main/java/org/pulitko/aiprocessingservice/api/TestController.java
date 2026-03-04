package org.pulitko.aiprocessingservice.api;

import lombok.RequiredArgsConstructor;
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

    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/send")
    public String send(@RequestBody String message) {
        kafkaTemplate.send(topic, message);
        return "Sent to Kafka!";
    }
}