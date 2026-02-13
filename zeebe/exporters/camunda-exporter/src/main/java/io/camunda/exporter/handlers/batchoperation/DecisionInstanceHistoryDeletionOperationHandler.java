/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;

public class DecisionInstanceHistoryDeletionOperationHandler
    extends AbstractOperationStatusHandler<HistoryDeletionRecordValue> {

  public DecisionInstanceHistoryDeletionOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(
        indexName,
        ValueType.HISTORY_DELETION,
        OperationType.DELETE_DECISION_INSTANCE,
        batchOperationCache);
  }

  @Override
  public boolean handlesRecord(final Record<HistoryDeletionRecordValue> record) {
    return super.handlesRecord(record)
        && record.getValue().getResourceType().equals(HistoryDeletionType.DECISION_INSTANCE);
  }

  @Override
  long getRootProcessInstanceKey(final Record<HistoryDeletionRecordValue> record) {
    return -1;
  }

  @Override
  long getItemKey(final Record<HistoryDeletionRecordValue> record) {
    return record.getValue().getResourceKey();
  }

  @Override
  long getProcessInstanceKey(final Record<HistoryDeletionRecordValue> record) {
    return -1L;
  }

  @Override
  boolean isCompleted(final Record<HistoryDeletionRecordValue> record) {
    return record.getValueType() == ValueType.HISTORY_DELETION
        && record.getIntent().equals(HistoryDeletionIntent.DELETED);
  }

  @Override
  boolean isFailed(final Record<HistoryDeletionRecordValue> record) {
    return record.getIntent().equals(HistoryDeletionIntent.DELETE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
