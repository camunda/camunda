/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

public class ResolveIncidentOperationHandler
    extends AbstractOperationStatusHandler<IncidentRecordValue> {

  public ResolveIncidentOperationHandler(final String indexName) {
    super(indexName, ValueType.INCIDENT);
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
