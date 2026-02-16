/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.metadataOffset;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setKey;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setMetadataLength;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setSourceEventPosition;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setTimestamp;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.setVersion;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.skipProcessing;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.valueOffset;
import static io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor.zeroReserved;

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

    // The dispatcher framing length should be written here,
    // but we write it later on to avoid calling getLength() for metadata
    // and value
    final var entryOffset = writeBufferOffset + DataFrameDescriptor.HEADER_LENGTH;

    // Write the entry
    setVersion(writeBuffer, entryOffset);
    skipProcessing(writeBuffer, entryOffset, entry.isProcessed());
    zeroReserved(writeBuffer, entryOffset);
    setPosition(writeBuffer, entryOffset, position);
    setSourceEventPosition(writeBuffer, entryOffset, sourcePosition);
    setKey(writeBuffer, entryOffset, key);
    setTimestamp(writeBuffer, entryOffset, entryTimestamp);
    // metadataLength  should be written here, just before the metadata value,
    // but it's instead written after we wrote the metadata itself, to avoid calling
    // metadata.getLength()
    final var metadataLength = metadata.write(writeBuffer, metadataOffset(entryOffset));
    setMetadataLength(writeBuffer, entryOffset, metadataLength);
    final var valueLength = value.write(writeBuffer, valueOffset(entryOffset, metadataLength));

    final var framedEntryLength = framedLength(metadataLength, valueLength);
    // written a previous offset, see above
    DataFrameDescriptor.write(writeBuffer, writeBufferOffset, framedEntryLength);
    return framedEntryLength;
  }

  static int framedLength(final int metadataLength, final int recordValueLength) {
    return DataFrameDescriptor.framedLength(
        LogEntryDescriptor.headerLength(metadataLength) + recordValueLength);
  }

  static int framedLength(final LogAppendEntry entry) {
    return framedLength(entry.recordMetadata().getLength(), entry.recordValue().getLength());
  }
}
