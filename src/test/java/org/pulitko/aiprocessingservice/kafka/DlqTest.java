package org.pulitko.aiprocessingservice.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.pulitko.aiprocessingservice.config.KafkaConfig;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(classes = {
        KafkaIncomingHandler.class,
        KafkaConfig.class,
        DlqPublisher.class,
        DlqListener.class,
        AiProcessingService.class
})
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@EmbeddedKafka(partitions = 1, topics = {
        "${spring.kafka.topics.dlq}",
        "${spring.kafka.topics.processing-dlq}",
        "${spring.kafka.topics.incoming}"})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DlqTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @MockBean private AiProcessingService aiProcessingService;

    // Топики из конфига
    @Value("${spring.kafka.topics.dlq}")
    private String autoDlq;

    @Value("${spring.kafka.topics.processing-dlq}")
    private String businessDlq;

    @Value("${spring.kafka.topics.incoming}")
    private String incoming;

    private Consumer<String, String> testConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-group-" + java.util.UUID.randomUUID(), "true", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        testConsumer = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new StringDeserializer()).createConsumer();
    }

    @AfterEach
    void closeConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    @Test
    void shouldGoToDeserializationDlq() {
        embeddedKafka.consumeFromAnEmbeddedTopic(testConsumer, autoDlq);

        kafkaTemplate.send(incoming, "not-a-json");

        ConsumerRecord<String, String> received = KafkaTestUtils.getSingleRecord(testConsumer, autoDlq);
        assertThat(received.value()).isEqualTo("\"not-a-json\"");
        assertThat(received.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
    }

    @Test
    void shouldGoToBusinessDlq() {
        embeddedKafka.consumeFromAnEmbeddedTopic(testConsumer, businessDlq);

        IncomingMessage msg = new IncomingMessage("txt", "ref-1", "data");
        doThrow(new RejectedExecutionException("Full")).
                when(aiProcessingService).process(any(), any());
        ProducerRecord<String, Object> record = new ProducerRecord<>(incoming, msg);
        record.headers().add("x-sourceId", "src-1".getBytes());
        kafkaTemplate.send(record);
        kafkaTemplate.flush();
        ConsumerRecord<String, String> received = KafkaTestUtils.getSingleRecord(testConsumer, businessDlq);
        String json = received.value();
        System.out.println("DEBUG JSON: " + json);
        assertThat(received.value()).contains("ref-1");
    }
}