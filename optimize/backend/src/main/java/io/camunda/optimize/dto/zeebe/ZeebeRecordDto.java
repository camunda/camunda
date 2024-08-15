/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe;

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
    final int PRIME = 59;
    int result = 1;
    final long $position = getPosition();
    result = result * PRIME + (int) ($position >>> 32 ^ $position);
    final Object $sequence = getSequence();
    result = result * PRIME + ($sequence == null ? 43 : $sequence.hashCode());
    final long $sourceRecordPosition = getSourceRecordPosition();
    result = result * PRIME + (int) ($sourceRecordPosition >>> 32 ^ $sourceRecordPosition);
    final long $key = getKey();
    result = result * PRIME + (int) ($key >>> 32 ^ $key);
    final long $timestamp = getTimestamp();
    result = result * PRIME + (int) ($timestamp >>> 32 ^ $timestamp);
    result = result * PRIME + getPartitionId();
    final Object $recordType = getRecordType();
    result = result * PRIME + ($recordType == null ? 43 : $recordType.hashCode());
    final Object $rejectionType = getRejectionType();
    result = result * PRIME + ($rejectionType == null ? 43 : $rejectionType.hashCode());
    final Object $rejectionReason = getRejectionReason();
    result = result * PRIME + ($rejectionReason == null ? 43 : $rejectionReason.hashCode());
    final Object $brokerVersion = getBrokerVersion();
    result = result * PRIME + ($brokerVersion == null ? 43 : $brokerVersion.hashCode());
    final Object $valueType = getValueType();
    result = result * PRIME + ($valueType == null ? 43 : $valueType.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $intent = getIntent();
    result = result * PRIME + ($intent == null ? 43 : $intent.hashCode());
    final Object $authorizations = getAuthorizations();
    result = result * PRIME + ($authorizations == null ? 43 : $authorizations.hashCode());
    final long $operationReference = getOperationReference();
    result = result * PRIME + (int) ($operationReference >>> 32 ^ $operationReference);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeRecordDto)) {
      return false;
    }
    final ZeebeRecordDto<?, ?> other = (ZeebeRecordDto<?, ?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getPosition() != other.getPosition()) {
      return false;
    }
    final Object this$sequence = getSequence();
    final Object other$sequence = other.getSequence();
    if (this$sequence == null ? other$sequence != null : !this$sequence.equals(other$sequence)) {
      return false;
    }
    if (getSourceRecordPosition() != other.getSourceRecordPosition()) {
      return false;
    }
    if (getKey() != other.getKey()) {
      return false;
    }
    if (getTimestamp() != other.getTimestamp()) {
      return false;
    }
    if (getPartitionId() != other.getPartitionId()) {
      return false;
    }
    final Object this$recordType = getRecordType();
    final Object other$recordType = other.getRecordType();
    if (this$recordType == null
        ? other$recordType != null
        : !this$recordType.equals(other$recordType)) {
      return false;
    }
    final Object this$rejectionType = getRejectionType();
    final Object other$rejectionType = other.getRejectionType();
    if (this$rejectionType == null
        ? other$rejectionType != null
        : !this$rejectionType.equals(other$rejectionType)) {
      return false;
    }
    final Object this$rejectionReason = getRejectionReason();
    final Object other$rejectionReason = other.getRejectionReason();
    if (this$rejectionReason == null
        ? other$rejectionReason != null
        : !this$rejectionReason.equals(other$rejectionReason)) {
      return false;
    }
    final Object this$brokerVersion = getBrokerVersion();
    final Object other$brokerVersion = other.getBrokerVersion();
    if (this$brokerVersion == null
        ? other$brokerVersion != null
        : !this$brokerVersion.equals(other$brokerVersion)) {
      return false;
    }
    final Object this$valueType = getValueType();
    final Object other$valueType = other.getValueType();
    if (this$valueType == null
        ? other$valueType != null
        : !this$valueType.equals(other$valueType)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$intent = getIntent();
    final Object other$intent = other.getIntent();
    if (this$intent == null ? other$intent != null : !this$intent.equals(other$intent)) {
      return false;
    }
    final Object this$authorizations = getAuthorizations();
    final Object other$authorizations = other.getAuthorizations();
    if (this$authorizations == null
        ? other$authorizations != null
        : !this$authorizations.equals(other$authorizations)) {
      return false;
    }
    if (getOperationReference() != other.getOperationReference()) {
      return false;
    }
    return true;
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
