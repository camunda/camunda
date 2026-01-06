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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;

public class ListViewFromProcessInstanceMigrationOperationHandler
    extends AbstractProcessInstanceFromOperationItemHandler<ProcessInstanceMigrationRecordValue> {

  public ListViewFromProcessInstanceMigrationOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, batchOperationCache, OperationType.MIGRATE_PROCESS_INSTANCE);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE_MIGRATION;
  }

  @Override
  protected boolean isFailed(final Record<ProcessInstanceMigrationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceMigrationIntent.MIGRATE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }

  @Override
  protected boolean isCompleted(final Record<ProcessInstanceMigrationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceMigrationIntent.MIGRATED);
  }
}
