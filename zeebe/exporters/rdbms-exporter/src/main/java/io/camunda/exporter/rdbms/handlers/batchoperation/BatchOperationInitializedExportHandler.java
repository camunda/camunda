/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.util.DateUtil;

/**
 * Exports a batch operation initialization record to the database. This handler transitions the
 * batch operation to {@code ACTIVE} state and sets the {@code startDate}.
 */
public class BatchOperationInitializedExportHandler
    implements RdbmsExportHandler<BatchOperationInitializationRecordValue> {

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationInitializedExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationInitializationRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_INITIALIZATION
        && record.getIntent().equals(BatchOperationIntent.INITIALIZED);
  }

  @Override
  public void export(final Record<BatchOperationInitializationRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationKey = String.valueOf(value.getBatchOperationKey());
    final var startDate = DateUtil.toOffsetDateTime(record.getTimestamp());
    batchOperationWriter.activate(batchOperationKey, startDate);
  }
}
