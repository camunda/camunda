/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.operation;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractOperationHandler<R extends RecordValue>
    implements ExportHandler<OperationEntity, R> {

  protected final String indexName;

  public AbstractOperationHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public Class<OperationEntity> getEntityType() {
    return OperationEntity.class;
  }

  @Override
  public List<String> generateIds(final Record<R> record) {
    final long operationReference = record.getOperationReference();
    if (operationReference > 0) {
      return List.of(String.valueOf(operationReference));
    }
    return List.of();
  }

  @Override
  public OperationEntity createNewEntity(final String id) {
    return new OperationEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<R> record, final OperationEntity entity) {
    entity
        .setState(OperationState.COMPLETED)
        .setLockOwner(null)
        .setLockExpirationTime(null)
        .setCompletedDate(OffsetDateTime.now());
  }

  @Override
  public void flush(final OperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(OperationTemplate.STATE, entity.getState());
    updateFields.put(OperationTemplate.COMPLETED_DATE, entity.getCompletedDate());
    updateFields.put(OperationTemplate.LOCK_OWNER, entity.getLockOwner());
    updateFields.put(OperationTemplate.LOCK_EXPIRATION_TIME, entity.getLockExpirationTime());

    batchRequest.update(indexName, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
