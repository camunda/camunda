/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.Set;

/** Tracks the execution of a batch operation, like completion. */
public class BatchOperationExecutionExportHandler
    implements RdbmsExportHandler<BatchOperationExecutionRecordValue> {

  private static final Set<BatchOperationExecutionIntent> EXPORTABLE_INTENTS =
      Set.of(
          BatchOperationExecutionIntent.EXECUTING,
          BatchOperationExecutionIntent.EXECUTED,
          BatchOperationExecutionIntent.COMPLETED);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationExecutionExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationExecutionRecordValue> record) {
    if (record.getValueType() == ValueType.BATCH_OPERATION_EXECUTION
        && record.getIntent() instanceof final BatchOperationExecutionIntent intent) {
      return EXPORTABLE_INTENTS.contains(intent);
    }

    return false;
  }

  @Override
  public void export(final Record<BatchOperationExecutionRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationKey = value.getBatchOperationKey();
    if (record.getIntent().equals(BatchOperationExecutionIntent.COMPLETED)) {
      batchOperationWriter.finish(
          batchOperationKey, DateUtil.toOffsetDateTime(record.getTimestamp()));
    }
  }
}
