/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter.record;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import java.util.Objects;

public class MockRecordMetadata extends ExporterMappedObject implements Cloneable {

  private static final String BROKER_VERSION = "";
  private final Intent intent = ProcessInstanceCreationIntent.CREATE;
  private int partitionId = 0;
  private RecordType recordType = RecordType.COMMAND;
  private final RejectionType rejectionType = RejectionType.NULL_VAL;
  private final String rejectionReason = "";
  private ValueType valueType = ValueType.PROCESS_INSTANCE_CREATION;

  public Intent getIntent() {
    return intent;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public MockRecordMetadata setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public MockRecordMetadata setRecordType(final RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public MockRecordMetadata setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public String getBrokerVersion() {
    return BROKER_VERSION;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getIntent(),
        getPartitionId(),
        getRecordType(),
        getRejectionType(),
        getRejectionReason(),
        getValueType(),
        getBrokerVersion());
  }

  @Override
  public boolean equals(final Object o) {
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
        && getValueType() == metadata.getValueType()
        && Objects.equals(getBrokerVersion(), metadata.getBrokerVersion());
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (final CloneNotSupportedException e) {
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
        + ", brokerVersion='"
        + BROKER_VERSION
        + '\''
        + ", valueType="
        + valueType
        + '}';
  }
}
