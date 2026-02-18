package org.pulitko.aiprocessingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.exception.BaseBusinessException;
import org.pulitko.aiprocessingservice.service.DeserializationErrorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.serializer.*;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.backoff.FixedBackOff;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableKafka
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.groups-id.consumer}")
    private String groupId;

    @Value("${spring.kafka.topics.outgoing}")
    private String publisherTopic;

    @Value("${spring.kafka.producer.properties.enable.idempotence}")
    private Boolean idempotence;

    @Value("${spring.kafka.producer.properties.max.in.flight.requests.per.connection}")
    private Integer maxInFlight;

    @Value("${spring.kafka.producer.transaction-id-prefix}")
    private String transactionalIdPrefix;

    @Value("${spring.kafka.consumer.isolation-level}")
    private String isolationLevel;

    private final DeserializationErrorService errorService;

    @Bean
    public ConsumerFactory<String, IncomingMessage> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        JsonDeserializer<IncomingMessage> jsonDeserializer = new JsonDeserializer<>(IncomingMessage.class);
        jsonDeserializer.addTrustedPackages("org.pulitko.aiprocessingservice.model");
        jsonDeserializer.setRemoveTypeHeaders(false);
        ErrorHandlingDeserializer<IncomingMessage> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                errorHandlingDeserializer
        );
    }

    @Bean
    public DefaultAfterRollbackProcessor<Object, Object> afterRollbackProcessor(
            DeserializationErrorService errorService) {

        log.info("!!! INITIALIZING FULL-POWER AFTER_ROLLBACK_PROCESSOR !!!");

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            String rawPayload = null;

            if (ex instanceof org.springframework.kafka.listener.ListenerExecutionFailedException) {
                Throwable cause = ex.getCause();
                if (cause instanceof org.springframework.kafka.support.serializer.DeserializationException dex) {
                    if (dex.getData() != null) {
                        rawPayload = new String(dex.getData(), StandardCharsets.UTF_8);
                    }
                }
            }

            if (rawPayload == null) {
                for (org.apache.kafka.common.header.Header header : record.headers()) {
                    if (header.key().contains("DeserializationException")) {
                        byte[] data = header.value();
                        if (data == null) continue;

                        if (data.length > 2 && data[0] == (byte) 0xAC && data[1] == (byte) 0xED) {
                            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                                Object obj = ois.readObject();
                                if (obj instanceof org.springframework.kafka.support.serializer.DeserializationException dex) {
                                    rawPayload = new String(dex.getData(), StandardCharsets.UTF_8);
                                } else if (obj instanceof byte[] bytes) {
                                    rawPayload = new String(bytes, StandardCharsets.UTF_8);
                                }
                            } catch (Exception e) {
                                rawPayload = "Hex Dump: " + javax.xml.bind.DatatypeConverter.printHexBinary(data);
                            }
                        } else {
                            rawPayload = new String(data, StandardCharsets.UTF_8);
                        }
                    }
                }
            }

            if (rawPayload == null && record.value() != null) {
                rawPayload = record.value().toString();
            }

            String finalData = (rawPayload != null) ? rawPayload : "Could not extract data";

            log.error("Recovery success: Saving to DB: {}", finalData);
            errorService.saveError(finalData, ex.getMessage(), record.topic());
        };

        var backOff = new FixedBackOff(2000L, 3);

        var processor = new DefaultAfterRollbackProcessor<>(recoverer, backOff);

        processor.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class,
                BaseBusinessException.class,
                org.springframework.messaging.handler.invocation.MethodArgumentResolutionException.class,
                org.springframework.messaging.converter.MessageConversionException.class
        );

        processor.addRetryableExceptions(
                org.springframework.dao.DataAccessException.class,
                java.net.ConnectException.class,
                org.springframework.web.client.ResourceAccessException.class,
                java.util.concurrent.RejectedExecutionException.class
        );

        return processor;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, IncomingMessage> consumerFactory,
            AfterRollbackProcessor<Object, Object> afterRollbackProcessor,
            ObjectProvider<KafkaTransactionManager<String, Object>> transactionManagerProvider) {
        log.info("Creating KafkaListenerContainerFactory bean");
        ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAfterRollbackProcessor(afterRollbackProcessor);
        transactionManagerProvider.ifAvailable(factory.getContainerProperties()::setKafkaAwareTransactionManager);
        factory.setAutoStartup(true);
        return factory;
    }

    private Map<String, Object> baseProducerConfig() {
        Map<String, Object> conf = new HashMap<>();
        conf.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        conf.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        conf.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        conf.put(ProducerConfig.RETRIES_CONFIG, 10);
        conf.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        conf.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        conf.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, idempotence);
        conf.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlight);
        return conf;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> conf = baseProducerConfig();
        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(conf);
        if (transactionalIdPrefix != null && !transactionalIdPrefix.isBlank()) {
            factory.setTransactionIdPrefix(transactionalIdPrefix);
        }

        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> testProducerFactory() {
        Map<String, Object> conf = baseProducerConfig();
        return new DefaultKafkaProducerFactory<>(conf);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    @Bean
    NewTopic createTopic() {
        return TopicBuilder.name(publisherTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }


}
