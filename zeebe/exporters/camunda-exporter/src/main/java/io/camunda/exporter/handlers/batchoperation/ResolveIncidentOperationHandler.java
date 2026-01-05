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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

/**
 * This handles the batch operation status item of batch operations of type RESOLVE_INCIDENT. It
 * tracks the resolution of incidents by updating the corresponding batch operation item entity.
 */
public class ResolveIncidentOperationHandler
    extends AbstractOperationStatusHandler<IncidentRecordValue> {

  public ResolveIncidentOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, ValueType.INCIDENT, OperationType.RESOLVE_INCIDENT, batchOperationCache);
  }

  @Override
  long getRootProcessInstanceKey(final Record<IncidentRecordValue> record) {
    return -1; // TODO implement when available in the record
    // https://github.com/camunda/camunda/pull/43320
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
