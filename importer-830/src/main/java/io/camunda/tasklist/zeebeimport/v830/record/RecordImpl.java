/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;

public class RecordImpl<T extends RecordValue> implements Record<T> {

  private int partitionId;

  @JsonDeserialize(using = StringToIntentSerializer.class)
  private Intent intent;

  private RecordType recordType;
  private RejectionType rejectionType;
  private String rejectionReason;
  private String brokerVersion;
  private ValueType valueType;

  private long key;
  private long position;
  private long timestamp;
  private long sourceRecordPosition;

  private T value;

  public RecordImpl() {}

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public RecordType getRecordType() {
    return recordType;
  }

  @Override
  public RejectionType getRejectionType() {
    return rejectionType;
  }

  @Override
  public String getRejectionReason() {
    return rejectionReason;
  }

  @Override
  public String getBrokerVersion() {
    return brokerVersion;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  public void setValueType(ValueType valueType) {
    this.valueType = valueType;
  }

  @Override
  public T getValue() {
    return value;
  }

  public void setValue(T value) {
    this.value = value;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void setBrokerVersion(final String brokerVersion) {
    this.brokerVersion = brokerVersion;
  }

  public void setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public void setRecordType(RecordType recordType) {
    this.recordType = recordType;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public void setIntent(Intent intent) {
    this.intent = intent;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public void setKey(long key) {
    this.key = key;
  }

  public void setSourceRecordPosition(long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  @Override
  public Record<T> clone() {
    throw new UnsupportedOperationException("Clone not implemented");
  }

  @Override
  public String toString() {
    return "RecordImpl{"
        + "partitionId="
        + partitionId
        + ", intent="
        + intent
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
        + ", key="
        + key
        + ", position="
        + position
        + ", timestamp="
        + timestamp
        + ", sourceRecordPosition="
        + sourceRecordPosition
        + ", value="
        + value
        + '}';
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }
}
