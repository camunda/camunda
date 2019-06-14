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
package io.zeebe.engine.processor;

import static io.zeebe.engine.processor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.util.ReflectUtil;
import java.time.Instant;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class CopiedTypedEvent extends TypedEventImpl {
  private final long key;
  private final long position;
  private final long sourcePosition;
  private final long timestamp;

  public CopiedTypedEvent(LoggedEvent event, UnifiedRecordValue object) {
    this.value = object;
    this.key = event.getKey();
    this.position = event.getPosition();
    this.sourcePosition = event.getSourceEventPosition();
    this.metadata = new RecordMetadata();
    this.timestamp = event.getTimestamp();
    event.readMetadata(metadata);
    event.readValue(object);
  }

  private CopiedTypedEvent(
      UnifiedRecordValue object,
      RecordMetadata recordMetadata,
      long key,
      long position,
      long sourcePosition,
      long timestamp) {
    this.metadata = recordMetadata;
    this.value = object;
    this.key = key;
    this.position = position;
    this.sourcePosition = sourcePosition;
    this.timestamp = timestamp;
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

  @Override
  public Instant getTimestamp() {
    return Instant.ofEpochMilli(timestamp);
  }

  @Override
  public RecordMetadata getMetadata() {
    return metadata;
  }

  public static <T extends UnifiedRecordValue> TypedRecord<T> toTypedEvent(
      LoggedEvent event, Class<T> valueClass) {
    final T value = ReflectUtil.newInstance(valueClass);
    return new CopiedTypedEvent(event, value);
  }

  public static CopiedTypedEvent createCopiedEvent(LoggedEvent rawEvent) {
    // we have to access the underlying buffer and copy the metadata and value bytes
    // otherwise next event will overwrite the event before, since UnpackedObject
    // and RecordMetadata has properties (buffers, StringProperty etc.) which only wraps the given
    // buffer instead of copying it

    final DirectBuffer contentBuffer = rawEvent.getValueBuffer();

    final byte[] metadataBytes = new byte[rawEvent.getMetadataLength()];
    contentBuffer.getBytes(rawEvent.getMetadataOffset(), metadataBytes);
    final DirectBuffer metadataBuffer = new UnsafeBuffer(metadataBytes);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.wrap(metadataBuffer, 0, metadataBuffer.capacity());

    final byte[] valueBytes = new byte[rawEvent.getValueLength()];
    contentBuffer.getBytes(rawEvent.getValueOffset(), valueBytes);
    final DirectBuffer valueBuffer = new UnsafeBuffer(valueBytes);

    final UnifiedRecordValue recordValue =
        ReflectUtil.newInstance(EVENT_REGISTRY.get(metadata.getValueType()));
    recordValue.wrap(valueBuffer);

    return new CopiedTypedEvent(
        recordValue,
        metadata,
        rawEvent.getKey(),
        rawEvent.getPosition(),
        rawEvent.getSourceEventPosition(),
        rawEvent.getTimestamp());
  }
}
