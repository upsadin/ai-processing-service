package org.pulitko.aiprocessingservice.usecases.mapper;

import org.pulitko.aiprocessingservice.dto.Prompt;
import org.pulitko.aiprocessingservice.dto.PromptFullResponse;
import org.pulitko.aiprocessingservice.model.PromptEntity;
import org.springframework.stereotype.Component;

@Component
public class PromptMapper {
    public Prompt toAiDto(PromptEntity entity) {
        return new Prompt(
                entity.getRef(),
                entity.getPromptTemplate(),
                entity.getSchemaJson()
        );
    }

    public PromptFullResponse toAdminResponse(PromptEntity entity) {
        return new PromptFullResponse(
                entity.getId(),
                entity.getRef(),
                entity.getPromptTemplate(),
                entity.getSchemaJson(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}