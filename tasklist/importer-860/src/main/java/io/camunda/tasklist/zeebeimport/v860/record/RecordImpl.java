/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v860.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Map;

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

  private int recordVersion;

  private T value;

  private Map<String, Object> authorizations;
  private long operationReference;

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
  public Map<String, Object> getAuthorizations() {
    return authorizations;
  }

  public void setAuthorizations(final Map<String, Object> authorizations) {
    this.authorizations = authorizations;
  }

  @Override
  public boolean isAnonymous() {
    return false;
  }

  @Override
  public int getRecordVersion() {
    return recordVersion;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  @Override
  public T getValue() {
    return value;
  }

  public void setValue(final T value) {
    this.value = value;
  }

  @Override
  public long getOperationReference() {
    return operationReference;
  }

  public void setOperationReference(final long operationReference) {
    this.operationReference = operationReference;
  }

  public void setValueType(final ValueType valueType) {
    this.valueType = valueType;
  }

  public RecordImpl<T> setRecordVersion(final int recordVersion) {
    this.recordVersion = recordVersion;
    return this;
  }

  public void setBrokerVersion(final String brokerVersion) {
    this.brokerVersion = brokerVersion;
  }

  public void setRejectionReason(final String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void setRejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public void setRecordType(final RecordType recordType) {
    this.recordType = recordType;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public void setIntent(final Intent intent) {
    this.intent = intent;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public void setSourceRecordPosition(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  public void setPosition(final long position) {
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
        + ", authorizations="
        + (authorizations == null ? "null" : String.format("[size='%d']", authorizations.size()))
        + ", value="
        + value
        + ", operationReference="
        + operationReference
        + '}';
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }
}
