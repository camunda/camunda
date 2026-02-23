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
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.Optional;

public class ProcessInstanceHistoryDeletionBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<HistoryDeletionRecordValue> {

  public ProcessInstanceHistoryDeletionBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      final BatchOperationType relevantOperationType) {
    super(batchOperationWriter, batchOperationCache, relevantOperationType);
  }

  @Override
  public boolean canExport(final Record<HistoryDeletionRecordValue> record) {
    return super.canExport(record)
        && record.getValue().getResourceType().equals(HistoryDeletionType.PROCESS_INSTANCE);
  }

  @Override
  long getItemKey(final Record<HistoryDeletionRecordValue> record) {
    return record.getValue().getResourceKey();
  }

  @Override
  Optional<Long> getProcessInstanceKey(final Record<HistoryDeletionRecordValue> record) {
    return Optional.of(record.getValue().getResourceKey());
  }

  @Override
  Optional<Long> getRootProcessInstanceKey(final Record<HistoryDeletionRecordValue> record) {
    return Optional.empty();
  }

  @Override
  boolean isCompleted(final Record<HistoryDeletionRecordValue> record) {
    return record.getIntent().equals(HistoryDeletionIntent.DELETED);
  }

  @Override
  boolean isFailed(final Record<HistoryDeletionRecordValue> record) {
    return record.getIntent().equals(HistoryDeletionIntent.DELETE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
