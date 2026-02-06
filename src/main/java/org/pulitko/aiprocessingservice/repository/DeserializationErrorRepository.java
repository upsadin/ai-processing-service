package org.pulitko.aiprocessingservice.repository;

import org.pulitko.aiprocessingservice.model.DeserializationErrorEntity;
import org.springframework.data.repository.CrudRepository;

public interface DeserializationErrorRepository extends CrudRepository<DeserializationErrorEntity, Long> {
}
