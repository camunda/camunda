/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Objects;

public class MockRecord extends ExporterMappedObject implements Record, Cloneable {
  private static final long SOURCE_RECORD_POSITION = -1;
  private long position = 0;
  private long key = -1;
  private long timestamp = -1;
  private MockRecordMetadata metadata = new MockRecordMetadata();
  private final MockRecordValueWithVariables value = new MockRecordValueWithVariables();

  @Override
  public long getPosition() {
    return position;
  }

  public MockRecord setPosition(final long position) {
    this.position = position;
    return this;
  }

  @Override
  public long getSourceRecordPosition() {
    return SOURCE_RECORD_POSITION;
  }

  @Override
  public long getKey() {
    return key;
  }

  public MockRecord setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public Intent getIntent() {
    return metadata.getIntent();
  }

  @Override
  public int getPartitionId() {
    return metadata.getPartitionId();
  }

  @Override
  public RecordType getRecordType() {
    return metadata.getRecordType();
  }

  @Override
  public RejectionType getRejectionType() {
    return metadata.getRejectionType();
  }

  @Override
  public String getRejectionReason() {
    return metadata.getRejectionReason();
  }

  @Override
  public String getBrokerVersion() {
    return metadata.getBrokerVersion();
  }

  @Override
  public ValueType getValueType() {
    return metadata.getValueType();
  }

  @Override
  public MockRecordValueWithVariables getValue() {
    return value;
  }

  public MockRecord setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public MockRecordMetadata getMetadata() {
    return metadata;
  }

  public MockRecord setMetadata(final MockRecordMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getPosition(),
        getSourceRecordPosition(),
        getKey(),
        getTimestamp(),
        getMetadata(),
        getValue());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MockRecord)) {
      return false;
    }

    final MockRecord record = (MockRecord) o;
    return getPosition() == record.getPosition()
        && getSourceRecordPosition() == record.getSourceRecordPosition()
        && getKey() == record.getKey()
        && Objects.equals(getTimestamp(), record.getTimestamp())
        && Objects.equals(getMetadata(), record.getMetadata())
        && Objects.equals(getValue(), record.getValue());
  }

  @Override
  public Record clone() {
    try {
      final MockRecord cloned = (MockRecord) super.clone();
      cloned.metadata = (MockRecordMetadata) metadata.clone();
      return cloned;
    } catch (final CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String toString() {
    return "MockRecord{"
        + "position="
        + position
        + ", sourceRecordPosition="
        + SOURCE_RECORD_POSITION
        + ", key="
        + key
        + ", timestamp="
        + timestamp
        + ", metadata="
        + metadata
        + ", value="
        + value
        + '}';
  }
}
