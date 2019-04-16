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
package io.zeebe.test.exporter.record;

import io.zeebe.exporter.api.record.Record;
import java.time.Instant;
import java.util.Objects;

public class MockRecord extends ExporterMappedObject implements Record, Cloneable {

  private long position = 0;
  private int raftTerm = 0;
  private long sourceRecordPosition = -1;
  private int producerId = 0;
  private long key = -1;
  private Instant timestamp = Instant.now();
  private MockRecordMetadata metadata = new MockRecordMetadata();
  private MockRecordValueWithVariables value = new MockRecordValueWithVariables();

  public MockRecord() {}

  public MockRecord(
      long position,
      int raftTerm,
      long sourceRecordPosition,
      int producerId,
      long key,
      Instant timestamp,
      MockRecordMetadata metadata,
      MockRecordValueWithVariables value) {
    this.position = position;
    this.raftTerm = raftTerm;
    this.sourceRecordPosition = sourceRecordPosition;
    this.producerId = producerId;
    this.key = key;
    this.timestamp = timestamp;
    this.metadata = metadata;
    this.value = value;
  }

  @Override
  public long getPosition() {
    return position;
  }

  public MockRecord setPosition(long position) {
    this.position = position;
    return this;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  public MockRecord setSourceRecordPosition(long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
    return this;
  }

  @Override
  public int getProducerId() {
    return producerId;
  }

  public MockRecord setProducerId(int producerId) {
    this.producerId = producerId;
    return this;
  }

  @Override
  public long getKey() {
    return key;
  }

  public MockRecord setKey(long key) {
    this.key = key;
    return this;
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  public MockRecord setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @Override
  public MockRecordMetadata getMetadata() {
    return metadata;
  }

  public MockRecord setMetadata(MockRecordMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  @Override
  public MockRecordValueWithVariables getValue() {
    return value;
  }

  public MockRecord setValue(MockRecordValueWithVariables value) {
    this.value = value;
    return this;
  }

  @Override
  public String toString() {
    return "MockRecord{"
        + "position="
        + position
        + ", raftTerm="
        + raftTerm
        + ", sourceRecordPosition="
        + sourceRecordPosition
        + ", producerId="
        + producerId
        + ", key="
        + key
        + ", timestamp="
        + timestamp
        + ", metadata="
        + metadata
        + ", value="
        + value
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MockRecord)) {
      return false;
    }

    final MockRecord record = (MockRecord) o;
    return getPosition() == record.getPosition()
        && getSourceRecordPosition() == record.getSourceRecordPosition()
        && getProducerId() == record.getProducerId()
        && getKey() == record.getKey()
        && Objects.equals(getTimestamp(), record.getTimestamp())
        && Objects.equals(getMetadata(), record.getMetadata())
        && Objects.equals(getValue(), record.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getPosition(),
        getSourceRecordPosition(),
        getProducerId(),
        getKey(),
        getTimestamp(),
        getMetadata(),
        getValue());
  }

  @Override
  public Object clone() {
    try {
      final MockRecord cloned = (MockRecord) super.clone();
      cloned.metadata = (MockRecordMetadata) metadata.clone();
      return cloned;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
