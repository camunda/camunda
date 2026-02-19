/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import java.util.Collection;
import java.util.List;

/** Exports a batch operation chunk record, which contains all the item keys, to the database. */
public class BatchOperationChunkExportHandler
    implements RdbmsExportHandler<BatchOperationChunkRecordValue> {

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
        String.valueOf(value.getBatchOperationKey()),
        mapItems(value.getItems(), record.getValue().getBatchOperationKey()));
  }

  private List<BatchOperationItemDbModel> mapItems(
      final Collection<BatchOperationItemValue> items, final long batchOperationKey) {
    return items.stream().map(item -> mapItem(item, batchOperationKey)).toList();
  }

  private BatchOperationItemDbModel mapItem(
      final BatchOperationItemValue value, final long batchOperationKey) {
    return new BatchOperationItemDbModel(
        Long.toString(batchOperationKey),
        value.getItemKey(),
        value.getProcessInstanceKey(),
        // for RDBMS this should not be -1, but if it was we would want to make to NULL in the
        // database
        value.getRootProcessInstanceKey() > 0 ? value.getRootProcessInstanceKey() : null,
        BatchOperationItemState.ACTIVE,
        null,
        null);
  }
}
