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
import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.logstreams.log.LoggedEvent;
import java.time.Instant;

public class RecordImpl<T extends RecordValue> implements Record<T> {
  private final long key;
  private final long position;
  private final Instant timestamp;
  private final int producerId;
  private final long sourceRecordPosition;

  private final RecordMetadata metadata;
  private final T value;

  @JsonIgnore private final ExporterObjectMapper objectMapper;

  public RecordImpl(
      ExporterObjectMapper objectMapper,
      long key,
      long position,
      Instant timestamp,
      int producerId,
      long sourceRecordPosition,
      RecordMetadata metadata,
      T value) {
    this.objectMapper = objectMapper;
    this.key = key;
    this.position = position;
    this.timestamp = timestamp;
    this.producerId = producerId;
    this.sourceRecordPosition = sourceRecordPosition;
    this.metadata = metadata;
    this.value = value;
  }

  public static <U extends RecordValue> RecordImpl<U> ofLoggedEvent(
      final ExporterObjectMapper objectMapper,
      final LoggedEvent event,
      final RecordMetadataImpl metadata,
      final U value) {
    return new RecordImpl<>(
        objectMapper,
        event.getKey(),
        event.getPosition(),
        Instant.ofEpochMilli(event.getTimestamp()),
        event.getProducerId(),
        event.getSourceEventPosition(),
        metadata,
        value);
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
  public T getValue() {
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
        + value
        + '}';
  }
}
