/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.exporter.record;

import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import java.util.Objects;

public class MockRecordMetadata extends ExporterMappedObject implements Cloneable {

  private Intent intent = WorkflowInstanceCreationIntent.CREATE;
  private int partitionId = 0;
  private RecordType recordType = RecordType.COMMAND;
  private RejectionType rejectionType = RejectionType.NULL_VAL;
  private String rejectionReason = "";
  private ValueType valueType = ValueType.WORKFLOW_INSTANCE_CREATION;

  public MockRecordMetadata() {}

  public MockRecordMetadata(
      Intent intent,
      int partitionId,
      RecordType recordType,
      RejectionType rejectionType,
      String rejectionReason,
      ValueType valueType) {
    this.intent = intent;
    this.partitionId = partitionId;
    this.recordType = recordType;
    this.rejectionType = rejectionType;
    this.rejectionReason = rejectionReason;
    this.valueType = valueType;
  }

  public Intent getIntent() {
    return intent;
  }

  public MockRecordMetadata setIntent(Intent intent) {
    this.intent = intent;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public MockRecordMetadata setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public MockRecordMetadata setRecordType(RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public MockRecordMetadata setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public MockRecordMetadata setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public MockRecordMetadata setValueType(ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getIntent(),
        getPartitionId(),
        getRecordType(),
        getRejectionType(),
        getRejectionReason(),
        getValueType());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MockRecordMetadata)) {
      return false;
    }

    final MockRecordMetadata metadata = (MockRecordMetadata) o;
    return getPartitionId() == metadata.getPartitionId()
        && Objects.equals(getIntent(), metadata.getIntent())
        && getRecordType() == metadata.getRecordType()
        && getRejectionType() == metadata.getRejectionType()
        && Objects.equals(getRejectionReason(), metadata.getRejectionReason())
        && getValueType() == metadata.getValueType();
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    return "MockRecordMetadata{"
        + "intent="
        + intent
        + ", partitionId="
        + partitionId
        + ", recordType="
        + recordType
        + ", rejectionType="
        + rejectionType
        + ", rejectionReason='"
        + rejectionReason
        + '\''
        + ", valueType="
        + valueType
        + '}';
  }
}
