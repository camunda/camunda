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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

/**
 * This handles the batch operation status item of batch operations of type RESOLVE_INCIDENT. It
 * tracks the resolution of incidents by updating the corresponding batch operation item entity.
 */
public class IncidentBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<IncidentRecordValue> {

  public IncidentBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(batchOperationWriter, batchOperationCache, BatchOperationType.RESOLVE_INCIDENT);
  }

  @Override
  long getItemKey(final Record<IncidentRecordValue> record) {
    return record.getKey();
  }

  @Override
  long getProcessInstanceKey(final Record<IncidentRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  boolean isCompleted(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(IncidentIntent.RESOLVED);
  }

  @Override
  boolean isFailed(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(IncidentIntent.RESOLVE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
