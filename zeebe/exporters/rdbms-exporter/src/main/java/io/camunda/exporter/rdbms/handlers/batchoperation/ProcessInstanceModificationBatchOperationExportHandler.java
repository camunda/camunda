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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

/**
 * This handles the batch operation item status of batch operations of type MODIFY_PROCESS_INSTANCE.
 * It handles the migration of process instances by tracking their modification status and updating
 * the corresponding batch operation item entity.
 */
public class ProcessInstanceModificationBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<ProcessInstanceModificationRecordValue> {

  public ProcessInstanceModificationBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(batchOperationWriter, batchOperationCache, BatchOperationType.MODIFY_PROCESS_INSTANCE);
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
  protected boolean isCompleted(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceModificationIntent.MODIFIED);
  }

  @Override
  protected boolean isFailed(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceModificationIntent.MODIFY)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
