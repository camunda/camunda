/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.operationReferenceNullValue;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

/** This aggregates the batch operation status of incident management tasks */
public class IncidentBatchOperationExportHandler
    implements RdbmsExportHandler<IncidentRecordValue> {

  private final BatchOperationWriter batchOperationWriter;

  public IncidentBatchOperationExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<IncidentRecordValue> record) {
    return record.getOperationReference() != operationReferenceNullValue()
        && (isIncidentResolved(record) || isFailed(record));
  }

  @Override
  public void export(final Record<IncidentRecordValue> record) {
    if (isIncidentResolved(record)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(), record.getKey(), BatchOperationItemState.COMPLETED);
    } else if (isFailed(record)) {
      batchOperationWriter.updateItem(
          record.getOperationReference(), record.getKey(), BatchOperationItemState.FAILED);
    }
  }

  private boolean isIncidentResolved(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(IncidentIntent.RESOLVED);
  }

  private boolean isFailed(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(IncidentIntent.RESOLVE)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
