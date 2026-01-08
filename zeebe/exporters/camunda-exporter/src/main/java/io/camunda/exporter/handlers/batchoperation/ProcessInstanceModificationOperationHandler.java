/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.ExporterMetadata;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

/**
 * This handles the batch operation item status of batch operations of type MODIFY_PROCESS_INSTANCE.
 * It handles the migration of process instances by tracking their modification status and updating
 * the corresponding batch operation item entity.
 */
public class ProcessInstanceModificationOperationHandler
    extends AbstractOperationStatusHandler<ProcessInstanceModificationRecordValue> {

  public ProcessInstanceModificationOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      final ExporterMetadata exporterMetadata) {
    super(
        indexName,
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        OperationType.MODIFY_PROCESS_INSTANCE,
        batchOperationCache,
        exporterMetadata);
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceModificationRecordValue> record) {
    return super.handlesRecord(record);
  }

  @Override
  long getRootProcessInstanceKey(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getValue().getRootProcessInstanceKey();
  }

  @Override
  long getItemKey(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  long getProcessInstanceKey(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  boolean isCompleted(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceModificationIntent.MODIFIED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceModificationIntent.MODIFY)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
