/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/**
 * This handles the batch operation item status of batch operations of type CANCEL_PROCESS_INSTANCE.
 * It tracks the cancellation of process instances by updating the corresponding batch operation
 * item entity.
 */
public class ProcessInstanceCancellationBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<ProcessInstanceRecordValue> {

  public ProcessInstanceCancellationBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(batchOperationWriter, batchOperationCache, BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return super.canExport(record) && record.getValue().getParentProcessInstanceKey() == -1;
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
    return record.getValue().getBpmnElementType() == BpmnElementType.PROCESS
        && record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceIntent.CANCEL)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
