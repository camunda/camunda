/*
 * Zeebe Broker Core
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
package org.camunda.operate.zeebeimport.record;

import java.time.Instant;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordValue;

public class RecordImpl<T extends RecordValue> implements Record<T> {
  private long key;
  private long position;
  private Instant timestamp;
  private int raftTerm;
  private int producerId;
  private long sourceRecordPosition;

  private RecordMetadataImpl metadata;
  private T value;

  public RecordImpl() {
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }

  @Override
  public int getRaftTerm() {
    return raftTerm;
  }

  @Override
  public int getProducerId() {
    return producerId;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  @Override
  public RecordMetadataImpl getMetadata() {
    return metadata;
  }

  @Override
  public T getValue() {
    return value;
  }

  public void setKey(long key) {
    this.key = key;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public void setRaftTerm(int raftTerm) {
    this.raftTerm = raftTerm;
  }

  public void setProducerId(int producerId) {
    this.producerId = producerId;
  }

  public void setSourceRecordPosition(long sourceRecordPosition) {
    this.sourceRecordPosition = sourceRecordPosition;
  }

  public void setMetadata(RecordMetadataImpl metadata) {
    this.metadata = metadata;
  }

  public void setValue(T value) {
    this.value = value;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public String toString() {
    return "RecordImpl{"
        + "key="
        + key
        + ", position="
        + position
        + ", timestamp="
        + timestamp
        + ", raftTerm="
        + raftTerm
        + ", producerId="
        + producerId
        + ", sourceRecordPosition="
        + sourceRecordPosition
        + ", metadata="
        + metadata
        + ", value="
        + value
        + '}';
  }
}
