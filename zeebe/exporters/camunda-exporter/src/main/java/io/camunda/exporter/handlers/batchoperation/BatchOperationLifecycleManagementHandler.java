/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.intent.BatchOperationIntent.*;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.IndexLocator;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.webapps.schema.entities.operation.BatchOperationErrorEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the lifecycle management of batch operations by updating the {@link BatchOperationEntity}
 * based on lifecycle events such as cancellation, suspension, resumption, and completion. This
 * handler ensures that the state and end date of the batch operation are correctly updated in the
 * database.
 */
public class BatchOperationLifecycleManagementHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationLifecycleManagementRecordValue> {
  // Painless script that guards against state regression in multi-partition clusters.
  // - If the incoming state is terminal, it is always applied (terminal states take priority).
  // - If the incoming state is non-terminal (ACTIVE from RESUMED, or SUSPENDED), it is only
  //   applied when the current stored state is also non-terminal, preventing a lagging distributed
  //   event from overwriting a terminal state.
  // - endDate and errors are updated only when the state transition is applied, ensuring that
  //   late non-terminal events cannot clear or modify the end date or errors of a terminal
  //   operation.
  static final String CONDITIONAL_STATE_UPDATE_SCRIPT =
      """
      def terminalStates = ['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELED'];
      def isNewStateTerminal = terminalStates.contains(params.state);
      def isCurrentStateTerminal = terminalStates.contains(ctx._source.state);
      def shouldApplyState = isNewStateTerminal || !isCurrentStateTerminal;
      if (shouldApplyState) {
          ctx._source.state = params.state;
          ctx._source.endDate = params.endDate;
          if (params.containsKey('errors')) {
              ctx._source.errors = params.errors;
          }
      }
      """;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationLifecycleManagementHandler.class);
  private static final Set<Intent> EXPORTABLE_INTENTS =
      Set.of(CANCELED, SUSPENDED, RESUMED, COMPLETED, FAILED);
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
    switch (record.getIntent()) {
      case CANCELED ->
          // set the endDate because the BatchOperationUpdateTask does not need to run here
          entity
              .setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
              .setState(BatchOperationState.CANCELED);
      case SUSPENDED -> entity.setEndDate(null).setState(BatchOperationState.SUSPENDED);
      case RESUMED -> entity.setEndDate(null).setState(BatchOperationState.ACTIVE);
      case COMPLETED -> {
        final var value = record.getValue();
        // set the endDate to null so that the BatchOperationUpdateTask does run again
        entity.setEndDate(null);
        if (value.getErrors().isEmpty()) {
          entity.setState(BatchOperationState.COMPLETED);
        } else {
          entity.setErrors(mapErrors(value.getErrors()));
          entity.setState(BatchOperationState.PARTIALLY_COMPLETED);
        }
      }
      case FAILED -> {
        final var value = record.getValue();
        // set the endDate to null so that the BatchOperationUpdateTask does run again
        entity.setEndDate(null);
        entity.setErrors(mapErrors(value.getErrors()));
        entity.setState(BatchOperationState.FAILED);
      }
      default -> // should never happen because of handlesRecord()
          LOGGER.warn(
              "Trying to export an un-supported batch operation lifecycle management intent: {}",
              record.getIntent());
    }
  }

  @Override
  public void flush(
      final IndexLocator indexLocator,
      final BatchOperationEntity entity,
      final BatchRequest batchRequest)
      throws PersistenceException {
    // Use upsertWithScript to be resilient against cross-partition ordering.
    // CANCELED, SUSPENDED, and RESUMED events are distributed to all partitions, so each
    // partition's exporter writes to the same document. A conditional Painless script prevents
    // a lagging non-terminal state (e.g., ACTIVE from RESUMED) from overwriting a terminal state
    // (e.g., COMPLETED) that was already written by the lead partition's exporter.
    // If the document does not yet exist, the upsert creates it from the entity.
    final Map<String, Object> scriptParams = new HashMap<>();
    scriptParams.put(BatchOperationTemplate.STATE, entity.getState().name());
    scriptParams.put(BatchOperationTemplate.END_DATE, entity.getEndDate());
    if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
      scriptParams.put(BatchOperationTemplate.ERRORS, entity.getErrors());
    }
    batchRequest.upsertWithScript(
        indexLocator.getIndexLocation(entity, indexName),
        entity.getId(),
        entity,
        CONDITIONAL_STATE_UPDATE_SCRIPT,
        scriptParams);
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
