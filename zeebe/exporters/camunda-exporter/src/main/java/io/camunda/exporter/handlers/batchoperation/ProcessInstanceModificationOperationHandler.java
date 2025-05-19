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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;

public class ProcessInstanceModificationOperationHandler
    extends AbstractOperationStatusHandler<ProcessInstanceModificationRecordValue> {

  public ProcessInstanceModificationOperationHandler(final String indexName) {
    super(indexName, ValueType.PROCESS_INSTANCE_MODIFICATION);
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceModificationRecordValue> record) {
    return super.handlesRecord(record);
  }

  @Override
  long getItemKey(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getValue().getProcessInstanceKey();
  }

  @Override
  boolean isCompleted(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceModificationIntent.MODIFIED);
  }

  @Override
  boolean isFailed(final Record<ProcessInstanceModificationRecordValue> record) {
    return record.getIntent().equals(ProcessInstanceModificationIntent.MODIFY)
        && record.getRejectionType() != RejectionType.NULL_VAL;
  }
}
