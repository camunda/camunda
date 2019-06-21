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
package io.zeebe.protocol.impl.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.time.Instant;

public class CopiedRecord<T extends UnifiedRecordValue> implements Record<T> {

  private final T recordValue;

  private final long key;
  private final long position;
  private final long sourcePosition;
  private final long timestamp;

  private final RecordType recordType;
  private final Intent intent;
  private final int partitionId;
  protected ValueType valueType;
  private final RejectionType rejectionType;
  private final String rejectionReason;

  public CopiedRecord(
      T recordValue,
      RecordMetadata metadata,
      long key,
      int partitionId,
      long position,
      long sourcePosition,
      long timestamp) {
    this.recordValue = recordValue;
    this.key = key;
    this.position = position;
    this.sourcePosition = sourcePosition;
    this.timestamp = timestamp;

    this.intent = metadata.getIntent();
    this.recordType = metadata.getRecordType();
    this.partitionId = partitionId;
    this.rejectionType = metadata.getRejectionType();
    this.rejectionReason = metadata.getRejectionReason();
    this.valueType = metadata.getValueType();
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourcePosition;
  }

  @Override
  public long getKey() {
    return key;
  }

  @JsonProperty("timestamp")
  public long getTimestampLong() {
    return timestamp;
  }

  @Override
  @JsonIgnore
  public Instant getTimestamp() {
    return Instant.ofEpochMilli(timestamp);
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
  public ValueType getValueType() {
    return valueType;
  }

  @Override
  public T getValue() {
    return recordValue;
  }

  @Override
  public String toJson() {
    return MsgPackConverter.convertJsonSerializableObjectToJson(this);
  }

  @Override
  public String toString() {
    return toJson();
  }
}
