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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class ProcessInstanceDeletionOperationHandler
    extends AbstractOperationStatusHandler<ProcessInstanceRecordValue> {

  public ProcessInstanceDeletionOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(
        indexName,
        ValueType.PROCESS_INSTANCE,
        OperationType.DELETE_PROCESS_INSTANCE,
        batchOperationCache);
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return super.handlesRecord(record);
  }

  @Override
  long getItemKey(final Record<ProcessInstanceRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  long getProcessInstanceKey(final Record<ProcessInstanceRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  boolean isCompleted(final Record<ProcessInstanceRecordValue> record) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getIntent().equals(ProcessInstanceIntent.DELETED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent();
    return (intent.equals(ProcessInstanceIntent.DELETE)
            || intent.equals(ProcessInstanceIntent.DELETE_COMPLETE))
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
