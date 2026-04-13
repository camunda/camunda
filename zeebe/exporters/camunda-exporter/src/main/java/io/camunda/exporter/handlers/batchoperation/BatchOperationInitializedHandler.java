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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports INITIALIZED events for batch operations. Each partition independently produces an
 * INITIALIZED event when its local initialization phase completes, carrying the partition-local
 * total item count in {@code operationsTotalCount}.
 *
 * <p>The handler uses an {@code upsertWithScript} strategy: the Painless script atomically
 * increments {@code operationsTotalCount} and conditionally transitions the state to ACTIVE. This
 * ensures correct counts even across multiple partitions.
 *
 * <p><b>Important:</b> {@code operationsTotalCount} is deliberately <em>not</em> set on the shared
 * {@link BatchOperationEntity} in {@link #updateEntity}. The {@link
 * io.camunda.exporter.store.ExporterBatchWriter} caches a single entity instance per {@code
 * (entityId, entityType)} and all handlers that return {@code BatchOperationEntity.class} from
 * {@link #getEntityType()} share that instance. If we set the count on the entity, the upsert body
 * of other handlers (e.g., {@link BatchOperationCreatedHandler}) would carry the contaminated count
 * and create the document with it — then this handler's script would increment again, causing
 * double-counting. Instead, the count is stored in a handler-local map and only passed through the
 * Painless script params.
 */
public class BatchOperationInitializedHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationInitializationRecordValue> {

  static final String CONDITIONAL_UPDATE_SCRIPT =
      """
      if (ctx._source.state == null || ctx._source.state == 'CREATED') {
          ctx._source.state = params.state;
      }
      if (ctx._source.startDate == null) {
          ctx._source.startDate = params.startDate;
      }
      if (params.operationsTotalCount >= 0) {
          ctx._source.operationsTotalCount =
              (ctx._source.operationsTotalCount ?: 0) + params.operationsTotalCount;
      }
      """;

  private final String indexName;

  /**
   * Handler-local storage for the partition-local total count per entity ID. The count is stored
   * here instead of on the shared {@link BatchOperationEntity} to avoid contaminating the entity's
   * upsert body (see class Javadoc). Entries are removed during {@link #flush}.
   */
  private final Map<String, Integer> pendingOperationsTotalCounts = new HashMap<>();

  public BatchOperationInitializedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_INITIALIZATION;
  }

  @Override
  public Class<BatchOperationEntity> getEntityType() {
    return BatchOperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationInitializationRecordValue> record) {
    return record.getIntent().equals(BatchOperationIntent.INITIALIZED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationInitializationRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationInitializationRecordValue> record,
      final BatchOperationEntity entity) {
    entity
        .setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .setState(BatchOperationState.ACTIVE);

    // Store the count in a handler-local map instead of on the shared entity to avoid
    // contaminating the upsert body that other handlers will serialize (see class Javadoc).
    final int totalCount = record.getValue().getOperationsTotalCount();
    if (totalCount >= 0) {
      pendingOperationsTotalCounts.put(entity.getId(), totalCount);
    }
  }

  @Override
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final int operationsTotalCount = pendingOperationsTotalCounts.getOrDefault(entity.getId(), -1);
    pendingOperationsTotalCounts.remove(entity.getId());

    // Use upsertWithScript to be resilient against cross-partition ordering. Each partition
    // independently produces an INITIALIZED event, so multiple exporters write state=ACTIVE to
    // the same document. A late write from a slow partition could overwrite a state that has
    // already advanced (e.g., COMPLETED). The Painless script only transitions to ACTIVE if the
    // current state is CREATED or null, preventing state regression.
    // The script also atomically increments operationsTotalCount with this partition's item count.
    // If the document does not yet exist, the upsert creates it from the entity.
    batchRequest.upsertWithScript(
        indexName,
        entity.getId(),
        entity,
        CONDITIONAL_UPDATE_SCRIPT,
        Map.of(
            BatchOperationTemplate.STATE, entity.getState().name(),
            BatchOperationTemplate.START_DATE, entity.getStartDate(),
            BatchOperationTemplate.OPERATIONS_TOTAL_COUNT, operationsTotalCount));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
