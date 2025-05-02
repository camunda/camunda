/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.util.DateUtil;

public abstract class RdbmsBatchOperationStatusExportHandler<T extends RecordValue>
    implements RdbmsExportHandler<T> {
  public static final String ERROR_MSG = "%s: %s";

  private final BatchOperationWriter batchOperationWriter;

  public RdbmsBatchOperationStatusExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<T> record) {
    return record.getOperationReference() != operationReferenceNullValue()
        && (isCompleted(record) || isFailed(record));
  }

  @Override
  public void export(final Record<T> record) {
    if (isCompleted(record)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(),
          getItemKey(record),
          BatchOperationItemState.COMPLETED,
          DateUtil.toOffsetDateTime(record.getTimestamp()),
          null);
    } else if (isFailed(record)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(),
          getItemKey(record),
          BatchOperationItemState.FAILED,
          DateUtil.toOffsetDateTime(record.getTimestamp()),
          String.format(ERROR_MSG, record.getRejectionType(), record.getRejectionReason()));
    }
  }

  abstract long getItemKey(Record<T> record);

  /** Checks if the batch operation item is completed */
  abstract boolean isCompleted(Record<T> record);

  /** Checks if the batch operation item is failed */
  abstract boolean isFailed(Record<T> record);
}
