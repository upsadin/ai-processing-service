package org.pulitko.aiprocessingservice.prompt;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface PromptRepository extends CrudRepository<PromptEntity, Long> {

    Optional<PromptEntity> findByRefAndActiveTrue(String ref);
}
