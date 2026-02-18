package org.pulitko.aiprocessingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.model.DeserializationErrorEntity;
import org.pulitko.aiprocessingservice.repository.DeserializationErrorRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeserializationErrorService {

    private final DeserializationErrorRepository repository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            transactionManager = "jdbcTransactionManager")
    public void saveError(String payload, String exceptionMessage, String topic) {
        try {
            var error = DeserializationErrorEntity.of(payload, exceptionMessage, topic);
            repository.save(error);
            log.info("Successfully saved deserialization error to DB");
        } catch (Exception e) {
            log.error("CRITICAL: Could not save error to DB. Payload: {}", payload, e);
        }
    }
}