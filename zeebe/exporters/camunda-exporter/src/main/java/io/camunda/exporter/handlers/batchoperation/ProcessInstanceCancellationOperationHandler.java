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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/** This aggregates the batch operation status of process instance cancellations */
public class ProcessInstanceCancellationOperationHandler
    extends AbstractOperationStatusHandler<ProcessInstanceRecordValue> {

  public ProcessInstanceCancellationOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(
        indexName,
        ValueType.PROCESS_INSTANCE,
        OperationType.CANCEL_PROCESS_INSTANCE,
        batchOperationCache);
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
        && record.getValue().getBpmnElementType() == BpmnElementType.PROCESS
        && record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceIntent.CANCEL)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
