package org.pulitko.aiprocessingservice.repository;

import org.pulitko.aiprocessingservice.model.DeserializationErrorEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DeserializationErrorRepository extends CrudRepository<DeserializationErrorEntity, Long> {
    @Override
    List<DeserializationErrorEntity> findAll();
}
