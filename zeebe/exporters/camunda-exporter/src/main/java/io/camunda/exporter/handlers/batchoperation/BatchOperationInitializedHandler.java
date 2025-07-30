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

public class BatchOperationInitializedHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationInitializationRecordValue> {

  private final String indexName;

  public BatchOperationInitializedHandler(final String indexName) {
    this.indexName = indexName;
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
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(BatchOperationTemplate.STATE, entity.getState());
    updateFields.put(BatchOperationTemplate.START_DATE, entity.getStartDate());
    batchRequest.update(indexName, entity.getId(), updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
