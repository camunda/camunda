/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.exporter.utils.ExporterUtil.map;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import java.util.List;

/**
 * Handles the creation of a batch operation by inserting the corresponding {@link
 * BatchOperationEntity} with the batch operation details. This handler is responsible for
 * initializing the batch operation state and type, and updating the local cache for immediate
 * access.
 */
public class BatchOperationCreatedHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationCreationRecordValue> {

  private final String indexName;
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public BatchOperationCreatedHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    this.indexName = indexName;
    this.batchOperationCache = batchOperationCache;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CREATION;
  }

  @Override
  public Class<BatchOperationEntity> getEntityType() {
    return BatchOperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationCreationRecordValue> record) {
    return record.getIntent().equals(BatchOperationIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationCreationRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationCreationRecordValue> record, final BatchOperationEntity entity) {
    final BatchOperationCreationRecordValue value = record.getValue();
    entity
        .setId(String.valueOf(value.getBatchOperationKey()))
        .setType(OperationType.valueOf(value.getBatchOperationType().name()))
        .setState(BatchOperationState.CREATED);

    // update local cache so that the batch operation info is available immediately to operation
    // status handlers
    final var cachedEntity = new CachedBatchOperationEntity(entity.getId(), map(entity.getType()));
    batchOperationCache.put(cachedEntity.batchOperationKey(), cachedEntity);
  }

  @Override
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
