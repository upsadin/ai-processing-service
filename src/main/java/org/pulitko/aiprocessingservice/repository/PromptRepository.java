package org.pulitko.aiprocessingservice.repository;

import org.pulitko.aiprocessingservice.model.PromptEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PromptRepository extends CrudRepository<PromptEntity, Long> {

    Optional<PromptEntity> findByRefAndActiveTrue(String ref);
}
