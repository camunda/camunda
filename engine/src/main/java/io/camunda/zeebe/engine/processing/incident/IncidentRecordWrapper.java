/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.incident;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;

final class IncidentRecordWrapper implements TypedRecord<ProcessInstanceRecord> {

  private final long key;
  private final ProcessInstanceIntent intent;
  private final ProcessInstanceRecord record;

  IncidentRecordWrapper(
      final long key, final ProcessInstanceIntent intent, final ProcessInstanceRecord record) {
    this.key = key;
    this.intent = intent;
    this.record = record;
  }

  @Override
  public String toJson() {
    return null;
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
  public String getBrokerVersion() {
    return null;
  }

  @Override
  public ValueType getValueType() {
    return null;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public ProcessInstanceRecord getValue() {
    return record;
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
  public Record<ProcessInstanceRecord> clone() {
    return this;
  }
}
