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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.Optional;

/**
 * Tracks the status of individual items in a batch operation of type {@link
 * BatchOperationType#UPDATE_JOB}. A job update is considered completed when the engine emits {@link
 * JobIntent#UPDATED} or {@link JobIntent#PRIORITY_UPDATED}, and failed when the {@link
 * JobIntent#UPDATE} command is rejected.
 */
public class JobBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<JobRecordValue> {

  public JobBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(batchOperationWriter, batchOperationCache, BatchOperationType.UPDATE_JOB);
  }

  @Override
  long getItemKey(final Record<JobRecordValue> record) {
    return record.getKey();
  }

  @Override
  Optional<Long> getProcessInstanceKey(final Record<JobRecordValue> record) {
    return Optional.of(record.getValue().getProcessInstanceKey());
  }

  @Override
  Optional<Long> getRootProcessInstanceKey(final Record<JobRecordValue> record) {
    return Optional.of(record.getValue().getRootProcessInstanceKey());
  }

  @Override
  boolean isCompleted(final Record<JobRecordValue> record) {
    return record.getIntent().equals(JobIntent.UPDATED)
        || record.getIntent().equals(JobIntent.PRIORITY_UPDATED);
  }

  @Override
  boolean isFailed(final Record<JobRecordValue> record) {
    return record.getIntent().equals(JobIntent.UPDATE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
