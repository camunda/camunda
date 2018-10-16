/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.record;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.gateway.api.record.RecordMetadata;
import io.zeebe.gateway.api.record.RecordType;
import io.zeebe.gateway.api.record.RejectionType;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.intent.Intent;
import java.time.Instant;

public class RecordMetadataImpl implements RecordMetadata {
  private int partitionId = ExecuteCommandRequestEncoder.partitionIdNullValue();
  private long key = ExecuteCommandRequestEncoder.keyNullValue();
  private long position = ExecuteCommandRequestEncoder.positionNullValue();
  private long sourceRecordPosition = ExecuteCommandRequestEncoder.sourceRecordPositionNullValue();
  private io.zeebe.protocol.clientapi.RecordType recordType;
  private io.zeebe.protocol.clientapi.ValueType valueType;
  private Intent intent;
  private Instant timestamp;
  private io.zeebe.protocol.clientapi.RejectionType rejectionType =
      io.zeebe.protocol.clientapi.RejectionType.NULL_VAL;
  private String rejectionReason;

  public RecordMetadataImpl() {
    // default constructor
  }

  @JsonCreator
  public RecordMetadataImpl(
      @JsonProperty("intent") final String intent,
      @JsonProperty("valueType") final io.zeebe.protocol.clientapi.ValueType valueType,
      @JsonProperty("recordType") final io.zeebe.protocol.clientapi.RecordType recordType,
      @JsonProperty("rejectionType")
          final io.zeebe.protocol.clientapi.RejectionType rejectionType) {
    // is used by Jackson to de-serialize a JSON String
    // resolve the intent from the given String and the value type
    this.valueType = valueType;
    this.recordType = recordType;
    this.intent = Intent.fromProtocolValue(valueType, intent);
    if (rejectionType != null) {
      this.rejectionType = rejectionType;
    } else {
      this.rejectionType = io.zeebe.protocol.clientapi.RejectionType.NULL_VAL;
    }
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public boolean hasPartitionId() {
    return partitionId != ExecuteCommandRequestEncoder.partitionIdNullValue();
  }

  @Override
  public long getPosition() {
    return position;
  }

  public void setPosition(final long position) {
    this.position = position;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  public void setSourceRecordPosition(final long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  @Override
  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final Instant timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public RecordType getRecordType() {
    return RecordType.valueOf(recordType.name());
  }

  @JsonIgnore
  public io.zeebe.protocol.clientapi.RecordType getProtocolRecordType() {
    return recordType;
  }

  public void setRecordType(final io.zeebe.protocol.clientapi.RecordType recordType) {
    this.recordType = recordType;
  }

  @Override
  public ValueType getValueType() {
    return ValueType.valueOf(valueType.name());
  }

  @JsonIgnore
  public io.zeebe.protocol.clientapi.ValueType getProtocolValueType() {
    return valueType;
  }

  public void setValueType(final io.zeebe.protocol.clientapi.ValueType valueType) {
    this.valueType = valueType;
  }

  @Override
  public String getIntent() {
    return intent.name();
  }

  @JsonIgnore
  public Intent getProtocolIntent() {
    return intent;
  }

  public void setIntent(final Intent intent) {
    this.intent = intent;
  }

  @Override
  public RejectionType getRejectionType() {
    if (rejectionType == io.zeebe.protocol.clientapi.RejectionType.NULL_VAL) {
      return null;
    } else {
      return RejectionType.valueOf(rejectionType.name());
    }
  }

  @JsonIgnore
  public io.zeebe.protocol.clientapi.RejectionType getProtocolRejectionType() {
    return rejectionType;
  }

  @Override
  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(final String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void setRejectionType(final io.zeebe.protocol.clientapi.RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("RecordMetadata [recordType=");
    builder.append(recordType);
    builder.append(", valueType=");
    builder.append(valueType);
    builder.append(", intent=");
    builder.append(intent);
    builder.append(", partitionId=");
    builder.append(partitionId);
    builder.append(", position=");
    builder.append(position);
    builder.append(", sourceRecordPosition=");
    builder.append(sourceRecordPosition);
    builder.append(", key=");
    builder.append(key);
    builder.append(", timestamp=");
    builder.append(timestamp);
    builder.append("]");
    return builder.toString();
  }
}
