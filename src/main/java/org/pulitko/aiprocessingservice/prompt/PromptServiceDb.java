package org.pulitko.aiprocessingservice.prompt;

import lombok.RequiredArgsConstructor;
import org.pulitko.aiprocessingservice.exception.PromptNotFoundException;
import org.pulitko.aiprocessingservice.service.PromptService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptServiceDb implements PromptService {

    private final PromptRepository promptRepository;

    @Override
    public Prompt getByRef(String ref) {
        PromptEntity entity = promptRepository.findByRefAndActiveTrue(ref)
                .orElseThrow(() -> new PromptNotFoundException("Prompt not found for ref=" + ref));
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