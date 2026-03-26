/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Describes the binary format of an EventBridge batch.
 *
 * <p>Layout:
 *
 * <pre>
 * ┌─────────────┬────────────┬───────────┬────────────┬──────────────────────┐
 * │ totalLength │ batchPos   │ timestamp │ entryCount │ entries              │
 * │ (4 bytes)   │ (8 bytes)  │ (8 bytes) │ (4 bytes)  │ [len (4b)][data] × N │
 * └─────────────┴────────────┴───────────┴────────────┴──────────────────────┘
 * </pre>
 *
 * <p>{@code totalLength} is the total byte length of the entire batch including the header. Set by
 * the client at construction time. The broker does not modify it.
 *
 * <p>{@code batchPosition} and {@code timestamp} are placeholders (0) from the client. The broker
 * patches them in-place before writing to LogStorage.
 */
public final class EventBridgeBatch {

  public static final int TOTAL_LENGTH_OFFSET = 0;
  public static final int TOTAL_LENGTH_SIZE = Integer.BYTES;

  public static final int BATCH_POSITION_SIZE = Long.BYTES;
  public static final int TIMESTAMP_SIZE = Long.BYTES;
  public static final int ENTRY_COUNT_SIZE = Integer.BYTES;
  public static final int BATCH_POSITION_OFFSET = TOTAL_LENGTH_OFFSET + TOTAL_LENGTH_SIZE;
  public static final int TIMESTAMP_OFFSET = BATCH_POSITION_OFFSET + BATCH_POSITION_SIZE;
  public static final int ENTRY_COUNT_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE;
  public static final int ENTRIES_OFFSET = ENTRY_COUNT_OFFSET + ENTRY_COUNT_SIZE;

  /** Header size in bytes: totalLength + batchPosition + timestamp + entryCount */
  public static final int HEADER_LENGTH = ENTRIES_OFFSET;

  private EventBridgeBatch() {}

  public static int batchLength(final int entriesLength) {
    return HEADER_LENGTH + entriesLength;
  }

  // --- Write ---

  public static void writeHeader(
      final MutableDirectBuffer buffer,
      final int offset,
      final int entriesLength,
      final long batchPosition,
      final long timestamp,
      final int entryCount) {
    final int totalLength = batchLength(entriesLength);
    buffer.putInt(offset + TOTAL_LENGTH_OFFSET, totalLength);
    buffer.putLong(offset + BATCH_POSITION_OFFSET, batchPosition);
    buffer.putLong(offset + TIMESTAMP_OFFSET, timestamp);
    buffer.putInt(offset + ENTRY_COUNT_OFFSET, entryCount);
  }

  // --- Patch (broker stamps position and timestamp in-place) ---

  public static void patchBatchPosition(
      final MutableDirectBuffer buffer, final int offset, final long batchPosition) {
    buffer.putLong(offset + BATCH_POSITION_OFFSET, batchPosition);
  }

  public static void patchTimestamp(
      final MutableDirectBuffer buffer, final int offset, final long timestamp) {
    buffer.putLong(offset + TIMESTAMP_OFFSET, timestamp);
  }

  // --- Read ---

  public static int getTotalLength(final DirectBuffer buffer, final int offset) {
    return buffer.getInt(offset + TOTAL_LENGTH_OFFSET);
  }

  public static long getBatchPosition(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(offset + BATCH_POSITION_OFFSET);
  }

  public static long getTimestamp(final DirectBuffer buffer, final int offset) {
    return buffer.getLong(offset + TIMESTAMP_OFFSET);
  }

  public static int getEntryCount(final DirectBuffer buffer, final int offset) {
    return buffer.getInt(offset + ENTRY_COUNT_OFFSET);
  }

  public static int entriesOffset(final int batchOffset) {
    return batchOffset + ENTRIES_OFFSET;
  }
}
