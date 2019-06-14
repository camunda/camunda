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
package io.zeebe.broker.exporter.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordMetadata;
import io.zeebe.protocol.record.RecordValue;
import java.time.Instant;
import java.util.function.Supplier;

public class RecordImpl<T extends RecordValue> implements Record<T> {
  private final long key;
  private final long position;
  private final Instant timestamp;
  private final int producerId;
  private final long sourceRecordPosition;

  private final RecordMetadata metadata;

  @JsonIgnore private final Supplier<T> valueSupplier;
  @JsonIgnore private T value = null;

  @JsonIgnore private final ExporterObjectMapper objectMapper;

  public RecordImpl(
      ExporterObjectMapper objectMapper,
      long key,
      long position,
      Instant timestamp,
      int producerId,
      long sourceRecordPosition,
      RecordMetadata metadata,
      Supplier<T> valueSupplier) {
    this.objectMapper = objectMapper;
    this.key = key;
    this.position = position;
    this.timestamp = timestamp;
    this.producerId = producerId;
    this.sourceRecordPosition = sourceRecordPosition;
    this.metadata = metadata;
    this.valueSupplier = valueSupplier;
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
  public int getProducerId() {
    return producerId;
  }

  @Override
  public long getSourceRecordPosition() {
    return sourceRecordPosition;
  }

  @Override
  public RecordMetadata getMetadata() {
    return metadata;
  }

  @Override
  @JsonProperty
  public T getValue() {
    if (value == null) {
      value = valueSupplier.get();
    }
    return value;
  }

  public ExporterObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public String toJson() {
    return objectMapper.toJson(this);
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
        + ", producerId="
        + producerId
        + ", sourceRecordPosition="
        + sourceRecordPosition
        + ", metadata="
        + metadata
        + ", value="
        + getValue()
        + '}';
  }
}
