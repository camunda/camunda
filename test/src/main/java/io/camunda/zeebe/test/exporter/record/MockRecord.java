/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Objects;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class MockRecord extends ExporterMappedObject implements Record, Cloneable {

  private long position = 0;
  private long sourceRecordPosition = -1;
  private long key = -1;
  private long timestamp = -1;
  private MockRecordMetadata metadata = new MockRecordMetadata();
  private MockRecordValueWithVariables value = new MockRecordValueWithVariables();

  public MockRecord() {}

  public MockRecord(
      final long position,
      final long sourceRecordPosition,
      final long key,
      final long timestamp,
      final MockRecordMetadata metadata,
      final MockRecordValueWithVariables value) {
    this.position = position;
    this.sourceRecordPosition = sourceRecordPosition;
    this.key = key;
    this.timestamp = timestamp;
    this.metadata = metadata;
    this.value = value;
  }

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
    return sourceRecordPosition;
  }

  public MockRecord setSourceRecordPosition(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
    return this;
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

  public MockRecord setValue(final MockRecordValueWithVariables value) {
    this.value = value;
    return this;
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
        + sourceRecordPosition
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
