/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;

/** This aggregates the batch operation status of process instance cancellations */
public class ProcessInstanceMigrationBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<ProcessInstanceMigrationRecordValue> {

  public ProcessInstanceMigrationBatchOperationExportHandler(
      final BatchOperationWriter batchOperationWriter) {
    super(batchOperationWriter);
  }

  @Override
  long getItemKey(final Record<ProcessInstanceMigrationRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  boolean isCompleted(final Record<ProcessInstanceMigrationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceMigrationIntent.MIGRATED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceMigrationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceMigrationIntent.MIGRATE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
