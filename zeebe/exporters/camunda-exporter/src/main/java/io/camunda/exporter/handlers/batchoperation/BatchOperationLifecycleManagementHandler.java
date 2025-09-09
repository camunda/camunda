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
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.webapps.schema.entities.operation.BatchOperationErrorEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the lifecycle management of batch operations by updating the {@link BatchOperationEntity}
 * based on lifecycle events such as cancellation, suspension, resumption, and completion. This
 * handler ensures that the state and end date of the batch operation are correctly updated in the
 * database.
 */
public class BatchOperationLifecycleManagementHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationLifecycleManagementRecordValue> {

  private static final Set<Intent> EXPORTABLE_INTENTS =
      Set.of(
          BatchOperationIntent.CANCELED,
          BatchOperationIntent.SUSPENDED,
          BatchOperationIntent.RESUMED,
          BatchOperationIntent.COMPLETED,
          BatchOperationIntent.FAILED);
  private final String indexName;

  public BatchOperationLifecycleManagementHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT;
  }

  @Override
  public Class<BatchOperationEntity> getEntityType() {
    return BatchOperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationLifecycleManagementRecordValue> record) {
    return EXPORTABLE_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(
      final Record<BatchOperationLifecycleManagementRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationLifecycleManagementRecordValue> record,
      final BatchOperationEntity entity) {
    if (record.getIntent().equals(BatchOperationIntent.CANCELED)) {
      // set the endDate because the BatchOperationUpdateTask does not need to run here
      entity
          .setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
          .setState(BatchOperationState.CANCELED);
    } else if (record.getIntent().equals(BatchOperationIntent.SUSPENDED)) {
      entity.setEndDate(null).setState(BatchOperationState.SUSPENDED);
    } else if (record.getIntent().equals(BatchOperationIntent.RESUMED)) {
      entity.setEndDate(null).setState(BatchOperationState.ACTIVE);
    } else if (record.getIntent().equals(BatchOperationIntent.COMPLETED)) {
      final var value = record.getValue();
      // set the endDate to null so that the BatchOperationUpdateTask does run again
      entity.setEndDate(null);
      if (value.getErrors().isEmpty()) {
        entity.setState(BatchOperationState.COMPLETED);
      } else {
        entity.setErrors(mapErrors(value.getErrors()));
        entity.setState(BatchOperationState.PARTIALLY_COMPLETED);
      }
    } else if (record.getIntent().equals(BatchOperationIntent.FAILED)) {
      final var value = record.getValue();
      // set the endDate to null so that the BatchOperationUpdateTask does run again
      entity.setEndDate(null);
      entity.setErrors(mapErrors(value.getErrors()));
      entity.setState(BatchOperationState.FAILED);
    }
  }

  @Override
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(BatchOperationTemplate.STATE, entity.getState());
    updateFields.put(BatchOperationTemplate.END_DATE, entity.getEndDate());
    if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
      updateFields.put(BatchOperationTemplate.ERRORS, entity.getErrors());
    }
    batchRequest.update(indexName, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private List<BatchOperationErrorEntity> mapErrors(final List<BatchOperationErrorValue> errors) {
    return errors.stream()
        .map(
            e ->
                new BatchOperationErrorEntity()
                    .setPartitionId(e.getPartitionId())
                    .setType(e.getType().name())
                    .setMessage(e.getMessage()))
        .toList();
  }
}
