package org.pulitko.aiprocessingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pulitko.aiprocessingservice.dto.PromptCreateRequest;
import org.pulitko.aiprocessingservice.dto.PromptFullResponse;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.dto.Prompt;
import org.pulitko.aiprocessingservice.model.PromptEntity;
import org.pulitko.aiprocessingservice.repository.PromptRepository;
import org.pulitko.aiprocessingservice.usecases.mapper.PromptMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "jdbcTransactionManager")
public class PromptServiceDb implements PromptService {

    private final PromptRepository repository;
    private final PromptMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Cacheable(value = "prompts", key = "#p0")
    @Retryable(
            retryFor = { org.springframework.dao.DataAccessException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public Prompt getActivePrompt(String ref) {
        log.info("Searching prompt for ref: {}", ref);
        return repository.findByRefAndActiveTrue(ref)
                .map(mapper::toAiDto)
                .orElseThrow(() -> new PromptNotFoundException("Prompt not found for ref " + ref));
    }

    public PromptFullResponse getPrompt(String ref) {
        return repository.findByRef(ref)
                .map(mapper::toAdminResponse)
                .orElseThrow(() -> new IllegalArgumentException("Prompt not found"));
    }

    public List<PromptFullResponse> getAll() {
        return repository.findAll().stream()
                .map(mapper::toAdminResponse)
                .toList();
    }

    @Transactional(transactionManager = "jdbcTransactionManager")
    @CacheEvict(value = "prompts", key = "#request.ref()")
    public PromptFullResponse saveOrUpdate(PromptCreateRequest request) {
        validateJson(request.schemaJson());
        PromptEntity entity = repository.findByRef(request.ref())
                .orElse(new PromptEntity());
        entity.setRef(request.ref());
        entity.setPromptTemplate(request.template());
        entity.setSchemaJson(request.schemaJson());
        repository.save(entity);
        return mapper.toAdminResponse(entity);
    }

    @Transactional(transactionManager = "jdbcTransactionManager")
    @CacheEvict(value = "prompts", key = "#ref")
    public void delete(String ref) {
        log.info("Попытка удаления промта с ref: {}", ref);
        if (!repository.existsByRef(ref)) {
            log.warn("Промт {} не найден в БД", ref);
            throw new IllegalArgumentException("...");
        }
        repository.deleteByRef(ref);
        log.info("Метод deleteByRef вызван для {}", ref);
    }

    @Transactional(transactionManager = "jdbcTransactionManager")
    @CacheEvict(value = "prompts", key = "#ref")
    public void changeStatus(String ref, boolean active) {
        PromptEntity entity = repository.findByRef(ref)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Промт с ключом '" + ref + "' не найден."));
        entity.setActive(active);
        repository.save(entity);
    }

    private void validateJson(String json) {
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("JSON Schema is invalid: {}", json, e);
            throw new IllegalArgumentException("Ошибка в формате JSON-схемы: " + e.getMessage());
        }
    }

}