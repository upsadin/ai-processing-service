package org.pulitko.aiprocessingservice.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pulitko.aiprocessingservice.ai.AiClient;
import org.pulitko.aiprocessingservice.config.AppConfig;
import org.pulitko.aiprocessingservice.config.KafkaConfig;
import org.pulitko.aiprocessingservice.model.IncomingMessage;
import org.pulitko.aiprocessingservice.model.Prompt;
import org.pulitko.aiprocessingservice.service.DeserializationErrorService;
import org.pulitko.aiprocessingservice.service.AiProcessingService;
import org.pulitko.aiprocessingservice.service.PromptService;
import org.pulitko.aiprocessingservice.usecases.validation.AiResultValidator;
import org.pulitko.aiprocessingservice.usecases.validation.IncomingMessageValidator;
import org.pulitko.aiprocessingservice.util.TestData;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.pulitko.aiprocessingservice.util.TestData.SOURCE_ID_JAVACANDIDATE;

@Slf4j
@SpringBootTest(classes = {
        KafkaIncomingHandler.class,
        KafkaConfig.class,
        DlqPublisher.class,
        KafkaOutgoingPublisher.class,
        AiProcessingService.class,
        AppConfig.class
})
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@EmbeddedKafka(partitions = 1, topics = {"${spring.kafka.topics.outgoing}", "${spring.kafka.topics.incoming}"})
@ActiveProfiles("test")
class KafkaOutgoingPublisherTest {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private AiProcessingService service;

    @MockBean
    private PromptService promptService;

    @MockBean
    private IncomingMessageValidator incomingMessageValidator;

    @MockBean
    private AiResultValidator aiResultValidator;

    @MockBean
    private AiClient aiClient;

    @MockBean
    private DeserializationErrorService deserializationErrorService;

    @Value("${spring.kafka.topics.outgoing}")
    private String outTopic;

    @Value("${spring.kafka.topics.incoming}")
    private String inTopic;

    private Consumer<String, String> testConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group-it", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer());

        testConsumer = cf.createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(testConsumer, outTopic);
    }

    @Test
    void testKafkaFlow(){

        IncomingMessage msg = TestData.INCOMING_MESSAGE;
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(inTopic, msg);
        record.headers().add(
                "x-sourceId",
                SOURCE_ID_JAVACANDIDATE.getBytes(StandardCharsets.UTF_8)
        );
        Prompt prompt = TestData.PROMPT;
        String aiResult = TestData.SUCCESS_AI_RESULT;

        doNothing().when(incomingMessageValidator).validate(msg);
        when(promptService.getByRef(any())).thenReturn(prompt);
        when(aiClient.analyze(any(), any())).thenReturn(aiResult);
        when(aiResultValidator.cleanAndValidate(any(),any(),any())).thenReturn(aiResult);

        kafkaTemplate.send(record);

        ConsumerRecord<String, String> received = KafkaTestUtils.getSingleRecord(
                testConsumer, outTopic, Duration.ofSeconds(15));

        assertNotNull(received);
        assertTrue(received.value().startsWith("{"));
    }

}