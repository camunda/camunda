/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe;

import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

public abstract class ZeebeRecordDto<VALUE extends RecordValue, INTENT extends Intent>
    implements Record<VALUE> {

  private long position;
  // sequence field was introduced with 8.2.0, it will not be present in records of prior versions
  private Long sequence;
  private long sourceRecordPosition;
  private long key;
  private long timestamp;
  private int partitionId;
  private RecordType recordType;
  private RejectionType rejectionType;
  private String rejectionReason;
  private String brokerVersion;
  private ValueType valueType;
  private VALUE value;
  private INTENT intent;
  private Map<String, Object> authorizations;
  private long operationReference;

  public ZeebeRecordDto() {}

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  public OffsetDateTime getDateForTimestamp() {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
  }

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
  public INTENT getIntent() {
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

  @Override
  public Agent getAgent() {
    return null;
  }

  @Override
  public int getRecordVersion() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  @Override
  public VALUE getValue() {
    return value;
  }

  @Override
  public long getOperationReference() {
    return operationReference;
  }

  @Override
  public Record<VALUE> copyOf() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  public void setOperationReference(final long operationReference) {
    this.operationReference = operationReference;
  }

  public void setValue(final VALUE value) {
    this.value = value;
  }

  public void setValueType(final ValueType valueType) {
    this.valueType = valueType;
  }

  public void setAuthorizations(final Map<String, Object> authorizations) {
    this.authorizations = authorizations;
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

  public void setIntent(final INTENT intent) {
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

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(final Long sequence) {
    this.sequence = sequence;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeRecordDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        position,
        sequence,
        sourceRecordPosition,
        key,
        timestamp,
        partitionId,
        recordType,
        rejectionType,
        rejectionReason,
        brokerVersion,
        valueType,
        value,
        intent,
        authorizations,
        operationReference);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeRecordDto<?, ?> that = (ZeebeRecordDto<?, ?>) o;
    return position == that.position
        && sourceRecordPosition == that.sourceRecordPosition
        && key == that.key
        && timestamp == that.timestamp
        && partitionId == that.partitionId
        && operationReference == that.operationReference
        && Objects.equals(sequence, that.sequence)
        && Objects.equals(recordType, that.recordType)
        && Objects.equals(rejectionType, that.rejectionType)
        && Objects.equals(rejectionReason, that.rejectionReason)
        && Objects.equals(brokerVersion, that.brokerVersion)
        && Objects.equals(valueType, that.valueType)
        && Objects.equals(value, that.value)
        && Objects.equals(intent, that.intent)
        && Objects.equals(authorizations, that.authorizations);
  }

  @Override
  public String toString() {
    return "ZeebeRecordDto(super="
        + super.toString()
        + ", position="
        + getPosition()
        + ", sequence="
        + getSequence()
        + ", sourceRecordPosition="
        + getSourceRecordPosition()
        + ", key="
        + getKey()
        + ", timestamp="
        + getTimestamp()
        + ", partitionId="
        + getPartitionId()
        + ", recordType="
        + getRecordType()
        + ", rejectionType="
        + getRejectionType()
        + ", rejectionReason="
        + getRejectionReason()
        + ", brokerVersion="
        + getBrokerVersion()
        + ", valueType="
        + getValueType()
        + ", value="
        + getValue()
        + ", intent="
        + getIntent()
        + ", authorizations="
        + getAuthorizations()
        + ", operationReference="
        + getOperationReference()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String position = "position";
    public static final String sequence = "sequence";
    public static final String sourceRecordPosition = "sourceRecordPosition";
    public static final String key = "key";
    public static final String timestamp = "timestamp";
    public static final String partitionId = "partitionId";
    public static final String recordType = "recordType";
    public static final String rejectionType = "rejectionType";
    public static final String rejectionReason = "rejectionReason";
    public static final String brokerVersion = "brokerVersion";
    public static final String valueType = "valueType";
    public static final String value = "value";
    public static final String intent = "intent";
    public static final String authorizations = "authorizations";
    public static final String operationReference = "operationReference";
  }
}
