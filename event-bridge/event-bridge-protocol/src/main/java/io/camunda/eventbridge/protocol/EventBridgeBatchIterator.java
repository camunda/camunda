/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import org.agrona.DirectBuffer;

/**
 * Iterates over entries within an EventBridge batch. Reusable — call {@link #wrap} or {@link
 * #wrapEntries} to iterate a new batch.
 */
public final class EventBridgeBatchIterator {

  private final EventBridgeEntry entry = new EventBridgeEntry();

  private DirectBuffer buffer;
  private long batchPosition;
  private long batchTimestamp;
  private int batchEntryCount;
  private int currentEntryIndex;
  private int currentEntryOffset;
  private int endOffset;

  /** Wraps over a complete batch (including header). */
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    this.buffer = buffer;
    batchPosition = EventBridgeBatch.getBatchPosition(buffer, offset);
    batchTimestamp = EventBridgeBatch.getTimestamp(buffer, offset);
    batchEntryCount = EventBridgeBatch.getEntryCount(buffer, offset);
    currentEntryIndex = 0;
    currentEntryOffset = EventBridgeBatch.entriesOffset(offset);
    endOffset = offset + length;
  }

  /** Wraps over raw entries (no batch header). Used for validation. */
  public void wrapEntries(
      final DirectBuffer buffer, final int offset, final int length, final int entryCount) {
    this.buffer = buffer;
    batchPosition = 0;
    batchTimestamp = 0;
    batchEntryCount = entryCount;
    currentEntryIndex = 0;
    currentEntryOffset = offset;
    endOffset = offset + length;
  }

  public boolean hasNext() {
    return currentEntryIndex < batchEntryCount && currentEntryOffset < endOffset;
  }

  public EventBridgeEntry next() {
    entry.wrap(buffer, currentEntryOffset, batchPosition, currentEntryIndex, batchTimestamp);
    currentEntryOffset += entry.getTotalLength();
    currentEntryIndex++;
    return entry;
  }

  public int getEntryCount() {
    return batchEntryCount;
  }

  /** Validates entries structure. Returns valid entry count, or -1 if malformed. */
  public static int validateEntries(final DirectBuffer buffer, final int offset, final int length) {
    int count = 0;
    int pos = offset;
    final int end = offset + length;

    while (pos < end) {
      if (pos + Integer.BYTES > end) {
        return -1;
      }
      final int entryLength = buffer.getInt(pos);
      pos += Integer.BYTES;
      if (entryLength <= 0 || pos + entryLength > end) {
        return -1;
      }
      pos += entryLength;
      count++;
    }

    return count;
  }
}
