/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.batch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/**
 * Pure-JDK builder for an EventBridge batch — the producer side of the wire format, with no Agrona
 * dependency. Accumulates entries, then {@link #build()} emits the complete {@code byte[]} (header
 * + entries + CRC) with {@code position}/{@code timestamp} left at 0 for the broker to patch.
 *
 * <p>Not thread-safe. Produces the exact same bytes the broker reads back through its Agrona
 * accessors, since both sides derive the layout from {@link BatchFormat}.
 */
public final class BatchBuilder {

  /** Shared empty key for keyless entries — mirrors {@code BatchReader.NO_KEY}. */
  private static final byte[] NO_KEY = new byte[0];

  private record Entry(byte[] key, byte[] value) {}

  private final List<Entry> entries = new ArrayList<>();
  private int entriesLength;
  private int attributes;

  /** Sets the compression codec (bits 0-2 of attributes). Default is no compression. */
  public BatchBuilder compression(final int codec) {
    attributes =
        (attributes & ~BatchFormat.COMPRESSION_MASK) | (codec & BatchFormat.COMPRESSION_MASK);
    return this;
  }

  /** Adds an entry with a UTF-8 key. */
  public BatchBuilder add(final String key, final byte[] value) {
    return add(key == null ? null : key.getBytes(StandardCharsets.UTF_8), value);
  }

  /** Adds an entry with no key. */
  public BatchBuilder add(final byte[] value) {
    return add((byte[]) null, value);
  }

  /** Adds an entry with an optional binary key (may be {@code null} or empty for no key). */
  public BatchBuilder add(final byte[] key, final byte[] value) {
    if (value == null) {
      throw new IllegalArgumentException("Entry value must not be null");
    }
    final byte[] k = key == null ? NO_KEY : key;
    entries.add(new Entry(k, value));
    entriesLength += BatchFormat.entryTotalSize(k.length, value.length);
    return this;
  }

  public int entryCount() {
    return entries.size();
  }

  /** Total on-wire size of the batch in bytes (header + entries), as {@link #build()} emits. */
  public int sizeBytes() {
    return BatchFormat.totalSize(BatchFormat.batchLengthValue(entriesLength));
  }

  /**
   * Emits the batch as an ordered list of byte[] segments — the header, then for each entry its
   * framing, key and value — whose concatenation is byte-identical to {@link #build()}. Intended
   * for zero-copy sending (e.g. {@code HttpRequest.BodyPublishers.ofByteArrays}): the caller's
   * key/value arrays are referenced in place rather than copied into one contiguous buffer, so a
   * large payload is never duplicated on the publish path.
   *
   * <p>The returned segments reference the added key/value arrays directly; callers must not mutate
   * a published value until the send completes.
   */
  public List<byte[]> buildSegments() {
    final int batchLength = BatchFormat.batchLengthValue(entriesLength);
    final byte[] header = new byte[BatchFormat.HEADER_LENGTH];

    // Header — position and timestamp are 0; the broker patches them on append.
    BatchFormat.putLongLE(header, BatchFormat.POSITION_OFFSET, 0L);
    BatchFormat.putIntLE(header, BatchFormat.BATCH_LENGTH_OFFSET, batchLength);
    BatchFormat.putIntLE(header, BatchFormat.VERSION_OFFSET, BatchFormat.VERSION_1);
    BatchFormat.putLongLE(header, BatchFormat.TIMESTAMP_OFFSET, 0L);
    BatchFormat.putIntLE(header, BatchFormat.CRC_OFFSET, 0); // patched below
    BatchFormat.putIntLE(header, BatchFormat.ATTRIBUTES_OFFSET, attributes);
    BatchFormat.putIntLE(header, BatchFormat.ENTRY_COUNT_OFFSET, entries.size());
    BatchFormat.putIntLE(header, BatchFormat.RESERVED_OFFSET, 0);

    final List<byte[]> segments = new ArrayList<>(1 + entries.size() * 3);
    segments.add(header);

    // CRC-32C over [ATTRIBUTES_OFFSET .. end): the header tail, then every entry segment in order.
    final var crc = new CRC32C();
    crc.update(
        header,
        BatchFormat.ATTRIBUTES_OFFSET,
        BatchFormat.HEADER_LENGTH - BatchFormat.ATTRIBUTES_OFFSET);

    for (final Entry e : entries) {
      final byte[] framing = new byte[BatchFormat.ENTRY_HEADER_SIZE];
      final int entryLength = BatchFormat.KEY_LENGTH_SIZE + e.key.length + e.value.length;
      BatchFormat.putIntLE(framing, 0, entryLength);
      BatchFormat.putIntLE(framing, BatchFormat.ENTRY_LENGTH_SIZE, e.key.length);
      segments.add(framing);
      crc.update(framing);
      if (e.key.length > 0) {
        segments.add(e.key);
        crc.update(e.key);
      }
      if (e.value.length > 0) {
        segments.add(e.value);
        crc.update(e.value);
      }
    }

    BatchFormat.putIntLE(header, BatchFormat.CRC_OFFSET, (int) crc.getValue());
    return segments;
  }

  /** Emits the complete batch as a {@code byte[]}, ready to send to the broker. */
  public byte[] build() {
    final int batchLength = BatchFormat.batchLengthValue(entriesLength);
    final byte[] out = new byte[BatchFormat.totalSize(batchLength)];

    // Header — position and timestamp are 0; the broker patches them on append.
    BatchFormat.putLongLE(out, BatchFormat.POSITION_OFFSET, 0L);
    BatchFormat.putIntLE(out, BatchFormat.BATCH_LENGTH_OFFSET, batchLength);
    BatchFormat.putIntLE(out, BatchFormat.VERSION_OFFSET, BatchFormat.VERSION_1);
    BatchFormat.putLongLE(out, BatchFormat.TIMESTAMP_OFFSET, 0L);
    BatchFormat.putIntLE(out, BatchFormat.CRC_OFFSET, 0); // computed below
    BatchFormat.putIntLE(out, BatchFormat.ATTRIBUTES_OFFSET, attributes);
    BatchFormat.putIntLE(out, BatchFormat.ENTRY_COUNT_OFFSET, entries.size());
    BatchFormat.putIntLE(out, BatchFormat.RESERVED_OFFSET, 0);

    // Entries: [entryLength(4)][keyLength(4)][key][value]
    int pos = BatchFormat.HEADER_LENGTH;
    for (final Entry e : entries) {
      final int entryLength = BatchFormat.KEY_LENGTH_SIZE + e.key.length + e.value.length;
      BatchFormat.putIntLE(out, pos, entryLength);
      pos += BatchFormat.ENTRY_LENGTH_SIZE;
      BatchFormat.putIntLE(out, pos, e.key.length);
      pos += BatchFormat.KEY_LENGTH_SIZE;
      System.arraycopy(e.key, 0, out, pos, e.key.length);
      pos += e.key.length;
      System.arraycopy(e.value, 0, out, pos, e.value.length);
      pos += e.value.length;
    }

    // CRC-32C over [ATTRIBUTES_OFFSET .. end).
    final var crc = new CRC32C();
    crc.update(out, BatchFormat.ATTRIBUTES_OFFSET, BatchFormat.crcLength(batchLength));
    BatchFormat.putIntLE(out, BatchFormat.CRC_OFFSET, (int) crc.getValue());

    return out;
  }
}
