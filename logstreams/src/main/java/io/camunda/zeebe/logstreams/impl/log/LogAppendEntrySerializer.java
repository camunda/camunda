/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setKey;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setMetadataLength;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setTimestamp;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;

/**
 * Temporary central point to serialize log entries to the dispatcher.
 *
 * <p>TODO: once we get rid of the dispatcher, we need to maintain backwards compatibility by adding
 * some padded framing around each entry, unfortunately.
 */
final class LogAppendEntrySerializer {

  /**
   * Serializes an entry into the given destination buffer. Returns the length of the serialized
   * entry, unframed and unaligned.
   *
   * @param writeBuffer the buffer to serialize into
   * @param writeBufferOffset the offset to start serializing at in the {@code writeBuffer}
   * @param entry the entry to serialize
   * @param position the position of the entry
   * @param sourcePosition the source record position of the entry
   * @param entryTimestamp the timestamp; useful to pass the same for a complete batch, for example
   * @return the length of the serialized entry, unframed/unaligned
   * @throws IllegalArgumentException if the entry's value is empty, i.e. has a length of 0
   */
  int serialize(
      final MutableDirectBuffer writeBuffer,
      final int writeBufferOffset,
      final LogAppendEntry entry,
      final long position,
      final long sourcePosition,
      final long entryTimestamp) {
    Objects.requireNonNull(writeBuffer, "must specify a destination buffer");
    Objects.requireNonNull(entry, "must specify an entry");

    final var key = entry.key();
    final var metadata = entry.recordMetadata();
    final var value = entry.recordValue();
    final var metadataLength = metadata.getLength();

    if (writeBufferOffset < 0) {
      throw new IllegalArgumentException(
          "Expected to serialize entry at a positive offset, but the offset given was %d"
              .formatted(writeBufferOffset));
    }

    if (value.getLength() == 0) {
      throw new IllegalArgumentException(
          "Expected to serialize an entry with a value, but the entry's value reports a length of 0");
    }

    if (position < 0) {
      throw new IllegalArgumentException(
          "Expected to serialize an entry with a positive position, but the position given was %d"
              .formatted(position));
    }

    if (entryTimestamp < 0) {
      throw new IllegalArgumentException(
          "Expected to serialize an entry with a positive timestamp, but the timestamp given was %d"
              .formatted(entryTimestamp));
    }

    setPosition(writeBuffer, writeBufferOffset, position);
    setSourceEventPosition(writeBuffer, writeBufferOffset, sourcePosition);
    setKey(writeBuffer, writeBufferOffset, key);
    setTimestamp(writeBuffer, writeBufferOffset, entryTimestamp);
    writeMetadata(writeBuffer, writeBufferOffset, metadata, metadataLength);
    value.write(writeBuffer, valueOffset(writeBufferOffset, metadataLength));

    return LogEntryDescriptor.headerLength(metadataLength) + value.getLength();
  }

  private void writeMetadata(
      final MutableDirectBuffer writeBuffer,
      final int writeBufferOffset,
      final BufferWriter metadata,
      final int metadataLength) {
    setMetadataLength(writeBuffer, writeBufferOffset, (short) metadataLength);
    if (metadataLength > 0) {
      metadata.write(writeBuffer, metadataOffset(writeBufferOffset));
    }
  }
}
