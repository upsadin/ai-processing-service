package org.pulitko.aiprocessingservice.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.dto.Prompt;
import org.pulitko.aiprocessingservice.model.PromptEntity;
import org.pulitko.aiprocessingservice.repository.PromptRepository;
import org.pulitko.aiprocessingservice.service.PromptServiceDb;
import org.pulitko.aiprocessingservice.usecases.mapper.PromptMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.pulitko.aiprocessingservice.util.TestData.PROMPT_ENTITY;
import static org.pulitko.aiprocessingservice.util.TestData.REF_JAVACANDIDATE;

@ExtendWith(MockitoExtension.class)
class PromptServiceDbTest {
    @Mock
    private PromptRepository promptRepository;
    private final PromptMapper promptMapper = new PromptMapper();
    private PromptServiceDb promptService;

    @BeforeEach
    void setUp() {

        promptService = new PromptServiceDb(promptRepository, promptMapper, new ObjectMapper());
    }

    @Test
    void shouldReturnPromptWhenFound() {
        String ref = REF_JAVACANDIDATE;
        PromptEntity entity = PROMPT_ENTITY;

        when(promptRepository.findByRefAndActiveTrue(ref)).thenReturn(Optional.of(entity));

        Prompt result = promptService.getActivePrompt(ref);

        assertNotNull(result);
        assertEquals(ref, result.ref());
        assertEquals("Promt for java candidate", result.template());

        verify(promptRepository, times(1)).findByRefAndActiveTrue(ref);
    }

    @Test
    void shouldThrowExceptionWhenNotFound() {
        String ref = "unknown-ref";
        when(promptRepository.findByRefAndActiveTrue(ref)).thenReturn(Optional.empty());

        assertThrows(PromptNotFoundException.class, () -> promptService.getActivePrompt(ref));
    }
}