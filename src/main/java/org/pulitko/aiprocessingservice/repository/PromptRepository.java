package org.pulitko.aiprocessingservice.repository;

import org.pulitko.aiprocessingservice.model.PromptEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface PromptRepository extends ListCrudRepository<PromptEntity, Long> {

    Optional<PromptEntity> findByRefAndActiveTrue(String ref);
    Optional<PromptEntity> findByRef(String ref);

    @Modifying
    @Query("DELETE FROM ai_prompt WHERE ref = :ref")
    void deleteByRef(String ref);

    boolean existsByRef(String ref);
}
