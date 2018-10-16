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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.gateway.api.record.Record;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import java.time.Instant;

public abstract class RecordImpl implements Record {
  private final RecordMetadataImpl metadata = new RecordMetadataImpl();
  protected final ZeebeObjectMapperImpl objectMapper;

  public RecordImpl(
      final ZeebeObjectMapperImpl objectMapper,
      final RecordType recordType,
      final ValueType valueType) {
    this.metadata.setRecordType(recordType);
    this.metadata.setValueType(valueType);
    this.objectMapper = objectMapper;
  }

  public RecordImpl(final RecordImpl baseEvent, final Intent intent) {
    updateMetadata(baseEvent.metadata);
    setIntent(intent);

    this.objectMapper = baseEvent.objectMapper;
  }

  @Override
  public RecordMetadataImpl getMetadata() {
    return metadata;
  }

  public void setPartitionId(final int id) {
    this.metadata.setPartitionId(id);
  }

  @Override
  @JsonIgnore
  public long getKey() {
    return metadata.getKey();
  }

  public void setKey(final long key) {
    this.metadata.setKey(key);
  }

  public void setPosition(final long position) {
    this.metadata.setPosition(position);
  }

  public void setSourceRecordPosition(final long sourceRecordPosition) {
    this.metadata.setSourceRecordPosition(sourceRecordPosition);
  }

  public boolean hasValidPartitionId() {
    return this.metadata.hasPartitionId();
  }

  public void setTimestamp(final Instant timestamp) {
    this.metadata.setTimestamp(timestamp);
  }

  public void setTimestamp(final long timestamp) {
    setTimestamp(Instant.ofEpochMilli(timestamp));
  }

  public void setRejectioReason(final String reason) {
    this.metadata.setRejectionReason(reason);
  }

  public void setRejectionType(final RejectionType rejectionType) {
    this.metadata.setRejectionType(rejectionType);
  }

  public void updateMetadata(final RecordMetadataImpl other) {
    this.metadata.setKey(other.getKey());
    this.metadata.setPosition(other.getPosition());
    this.metadata.setPartitionId(other.getPartitionId());
    this.metadata.setRecordType(other.getProtocolRecordType());
    this.metadata.setValueType(other.getProtocolValueType());
    this.metadata.setSourceRecordPosition(other.getSourceRecordPosition());
    this.metadata.setIntent(other.getProtocolIntent());
    this.metadata.setTimestamp(other.getTimestamp());
    this.metadata.setRejectionType(other.getProtocolRejectionType());
    this.metadata.setRejectionReason(other.getRejectionReason());
  }

  @Override
  public String toJson() {
    return objectMapper.toJson(this);
  }

  public void setIntent(final Intent intent) {
    this.metadata.setIntent(intent);
  }

  @JsonIgnore
  public abstract Class<? extends RecordImpl> getEventClass();
}
