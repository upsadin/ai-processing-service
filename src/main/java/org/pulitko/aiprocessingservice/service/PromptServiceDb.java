package org.pulitko.aiprocessingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.model.Prompt;
import org.pulitko.aiprocessingservice.model.PromptEntity;
import org.pulitko.aiprocessingservice.repository.PromptRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceDb implements PromptService {

    private final PromptRepository promptRepository;

    @Override
    @Cacheable(value = "prompts", key = "#ref")
    @Retryable(
            retryFor = { org.springframework.dao.DataAccessException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public Prompt getByRef(String ref) {
        log.info("Searching prompt for ref: {}", ref);
        PromptEntity entity = promptRepository.findByRefAndActiveTrue(ref)
                .orElseThrow(() -> new PromptNotFoundException("Prompt not found for ref " + ref));

        return mapToDomain(entity);
    }

    private Prompt mapToDomain(PromptEntity entity) {
        return new Prompt(
                entity.getRef(),
                entity.getPromptTemplate(),
                entity.getSchemaJson()
        );
    }
}