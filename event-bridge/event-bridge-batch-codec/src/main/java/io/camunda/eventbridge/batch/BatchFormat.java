/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.batch;

/**
 * The single source of truth for the EventBridge batch wire format: field offsets, sizes, the
 * version, attribute masks, and the derived size/CRC math. Both the broker's zero-copy Agrona
 * accessors (event-bridge-protocol) and the pure {@link BatchBuilder}/{@link BatchReader} read the
 * layout from here, so there is exactly one definition of the format.
 *
 * <h3>Batch layout (v1) — all multi-byte fields LITTLE-ENDIAN</h3>
 *
 * <pre>
 * offset size field        patched?
 * 0      8    position     yes (broker assigns; producer sends 0)
 * 8      4    batchLength   no (bytes after this field)
 * 12     4    version       no
 * 16     8    timestamp    yes (broker append time; producer sends 0)
 * 24     4    crc           no (CRC-32C of [28..end))
 * 28     4    attributes    no (bit flags)
 * 32     4    entryCount    no
 * 36     4    reserved      no (must be 0)
 * 40     var  entries...    no ([entryLength(4)][keyLength(4)][key][value]) × entryCount
 * </pre>
 *
 * <p>The CRC covers bytes from {@code attributes} (offset 28) to the end of the batch; position,
 * batchLength, version, timestamp and the crc field itself are excluded so the broker can patch
 * position/timestamp without recomputing it.
 */
public final class BatchFormat {

  /** Current format version. */
  public static final int VERSION_1 = 1;

  // -- Batch header field offsets (naturally aligned) --
  public static final int POSITION_OFFSET = 0;
  public static final int BATCH_LENGTH_OFFSET = 8;
  public static final int VERSION_OFFSET = 12;
  public static final int TIMESTAMP_OFFSET = 16;
  public static final int CRC_OFFSET = 24;
  public static final int ATTRIBUTES_OFFSET = 28;
  public static final int ENTRY_COUNT_OFFSET = 32;
  public static final int RESERVED_OFFSET = 36;

  /** Batch header size in bytes; entries start immediately after. */
  public static final int HEADER_LENGTH = 40;

  // -- Attribute bit masks: compression codec in bits 0-2 --
  public static final int COMPRESSION_MASK = 0x07;
  public static final int COMPRESSION_NONE = 0;
  public static final int COMPRESSION_LZ4 = 1;
  public static final int COMPRESSION_ZSTD = 2;
  public static final int COMPRESSION_SNAPPY = 3;

  // -- Entry layout: [entryLength(4)][keyLength(4)][key][value] --
  public static final int ENTRY_LENGTH_SIZE = Integer.BYTES;
  public static final int KEY_LENGTH_SIZE = Integer.BYTES;
  public static final int ENTRY_HEADER_SIZE = ENTRY_LENGTH_SIZE + KEY_LENGTH_SIZE;

  /** Minimum valid entryLength — must at least contain the keyLength field. */
  public static final int MIN_ENTRY_LENGTH = KEY_LENGTH_SIZE;

  /** Bytes before the batchLength payload (position + the batchLength field itself). */
  private static final int BYTES_BEFORE_BATCH_LENGTH_PAYLOAD = BATCH_LENGTH_OFFSET + Integer.BYTES;

  private BatchFormat() {}

  /** The {@code batchLength} field value for the given total entry bytes. */
  public static int batchLengthValue(final int entriesLength) {
    return (HEADER_LENGTH - BYTES_BEFORE_BATCH_LENGTH_PAYLOAD) + entriesLength;
  }

  /** Total batch size (full header + entries) for the given {@code batchLength} field value. */
  public static int totalSize(final int batchLengthFieldValue) {
    return BYTES_BEFORE_BATCH_LENGTH_PAYLOAD + batchLengthFieldValue;
  }

  /** Length of the CRC-covered region given the {@code batchLength} field value. */
  public static int crcLength(final int batchLengthFieldValue) {
    return batchLengthFieldValue - (ATTRIBUTES_OFFSET - VERSION_OFFSET);
  }

  /** Total on-wire size of an entry with the given key/value byte lengths. */
  public static int entryTotalSize(final int keyLength, final int valueLength) {
    return ENTRY_HEADER_SIZE + keyLength + valueLength;
  }

  // -- Little-endian byte[] helpers (the format's byte order) --

  public static int getIntLE(final byte[] b, final int off) {
    return (b[off] & 0xFF)
        | ((b[off + 1] & 0xFF) << 8)
        | ((b[off + 2] & 0xFF) << 16)
        | ((b[off + 3] & 0xFF) << 24);
  }

  public static long getLongLE(final byte[] b, final int off) {
    return (getIntLE(b, off) & 0xFFFFFFFFL) | ((long) getIntLE(b, off + 4) << 32);
  }

  public static void putIntLE(final byte[] b, final int off, final int v) {
    b[off] = (byte) v;
    b[off + 1] = (byte) (v >>> 8);
    b[off + 2] = (byte) (v >>> 16);
    b[off + 3] = (byte) (v >>> 24);
  }

  public static void putLongLE(final byte[] b, final int off, final long v) {
    putIntLE(b, off, (int) v);
    putIntLE(b, off + 4, (int) (v >>> 32));
  }
}
