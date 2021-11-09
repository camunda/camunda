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

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class MockRecordMetadata extends ExporterMappedObject implements Cloneable {

  private Intent intent = ProcessInstanceCreationIntent.CREATE;
  private int partitionId = 0;
  private RecordType recordType = RecordType.COMMAND;
  private RejectionType rejectionType = RejectionType.NULL_VAL;
  private String rejectionReason = "";
  private ValueType valueType = ValueType.PROCESS_INSTANCE_CREATION;
  private String brokerVersion = "";

  public MockRecordMetadata() {}

  public MockRecordMetadata(
      final Intent intent,
      final int partitionId,
      final RecordType recordType,
      final RejectionType rejectionType,
      final String rejectionReason,
      final ValueType valueType) {
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

  public MockRecordMetadata setIntent(final Intent intent) {
    this.intent = intent;
    return this;
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

  public MockRecordMetadata setRejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public MockRecordMetadata setRejectionReason(final String rejectionReason) {
    this.rejectionReason = rejectionReason;
    return this;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public MockRecordMetadata setValueType(final ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  public String getBrokerVersion() {
    return brokerVersion;
  }

  public void setBrokerVersion(final String brokerVersion) {
    this.brokerVersion = brokerVersion;
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
        + brokerVersion
        + '\''
        + ", valueType="
        + valueType
        + '}';
  }
}
