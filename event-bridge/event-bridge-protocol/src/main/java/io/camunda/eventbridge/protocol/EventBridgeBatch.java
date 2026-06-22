/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Binary format of an EventBridge batch. One format used everywhere: producer memory, wire, journal
 * segment, consumer. The producer creates the batch in this format. The broker patches two fields
 * (position and timestamp) and copies the bytes into the journal as-is. The consumer reads the
 * bytes directly — no deserialization, no re-framing.
 *
 * <h3>Layout (v1)</h3>
 *
 * <pre>
 * offset  size  field         alignment  CRC'd?  patched?
 * ──────  ────  ─────         ─────────  ──────  ────────
 * 0       8     position      8-byte     no      yes       log position (producer sends 0)
 * 8       4     batchLength   4-byte     no      no        bytes after this field
 * 12      4     version       4-byte     no      no        format version (1)
 * 16      8     timestamp     8-byte     no      yes       broker append time (producer sends 0)
 * 24      4     crc           4-byte     no      no        CRC-32C of [28..end)
 * 28      4     attributes    4-byte     yes     no        bit flags (compression, future)
 * 32      4     entryCount    4-byte     yes     no        number of records
 * 36      4     reserved      4-byte     yes     no        must be 0 (future use)
 * 40      var   entries...    —          yes     no        [len(4)][data] × entryCount
 * </pre>
 *
 * <h3>Field semantics</h3>
 *
 * <ul>
 *   <li>{@code position} — log position of the first record. Producer sends 0; the broker assigns
 *       the actual position during append. First in the layout because it's the most important
 *       field when reading (index lookup, sequential scan, log recovery).
 *   <li>{@code batchLength} — total bytes after this field (header remainder + records). Total
 *       batch size = 12 + batchLength. Used to skip to the next batch when scanning.
 *   <li>{@code version} — format version. Decoders read this and select the appropriate parser.
 *       Starts at 1. Bump when the header layout changes.
 *   <li>{@code timestamp} — broker wall-clock time (millis) at append. Producer sends 0; the broker
 *       patches during append.
 *   <li>{@code crc} — CRC-32C of bytes [28..end). Covers attributes, entryCount, reserved, and all
 *       record data. Excludes position, batchLength, version, timestamp, and crc itself. Position
 *       and timestamp are excluded because the broker patches them after the producer computes the
 *       CRC — this avoids CRC recomputation on the broker's hot path.
 *   <li>{@code attributes} — bit flags. Bits 0-2: compression codec (0=none, 1=lz4, 2=zstd,
 *       3=snappy). Bits 3-31: reserved (must be 0).
 *   <li>{@code entryCount} — number of records in this batch. The broker uses this to advance the
 *       position counter.
 *   <li>{@code reserved} — must be 0. Available for future fields without a version bump if the
 *       field fits in 4 bytes and zero is a safe default.
 * </ul>
 *
 * <h3>Natural alignment</h3>
 *
 * <p>All multi-byte fields are naturally aligned: int32 at offsets divisible by 4, int64 at offsets
 * divisible by 8. This avoids split loads on the CPU without requiring padding between batches.
 *
 * <h3>Byte order</h3>
 *
 * <p>All multi-byte fields use the byte order of the underlying {@link DirectBuffer} (typically
 * native/little-endian on x86 with Agrona). This matches Agrona's default behavior.
 *
 * <h3>Versioning</h3>
 *
 * <p>To evolve the format:
 *
 * <ol>
 *   <li>Use the {@code reserved} field first (no version bump needed if zero is a safe default)
 *   <li>Append new fields after {@code reserved} and bump version (e.g., v2 header = 48+ bytes)
 *   <li>Never change existing field offsets — new fields are always appended
 *   <li>The decoder reads version first and selects the header length accordingly
 *   <li>Old data on disk with version=1 remains readable by newer decoders
 * </ol>
 */
public final class EventBridgeBatch {

  public static final int VERSION_1 = 1;

  // -- Current format version --
  public static final int POSITION_OFFSET = 0; // int64, 0 % 8 = 0 ✓

  // -- Header field offsets (naturally aligned) --
  public static final int BATCH_LENGTH_OFFSET = 8; // int32, 8 % 4 = 0 ✓
  public static final int VERSION_OFFSET = 12; // int32, 12 % 4 = 0 ✓
  public static final int TIMESTAMP_OFFSET = 16; // int64, 16 % 8 = 0 ✓
  public static final int CRC_OFFSET = 24; // int32, 24 % 4 = 0 ✓
  public static final int ATTRIBUTES_OFFSET = 28; // int32, 28 % 4 = 0 ✓
  public static final int ENTRY_COUNT_OFFSET = 32; // int32, 32 % 4 = 0 ✓
  public static final int RESERVED_OFFSET = 36; // int32, 36 % 4 = 0 ✓

  /** Header size in bytes. Records start immediately after. */
  public static final int HEADER_LENGTH = 40;

  // -- Attribute bit masks: compression codec in bits 0-2 --
  public static final int COMPRESSION_MASK = 0x07; // bits 0-2
  public static final int COMPRESSION_NONE = 0;
  public static final int COMPRESSION_LZ4 = 1;
  public static final int COMPRESSION_ZSTD = 2;
  public static final int COMPRESSION_SNAPPY = 3;

  // -- CRC range: from ATTRIBUTES_OFFSET to end of batch --
  //
  // Covered:   attributes, entryCount, reserved, records
  // Excluded:  position, batchLength, version, timestamp, crc
  //
  // Position and timestamp are excluded because the broker patches them
  // after the producer computes the CRC. This avoids CRC recomputation
  // on the broker's hot path.

  /** Number of bytes before the batchLength field (position + batchLength field itself). */
  private static final int BYTES_BEFORE_BATCH_LENGTH_PAYLOAD = BATCH_LENGTH_OFFSET + Integer.BYTES;

  private static final int CRC_DATA_START = ATTRIBUTES_OFFSET;

  private EventBridgeBatch() {}

  // -- Size computation --

  /**
   * Computes the value of the {@code batchLength} field given the total size of all record bytes.
   * {@code batchLength} = header bytes after the batchLength field + record bytes.
   *
   * @param entriesLength total byte length of all records (without header)
   * @return the value to write into the batchLength field
   */
  public static int batchLengthValue(final int entriesLength) {
    // batchLength = bytes after the batchLength field
    // = (HEADER_LENGTH - BYTES_BEFORE_BATCH_LENGTH_PAYLOAD) + entriesLength
    return (HEADER_LENGTH - BYTES_BEFORE_BATCH_LENGTH_PAYLOAD) + entriesLength;
  }

  /**
   * Computes the total byte size of a batch (full header + records). This is the number of bytes
   * the batch occupies in the journal segment.
   *
   * @param batchLengthFieldValue the value of the batchLength field
   * @return total batch size in bytes
   */
  public static int totalSize(final int batchLengthFieldValue) {
    return BYTES_BEFORE_BATCH_LENGTH_PAYLOAD + batchLengthFieldValue;
  }

  /**
   * Returns the offset where record entries begin, relative to the batch start.
   *
   * @param batchOffset offset of the batch within the buffer
   * @return absolute offset of the first record entry
   */
  public static int entriesOffset(final int batchOffset) {
    return batchOffset + HEADER_LENGTH;
  }

  // -- Write (producer side) --

  /**
   * Writes the full batch header. Called by the producer when constructing a batch. The producer
   * sets position and timestamp to 0 — the broker patches them during append.
   *
   * @param buffer target buffer
   * @param offset batch start offset within the buffer
   * @param entriesLength total byte length of all record entries (after header)
   * @param attributes bit flags (compression codec in bits 0-2)
   * @param entryCount number of records in the batch
   */
  public static void writeHeader(
      final MutableDirectBuffer buffer,
      final int offset,
      final int entriesLength,
      final int attributes,
      final int entryCount) {
    buffer.putLong(offset + POSITION_OFFSET, 0L); // broker patches
    buffer.putInt(offset + BATCH_LENGTH_OFFSET, batchLengthValue(entriesLength));
    buffer.putInt(offset + VERSION_OFFSET, VERSION_1);
    buffer.putLong(offset + TIMESTAMP_OFFSET, 0L); // broker patches
    buffer.putInt(offset + CRC_OFFSET, 0); // computed after records
    buffer.putInt(offset + ATTRIBUTES_OFFSET, attributes);
    buffer.putInt(offset + ENTRY_COUNT_OFFSET, entryCount);
    buffer.putInt(offset + RESERVED_OFFSET, 0);
  }

  /**
   * Computes and writes the CRC. Called by the producer after the header and all records have been
   * written to the buffer. CRC covers bytes from attributes to end of batch.
   *
   * @param buffer the buffer containing the fully written batch
   * @param batchOffset batch start offset within the buffer
   */
  public static void writeCrc(final MutableDirectBuffer buffer, final int batchOffset) {
    final int batchLength = buffer.getInt(batchOffset + BATCH_LENGTH_OFFSET);
    final int crc = computeCrc(buffer, batchOffset, batchLength);
    buffer.putInt(batchOffset + CRC_OFFSET, crc);
  }

  // -- Patch (broker side) --

  /**
   * Patches the position field. Called by the broker during the copy-and-patch write. Does not
   * invalidate the CRC (position is outside the CRC range).
   */
  public static void patchPosition(
      final MutableDirectBuffer buffer, final int batchOffset, final long position) {
    buffer.putLong(batchOffset + POSITION_OFFSET, position);
  }

  /**
   * Patches the timestamp field. Called by the broker during the copy-and-patch write. Does not
   * invalidate the CRC (timestamp is outside the CRC range).
   */
  public static void patchTimestamp(
      final MutableDirectBuffer buffer, final int batchOffset, final long timestamp) {
    buffer.putLong(batchOffset + TIMESTAMP_OFFSET, timestamp);
  }

  // -- CRC computation and validation --

  /**
   * Computes the CRC-32C of the CRC-covered region. CRC covers bytes from attributes offset to end
   * of batch: attributes, entryCount, reserved, and all record bytes.
   *
   * @param buffer buffer containing the batch
   * @param batchOffset batch start offset within the buffer
   * @param batchLength value of the batchLength field
   * @return CRC-32C checksum
   */
  public static int computeCrc(
      final DirectBuffer buffer, final int batchOffset, final int batchLength) {

    final var crc = new CRC32C();
    final int crcStart = batchOffset + ATTRIBUTES_OFFSET;
    final int crcLength = batchLength - (ATTRIBUTES_OFFSET - VERSION_OFFSET);

    final ByteBuffer nioBuffer = buffer.byteBuffer();
    if (nioBuffer != null) {
      // FAST PATH: Off-heap memory
      final ByteBuffer slice = nioBuffer.duplicate();
      slice.position(buffer.wrapAdjustment() + crcStart);
      slice.limit(buffer.wrapAdjustment() + crcStart + crcLength);
      crc.update(slice);
    } else {
      // FAST PATH: On-heap array
      crc.update(buffer.byteArray(), buffer.wrapAdjustment() + crcStart, crcLength);
    }

    return (int) crc.getValue();
  }

  /**
   * Validates the CRC of a batch. Recomputes the CRC over the covered region and compares with the
   * stored value. Called by the broker before appending to the journal.
   *
   * @param buffer buffer containing the batch
   * @param batchOffset batch start offset within the buffer
   * @return {@code true} if CRC matches, {@code false} if corrupted
   */
  public static boolean validateCrc(final DirectBuffer buffer, final int batchOffset) {
    final int batchLength = buffer.getInt(batchOffset + BATCH_LENGTH_OFFSET);
    final int storedCrc = buffer.getInt(batchOffset + CRC_OFFSET);
    final int computedCrc = computeCrc(buffer, batchOffset, batchLength);
    return storedCrc == computedCrc;
  }

  // -- Read --

  /** Reads the position field (log position of the first record). */
  public static long getPosition(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getLong(batchOffset + POSITION_OFFSET);
  }

  /** Reads the batchLength field (bytes after the batchLength field). */
  public static int getBatchLength(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getInt(batchOffset + BATCH_LENGTH_OFFSET);
  }

  /** Reads the version (format version) field. */
  public static int getVersion(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getInt(batchOffset + VERSION_OFFSET);
  }

  /** Reads the timestamp field (broker append time in millis). */
  public static long getTimestamp(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getLong(batchOffset + TIMESTAMP_OFFSET);
  }

  /** Reads the CRC field. */
  public static int getCrc(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getInt(batchOffset + CRC_OFFSET);
  }

  /** Reads the attributes field (bit flags). */
  public static int getAttributes(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getInt(batchOffset + ATTRIBUTES_OFFSET);
  }

  /** Reads the entryCount field (number of records in this batch). */
  public static int getEntryCount(final DirectBuffer buffer, final int batchOffset) {
    return buffer.getInt(batchOffset + ENTRY_COUNT_OFFSET);
  }

  /** Extracts the compression codec from the attributes field. */
  public static int compressionCodec(final int attributes) {
    return attributes & COMPRESSION_MASK;
  }
}
