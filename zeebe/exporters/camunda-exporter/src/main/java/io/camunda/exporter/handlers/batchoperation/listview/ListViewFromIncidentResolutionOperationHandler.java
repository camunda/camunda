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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

public class ListViewFromIncidentResolutionOperationHandler
    extends AbstractProcessInstanceFromOperationItemHandler<IncidentRecordValue> {

  public ListViewFromIncidentResolutionOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, batchOperationCache, OperationType.RESOLVE_INCIDENT);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  protected boolean isFailed(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(IncidentIntent.RESOLVE)
        && record.getRecordType() == RecordType.COMMAND_REJECTION
        && record.getRejectionType() != RejectionType.NULL_VAL
        && record.getRejectionType() != RejectionType.NOT_FOUND;
  }

  @Override
  protected boolean isCompleted(final Record<IncidentRecordValue> record) {
    return record.getIntent() == IncidentIntent.RESOLVED;
  }
}
