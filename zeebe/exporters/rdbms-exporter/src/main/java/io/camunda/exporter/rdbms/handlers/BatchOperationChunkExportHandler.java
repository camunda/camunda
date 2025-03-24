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
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exports a batch operation chunk record, which contains all the item keys, to the database. */
public class BatchOperationChunkExportHandler
    implements RdbmsExportHandler<BatchOperationChunkRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationChunkExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationChunkExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationChunkRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_CHUNK
        && record.getIntent().equals(BatchOperationChunkIntent.CREATE);
  }

  @Override
  public void export(final Record<BatchOperationChunkRecordValue> record) {
    final var value = record.getValue();
    batchOperationWriter.updateBatchAndInsertItems(
        value.getBatchOperationKey(), value.getItemKeys());
  }
}
