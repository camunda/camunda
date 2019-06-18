/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.record;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.protocol.record.RecordMetadata;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;

public class RecordMetadataImpl implements RecordMetadata {
  private int partitionId;
  @JsonDeserialize(using = StringToIntentSerializer.class)
  private Intent intent;
  private RecordType recordType;
  private RejectionType rejectionType;
  private String rejectionReason;
  private ValueType valueType;

  public RecordMetadataImpl() {
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public Intent getIntent() {
    return intent;
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
  public ValueType getValueType() {
    return valueType;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public void setIntent(Intent intent) {
    this.intent = intent;
  }

  public void setRecordType(RecordType recordType) {
    this.recordType = recordType;
  }

  public void setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void setValueType(ValueType valueType) {
    this.valueType = valueType;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public String toString() {
    return "RecordMetadataImpl{"
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
        + ", valueType="
        + valueType
        + '}';
  }
}
