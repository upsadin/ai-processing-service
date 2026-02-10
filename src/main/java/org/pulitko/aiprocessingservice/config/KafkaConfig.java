package org.pulitko.aiprocessingservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.pulitko.aiprocessingservice.dto.IncomingMessage;
import org.pulitko.aiprocessingservice.exception.AiResultValidationException;
import org.pulitko.aiprocessingservice.exception.AiRetryableException;
import org.pulitko.aiprocessingservice.service.DeserializationErrorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableKafka
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.groups-id.consumer}")
    private String groupId;

    @Value("${spring.kafka.topics.dlq}")
    private String dlqTopic;

    @Value("${spring.kafka.topics.outgoing}")
    private String publisherTopic;

    private final DeserializationErrorService errorService;

    @Bean
    public ConsumerFactory<String, IncomingMessage> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
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
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                           DeserializationErrorService errorService) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, -1));
        var backOff = new FixedBackOff(2000L, 3);
        var errorHandler = new DefaultErrorHandler(recoverer, backOff) {
            @Override
            public boolean handleOne(Exception thrownException, ConsumerRecord<?, ?> record,
                                     Consumer<?, ?> consumer, MessageListenerContainer container) {
                processError(thrownException, record);
                return super.handleOne(thrownException, record, consumer, container);
            }

            @Override
            public void handleRemaining(Exception thrownException, List<ConsumerRecord<?, ?>> records,
                                        Consumer<?, ?> consumer, MessageListenerContainer container) {
                if (records != null && !records.isEmpty()) {
                    processError(thrownException, records.get(0));
                }
                super.handleRemaining(thrownException, records, consumer, container);
            }

            @Override
            public void handleOtherException(Exception thrownException, Consumer<?, ?> consumer,
                                             MessageListenerContainer container, boolean batchListener) {
                log.error("!!! SYSTEM ERROR in handleOtherException: {}", thrownException.getMessage());
                super.handleOtherException(thrownException, consumer, container, batchListener);
            }

            private void processError(Exception ex, ConsumerRecord<?, ?> record) {
                if (record == null) return;
                DeserializationException derEx = findDeserializationException(ex);
                boolean isConvEx = isConversionException(ex);

                if (derEx == null && !isConvEx) {
                    log.info("CommonErrorHandler: Business or Runtime exception detected ({}). " +
                            "Skipping DB persistence as it's not a deserialization issue.", ex.getClass().getSimpleName());
                    return;
                }

                String rawPayload;
                String errorMessage;

                if (derEx != null) {
                    log.error("!!! DETECTED Deserialization Error in topic: {}", record.topic());
                    rawPayload = (derEx.getData() != null)
                            ? new String(derEx.getData(), StandardCharsets.UTF_8)
                            : "unknown_payload_bytes";
                    errorMessage = "Deserialization failed: " + ex.getMessage();
                } else {
                    log.error("!!! DETECTED Conversion Error in topic: {}", record.topic());
                    rawPayload = record.value() != null ? record.value().toString() : "null_payload";
                    errorMessage = "Mapping/Conversion error: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                }

                log.info("Saving corrupted message to DB: payload={}, error={}", rawPayload, errorMessage);
                try {
                    errorService.saveError(rawPayload, errorMessage, record.topic());
                } catch (Exception dbEx) {
                    log.error("!!! FATAL: Could not save error to DB", dbEx);
                }
            }

            private boolean isConversionException(Throwable ex) {
                Throwable cause = ex;
                while (cause != null) {
                    if (cause instanceof org.springframework.kafka.support.converter.ConversionException ||
                            cause instanceof org.springframework.messaging.converter.MessageConversionException) {
                        return true;
                    }
                    cause = cause.getCause();
                }
                return false;
            }

            private DeserializationException findDeserializationException(Throwable ex) {
                Throwable cause = ex;
                while (cause != null) {
                    if (cause instanceof DeserializationException) {
                        return (DeserializationException) cause;
                    }
                    if (cause instanceof org.springframework.kafka.support.converter.ConversionException) {
                        if (cause.getCause() instanceof DeserializationException) {
                            return (DeserializationException) cause.getCause();
                        }
                        return null;
                    }
                    cause = cause.getCause();
                }
                return null;
            }
        };
        errorHandler.addNotRetryableExceptions(
                AiResultValidationException.class,
                DeserializationException.class,
                MessageConversionException.class
        );

        errorHandler.addRetryableExceptions(
                AiRetryableException.class,
                ResourceAccessException.class,
                RejectedExecutionException.class
        );
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, IncomingMessage> consumerFactory,
            CommonErrorHandler errorHandler) {
        log.info("Creating KafkaListenerContainerFactory bean");
        ConcurrentKafkaListenerContainerFactory<String, IncomingMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setAutoStartup(true);
//        factory.setRecordMessageConverter(new JsonMessageConverter());
//        factory.setConcurrency(10);
        return factory;
    }


    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> conf = new HashMap<>();
        conf.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        conf.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        conf.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        conf.put(ProducerConfig.RETRIES_CONFIG, 10);
        conf.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        conf.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        conf.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(conf);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

/*    @Bean
    public RecordMessageConverter converter() {
        return new JsonMessageConverter();
    }*/

    @Bean
    NewTopic createTopic() {
        return TopicBuilder.name(publisherTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
