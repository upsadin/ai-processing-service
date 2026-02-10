package org.pulitko.aiprocessingservice.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.pulitko.aiprocessingservice.config.KafkaConfig;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.pulitko.aiprocessingservice.service.DeserializationErrorService;
import org.pulitko.aiprocessingservice.util.TestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = {
        KafkaIncomingHandler.class,
        KafkaConfig.class,
        DlqPublisher.class,
        KafkaOutgoingPublisher.class
})
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@EmbeddedKafka(partitions = 1, topics = "${spring.kafka.topics.incoming}")
@ActiveProfiles("test")
class KafkaIncomingHandlerTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private AiProcessingService service;

    @MockBean
    private DeserializationErrorService deserializationErrorService;

    @Test
    void shouldForwardMessageToService() throws Exception {
        IncomingMessage msg = TestData.INCOMING_MESSAGE;
        ProducerRecord<String, Object> record =
                new ProducerRecord<>("ai.processing.raw", msg);
        record.headers().add(
                "x-sourceId",
                "1".getBytes(StandardCharsets.UTF_8)
        );
        kafkaTemplate.send(record);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(service)
                        .process(eq(msg))
        );
    }

}