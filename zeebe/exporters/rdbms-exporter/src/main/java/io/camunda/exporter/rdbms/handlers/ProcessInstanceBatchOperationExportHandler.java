/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemStatus;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/** This aggregates the batch operation status of process instance cancellations */
public class ProcessInstanceBatchOperationExportHandler
    implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private final BatchOperationWriter batchOperationWriter;

  public ProcessInstanceBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return record.getOperationReference() != operationReferenceNullValue()
        && record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getValue().getParentProcessInstanceKey() == -1
        && (isProcessCanceled(record) || isFailed(record));
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (isProcessCanceled(record)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(),
          value.getProcessInstanceKey(),
          BatchOperationItemStatus.COMPLETED);
    } else if (isFailed(record)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(),
          value.getProcessInstanceKey(),
          BatchOperationItemStatus.FAILED);
    }
  }

  private boolean isProcessCanceled(final Record<ProcessInstanceRecordValue> record) {
    return record.getValue().getBpmnElementType() == BpmnElementType.PROCESS
        && record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  private boolean isFailed(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceIntent.CANCEL)
        && record.getRejectionType() != null;
  }
}
