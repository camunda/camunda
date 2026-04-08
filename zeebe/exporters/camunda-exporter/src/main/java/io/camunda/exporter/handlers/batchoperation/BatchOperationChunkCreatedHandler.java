/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateTask;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.List;
import java.util.Map;

/**
 * This handler resets the {@code endDate} of a {@link BatchOperationEntity} to {@code null} on each
 * CHUNK_CREATED event. This ensures the {@link BatchOperationUpdateTask} will re-process this batch
 * operation to update completion/failure counts. <br>
 * <br>
 * This is necessary because sometimes the {@code COMPLETED} event from one partition is exported
 * before all {@code CHUNK_CREATED} events are exported from another partition. Resetting {@code
 * endDate} forces the update task to re-evaluate the counts. <br>
 * <br>
 * Note: {@code operationsTotalCount} is no longer incremented here. It is now set atomically by the
 * {@link BatchOperationInitializedHandler} when the INITIALIZED event is exported, avoiding
 * double-counting when CREATED and CHUNK_CREATED events land in the same exporter flush cycle.
 */
public class BatchOperationChunkCreatedHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationChunkRecordValue> {

  private final String indexName;

  public BatchOperationChunkCreatedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CHUNK;
  }

  @Override
  public Class<BatchOperationEntity> getEntityType() {
    return BatchOperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationChunkRecordValue> record) {
    return record.getIntent().equals(BatchOperationChunkIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationChunkRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationChunkRecordValue> record, final BatchOperationEntity entity) {
    // No fields to update on the entity. The flush script resets endDate to null so that
    // BatchOperationUpdateTask re-processes this batch operation.
  }

  @Override
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // Use upsertWithScript to be resilient against cross-partition ordering: if the batch operation
    // document has not been created yet (e.g., a slow partition's CREATED event export), the upsert
    // creates a minimal document from the entity. When the document already exists, the script
    // resets endDate to null so that the BatchOperationUpdateTask will re-process this batch
    // operation to update all counts.
    batchRequest.upsertWithScript(
        indexName, entity.getId(), entity, "ctx._source.endDate = null;", Map.of());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
