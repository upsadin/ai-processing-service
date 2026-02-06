package org.pulitko.aiprocessingservice.service;

import org.junit.jupiter.api.Test;
import org.pulitko.aiprocessingservice.repository.PromptRepository;
import org.pulitko.aiprocessingservice.util.TestData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringJUnitConfig(classes = {
        PromptServiceDb.class,
        PromptServiceRetryTest.class
})
@EnableRetry
class PromptServiceRetryTest {

    @MockBean
    private PromptRepository promptRepository;

    @Autowired
    private PromptService promptService;

    @Test
    void shouldRetryOnDatabaseException() {
        when(promptRepository.findByRefAndActiveTrue(anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB Connection Timeout"))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB Connection Timeout"))
                .thenReturn(Optional.of(TestData.PROMPT_ENTITY));

        promptService.getByRef("candidate_java");

        verify(promptRepository, times(3)).findByRefAndActiveTrue("candidate_java");
    }
}