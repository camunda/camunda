/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setKey;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setMetadataLength;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.skipProcessing;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setTimestamp;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;

/** Serializes {@link LogAppendEntry}, including legacy dispatcher framing. */
final class LogAppendEntrySerializer {

  /**
   * Serializes an entry into the given destination buffer. Returns the length of the serialized
   * entry, framed but unaligned.
   *
   * @param writeBuffer the buffer to serialize into
   * @param writeBufferOffset the offset to start serializing at in the {@code writeBuffer}
   * @param entry the entry to serialize
   * @param position the position of the entry
   * @param sourcePosition the source record position of the entry
   * @param entryTimestamp the timestamp; useful to pass the same for a complete batch, for example
   * @return the length of the serialized entry, with dispatcher framing but not aligned
   * @throws IllegalArgumentException if the entry's value is empty, i.e. has a length of 0
   */
  static int serialize(
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
    Objects.requireNonNull(metadata, "must specify metadata");
    Objects.requireNonNull(value, "must specify value");

    if (writeBufferOffset < 0) {
      throw new IllegalArgumentException(
          "Expected to serialize entry at a positive offset, but the offset given was %d"
              .formatted(writeBufferOffset));
    }

    if (value.getLength() == 0) {
      throw new IllegalArgumentException(
          "Expected to serialize an entry with a value, but the entry's value reports a length of 0");
    }

    if (metadata.getLength() == 0) {
      throw new IllegalArgumentException(
          "Expected to serialize an entry with metadata, but the entry's metadata reports a length of 0");
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

    final var metadataLength = metadata.getLength();
    final var framedEntryLength = framedLength(entry);

    // Write the dispatcher framing
    DataFrameDescriptor.setFramedLength(writeBuffer, writeBufferOffset, framedEntryLength);
    final var entryOffset = writeBufferOffset + DataFrameDescriptor.HEADER_LENGTH;

    // Write the entry
    if (entry.isProcessed()) {
      skipProcessing(writeBuffer, entryOffset);
    }
    setPosition(writeBuffer, entryOffset, position);
    setSourceEventPosition(writeBuffer, entryOffset, sourcePosition);
    setKey(writeBuffer, entryOffset, key);
    setTimestamp(writeBuffer, entryOffset, entryTimestamp);
    setMetadataLength(writeBuffer, entryOffset, (short) metadataLength);
    metadata.write(writeBuffer, metadataOffset(entryOffset));
    value.write(writeBuffer, valueOffset(entryOffset, metadataLength));

    return framedEntryLength;
  }

  static int framedLength(final LogAppendEntry entry) {
    return DataFrameDescriptor.framedLength(
        LogEntryDescriptor.headerLength(entry.recordMetadata().getLength())
            + entry.recordValue().getLength());
  }
}
