/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.zeebe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Map;

/**
 * Generic record DTO used when fetching from the combined Zeebe record index, where all value types
 * are stored in a single index. The {@code value} field is kept as a raw {@link Map} so that each
 * import service can convert it to the specific typed DTO it needs.
 *
 * <p>This class is intentionally <em>not</em> a subclass of {@link ZeebeRecordDto} because the
 * generic type parameters of that class ({@code VALUE extends RecordValue}, {@code INTENT extends
 * Intent}) cannot accommodate the combined-index scenario where the concrete value and intent types
 * vary per record.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZeebeGenericRecordDto {

  private long position;
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
  /**
   * Raw value payload stored as a map so that this single DTO class can represent records of any
   * value type. Each import service is responsible for converting this map to its specific typed
   * value DTO (e.g., {@code ZeebeProcessInstanceDataDto}) via
   * {@code ObjectMapper.convertValue(value, SpecificValueDto.class)}.
   */
  private Map<String, Object> value;
  /** Intent stored as a plain string so it can hold any value-type-specific intent enum name. */
  private String intent;

  private Map<String, Object> authorizations;
  private long operationReference;

  public ZeebeGenericRecordDto() {}

  // ---- getters & setters ----

  public long getPosition() {
    return position;
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

  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  public void setSourceRecordPosition(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public RecordType getRecordType() {
    return recordType;
  }

  public void setRecordType(final RecordType recordType) {
    this.recordType = recordType;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public void setRejectionType(final RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(final String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public String getBrokerVersion() {
    return brokerVersion;
  }

  public void setBrokerVersion(final String brokerVersion) {
    this.brokerVersion = brokerVersion;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public void setValueType(final ValueType valueType) {
    this.valueType = valueType;
  }

  public Map<String, Object> getValue() {
    return value;
  }

  public void setValue(final Map<String, Object> value) {
    this.value = value;
  }

  public String getIntent() {
    return intent;
  }

  public void setIntent(final String intent) {
    this.intent = intent;
  }

  public Map<String, Object> getAuthorizations() {
    return authorizations;
  }

  public void setAuthorizations(final Map<String, Object> authorizations) {
    this.authorizations = authorizations;
  }

  public long getOperationReference() {
    return operationReference;
  }

  public void setOperationReference(final long operationReference) {
    this.operationReference = operationReference;
  }
}
