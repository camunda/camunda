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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

/** This aggregates the batch operation status of incident management tasks */
public class IncidentBatchOperationExportHandler
    extends RdbmsBatchOperationStatusExportHandler<IncidentRecordValue> {

  public IncidentBatchOperationExportHandler(final BatchOperationWriter batchOperationWriter) {
    super(batchOperationWriter);
  }

  @Override
  long getItemKey(final Record<IncidentRecordValue> record) {
    return record.getKey();
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
