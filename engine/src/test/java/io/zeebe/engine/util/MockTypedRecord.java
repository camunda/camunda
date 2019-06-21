/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.util;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import java.time.Instant;

public class MockTypedRecord<T extends UnifiedRecordValue> implements TypedRecord<T> {

  private long key;
  private RecordMetadata metadata;
  private T value;

  public MockTypedRecord() {}

  public MockTypedRecord(long key, RecordMetadata metadata, T value) {
    this.key = key;
    this.metadata = metadata;
    this.value = value;
  }

  @Override
  public long getKey() {
    return key;
  }

  public void setKey(long key) {
    this.key = key;
  }

  public void setMetadata(RecordMetadata metadata) {
    this.metadata = metadata;
  }

  @Override
  public T getValue() {
    return value;
  }

  @Override
  public int getRequestStreamId() {
    return metadata.getRequestStreamId();
  }

  @Override
  public long getRequestId() {
    return metadata.getRequestId();
  }

  public void setValue(T value) {
    this.value = value;
  }

  @Override
  public long getPosition() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public long getSourceRecordPosition() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Instant getTimestamp() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Intent getIntent() {
    return metadata.getIntent();
  }

  @Override
  public int getPartitionId() {
    return Protocol.decodePartitionId(key);
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
  public ValueType getValueType() {
    return metadata.getValueType();
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("not yet implemented");
  }
}
