/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Builds an EventBridge batch from individual entries.
 *
 * <p>Usage:
 *
 * <pre>
 *   byte[] batch = new EventBridgeBatchBuilder()
 *       .addEntry(value1)
 *       .addEntry(value2, offset, length)
 *       .build();
 * </pre>
 *
 * <p>The builder sets all header fields at build time:
 *
 * <ul>
 *   <li>{@code position} and {@code timestamp} — set to 0 (the broker patches them during append)
 *   <li>{@code batchLength} — computed from the entries
 *   <li>{@code version} — set to {@link EventBridgeBatch#VERSION_1}
 *   <li>{@code attributes} — set via {@link #compression(int)}, default is no compression
 *   <li>{@code entryCount} — counted from {@link #addEntry} calls
 *   <li>{@code crc} — computed over the CRC-covered region (attributes, entryCount, reserved, and
 *       all entry bytes) after all entries have been written
 * </ul>
 *
 * <p>Reusable: call {@link #reset()} to clear entries and reuse the builder. The internal buffer is
 * not deallocated on reset — it retains its capacity for the next batch.
 */
public final class EventBridgeBatchBuilder {

  private final ExpandableArrayBuffer entriesBuffer = new ExpandableArrayBuffer();
  private int entriesOffset;
  private int entryCount;
  private int attributes;

  public EventBridgeBatchBuilder() {
    reset();
  }

  /**
   * Sets the compression codec for this batch. Must be called before {@link #build()}.
   *
   * @param codec one of {@link EventBridgeBatch#COMPRESSION_NONE}, {@link
   *     EventBridgeBatch#COMPRESSION_LZ4}, {@link EventBridgeBatch#COMPRESSION_ZSTD}, {@link
   *     EventBridgeBatch#COMPRESSION_SNAPPY}
   * @return this builder
   */
  public EventBridgeBatchBuilder compression(final int codec) {
    attributes =
        (attributes & ~EventBridgeBatch.COMPRESSION_MASK)
            | (codec & EventBridgeBatch.COMPRESSION_MASK);
    return this;
  }

  /**
   * Adds a pre-built entry to the batch. The entry must be a complete serialized entry as produced
   * by {@link EventBridgeEntryBuilder#build()}.
   *
   * @param entry complete serialized entry bytes (including entryLength and keyLength framing)
   * @return this builder
   */
  public EventBridgeBatchBuilder addEntry(final byte[] entry) {
    entriesBuffer.putBytes(entriesOffset, entry, 0, entry.length);
    entriesOffset += entry.length;
    entryCount++;
    return this;
  }

  /**
   * Adds a pre-built entry from a region of a byte array.
   *
   * @param entry byte array containing the serialized entry
   * @param offset offset within the array
   * @param length number of bytes
   * @return this builder
   */
  public EventBridgeBatchBuilder addEntry(final byte[] entry, final int offset, final int length) {
    entriesBuffer.putBytes(entriesOffset, entry, offset, length);
    entriesOffset += length;
    entryCount++;
    return this;
  }

  public int getEntryCount() {
    return entryCount;
  }

  public int getEntriesLength() {
    return entriesOffset;
  }

  /**
   * Builds the complete batch including header and CRC.
   *
   * <p>Steps:
   *
   * <ol>
   *   <li>Compute total size from entries length
   *   <li>Write header (position=0, timestamp=0 — broker patches these)
   *   <li>Copy entries after the header
   *   <li>Compute and write CRC over the CRC-covered region
   * </ol>
   *
   * @return the complete batch as a byte array, ready to send to the broker
   */
  public byte[] build() {
    final int batchLength = EventBridgeBatch.batchLengthValue(entriesOffset);
    final int totalSize = EventBridgeBatch.totalSize(batchLength);

    final var output = new byte[totalSize];
    final var buffer = new UnsafeBuffer(output);

    // Write header — position and timestamp are 0, broker patches them
    EventBridgeBatch.writeHeader(buffer, 0, entriesOffset, attributes, entryCount);

    // Copy entries after the header
    buffer.putBytes(EventBridgeBatch.HEADER_LENGTH, entriesBuffer, 0, entriesOffset);

    // Compute and write CRC over the covered region (attributes → end of batch).
    // Must be called AFTER header and entries are written.
    EventBridgeBatch.writeCrc(buffer, 0);

    return output;
  }

  /**
   * Resets the builder for reuse. Clears entry count and write position but retains the internal
   * buffer's allocated capacity.
   */
  public void reset() {
    entriesOffset = 0;
    entryCount = 0;
    attributes = 0;
  }
}
