/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class ListViewFromProcessInstanceCancellationOperationHandler
    extends AbstractProcessInstanceFromOperationItemHandler<ProcessInstanceRecordValue> {

  public ListViewFromProcessInstanceCancellationOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, batchOperationCache, OperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  private boolean isProcess(final Record<ProcessInstanceRecordValue> record) {
    return record.getValue().getBpmnElementType() == BpmnElementType.PROCESS;
  }

  private boolean isRootProcessInstance(final Record<ProcessInstanceRecordValue> record) {
    return record.getValue().getParentProcessInstanceKey() == -1;
  }

  @Override
  protected boolean isFailed(final Record<ProcessInstanceRecordValue> record) {
    return isRootProcessInstance(record)
        && record.getIntent().equals(ProcessInstanceIntent.CANCEL)
        && record.getRecordType() == RecordType.COMMAND_REJECTION
        && record.getRejectionType() != RejectionType.NULL_VAL
        && record.getRejectionType() != RejectionType.NOT_FOUND;
  }

  @Override
  protected boolean isCompleted(final Record<ProcessInstanceRecordValue> record) {
    return isProcess(record)
        && isRootProcessInstance(record)
        && record.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED;
  }
}
