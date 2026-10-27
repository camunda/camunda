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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Optional;

/**
 * This handles the batch operation item status of batch operations of type RESUME_PROCESS_INSTANCE.
 * It tracks the resumption of process instances by updating the corresponding batch operation item
 * entity.
 */
public class ProcessInstanceResumptionBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<ProcessInstanceRecordValue> {

  public ProcessInstanceResumptionBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(batchOperationWriter, batchOperationCache, BatchOperationType.RESUME_PROCESS_INSTANCE);
  }

  @Override
  long getItemKey(final Record<ProcessInstanceRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  Optional<Long> getProcessInstanceKey(final Record<ProcessInstanceRecordValue> record) {
    return Optional.of(record.getValue().getProcessInstanceKey());
  }

  @Override
  Optional<Long> getRootProcessInstanceKey(final Record<ProcessInstanceRecordValue> record) {
    return Optional.of(record.getValue().getRootProcessInstanceKey());
  }

  @Override
  boolean isCompleted(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceIntent.RESUMED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceIntent.RESUME)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
