/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.behavior;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;

public class ProcessingRecordWrapper implements TypedRecord<WorkflowInstanceRecord> {

  private final long key;
  private final WorkflowInstanceRecord recordValue;
  private final Intent intent;

  public ProcessingRecordWrapper(
      final long key, final WorkflowInstanceRecord recordValue, final Intent intent) {
    this.key = key;
    this.recordValue = recordValue;
    this.intent = intent;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public WorkflowInstanceRecord getValue() {

    return recordValue;
  }

  @Override
  public int getRequestStreamId() {
    return 0;
  }

  @Override
  public long getRequestId() {
    return 0;
  }

  @Override
  public long getLength() {
    return 0;
  }

  @Override
  public long getPosition() {
    return 0;
  }

  @Override
  public long getSourceRecordPosition() {
    return 0;
  }

  @Override
  public long getTimestamp() {
    return 0;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  @Override
  public int getPartitionId() {
    return 0;
  }

  @Override
  public RecordType getRecordType() {
    return null;
  }

  @Override
  public RejectionType getRejectionType() {
    return null;
  }

  @Override
  public String getRejectionReason() {
    return null;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.WORKFLOW_INSTANCE;
  }

  @Override
  public Record<WorkflowInstanceRecord> clone() {
    return this;
  }

  @Override
  public String toJson() {
    return null;
  }
}
