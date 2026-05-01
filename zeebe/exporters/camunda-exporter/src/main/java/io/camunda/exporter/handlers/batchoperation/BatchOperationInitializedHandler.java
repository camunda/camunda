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
import io.camunda.exporter.store.IndexLocator;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import java.util.Map;

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
      """;

  private final String indexName;

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
  }

  @Override
  public void flush(
      final IndexLocator indexLocator,
      final BatchOperationEntity entity,
      final BatchRequest batchRequest)
      throws PersistenceException {
    // Use upsertWithScript to be resilient against cross-partition ordering. Each partition
    // independently produces an INITIALIZED event, so multiple exporters write state=ACTIVE to
    // the same document. A late write from a slow partition could overwrite a state that has
    // already advanced (e.g., COMPLETED). The Painless script only transitions to ACTIVE if the
    // current state is CREATED or null, preventing state regression.
    // If the document does not yet exist, the upsert creates it from the entity.
    batchRequest.upsertWithScript(
        indexName,
        entity.getId(),
        entity,
        CONDITIONAL_UPDATE_SCRIPT,
        Map.of(
            BatchOperationTemplate.STATE, entity.getState().name(),
            BatchOperationTemplate.START_DATE, entity.getStartDate()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
