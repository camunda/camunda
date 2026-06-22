/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;

/**
 * Iterates over entries within an EventBridge batch. Reusable — call {@link #wrap} or {@link
 * #wrapEntries} to iterate a new batch.
 *
 * <p><b>Entry reuse:</b> {@link #next()} returns the same {@link EventBridgeEntry} instance on
 * every call — the entry is overwritten on each advance. Callers must consume or copy the entry
 * before calling {@link #next()} again. This avoids per-entry allocation on the hot path.
 *
 * <p><b>Skipping to an offset:</b> this iterator starts at the first entry. If the consumer
 * requested a mid-batch offset, the caller must call {@link #skipTo(long)} after wrapping to
 * advance past entries before the requested offset. The broker returns complete batches — the
 * consumer SDK handles offset-based filtering.
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

  /**
   * Wraps over a complete batch (including header). Reads position, timestamp, and entry count from
   * the header. The iterator is positioned before the first entry — call {@link #hasNext()} and
   * {@link #next()} to iterate.
   *
   * @param buffer buffer containing the batch
   * @param offset batch start offset within the buffer
   * @param length total batch size in bytes (header + entries)
   * @throws IllegalArgumentException if the batch version is not supported
   */
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final int version = EventBridgeBatch.getVersion(buffer, offset);
    if (version != EventBridgeBatch.VERSION_1) {
      throw new IllegalArgumentException(
          "Unsupported batch version: " + version + ", expected: " + EventBridgeBatch.VERSION_1);
    }

    this.buffer = buffer;
    batchPosition = EventBridgeBatch.getPosition(buffer, offset);
    batchTimestamp = EventBridgeBatch.getTimestamp(buffer, offset);
    batchEntryCount = EventBridgeBatch.getEntryCount(buffer, offset);
    currentEntryIndex = 0;
    currentEntryOffset = EventBridgeBatch.entriesOffset(offset);
    endOffset = offset + length;
  }

  /**
   * Wraps over raw entries (no batch header). Used for validation or when entries have been
   * extracted from a batch separately.
   *
   * @param buffer buffer containing the raw entries
   * @param offset start offset of the first entry
   * @param length total byte length of all entries
   * @param entryCount expected number of entries
   */
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

  /**
   * Advances the iterator past all entries with a position before {@code targetPosition}. After
   * this call, the next {@link #next()} call returns the first entry at or after the target
   * position.
   *
   * <p>Used by the consumer SDK when the broker returns a complete batch but the consumer requested
   * a mid-batch offset.
   *
   * @param targetPosition the position to skip to (inclusive)
   */
  public void skipTo(final long targetPosition) {
    while (hasNext() && (batchPosition + currentEntryIndex) < targetPosition) {
      next();
    }
  }

  /**
   * Returns {@code true} if there are more entries to iterate. Checks both the entry count and the
   * byte boundary to guard against malformed batches where entry count and data disagree.
   */
  public boolean hasNext() {
    return currentEntryIndex < batchEntryCount && currentEntryOffset < endOffset;
  }

  /**
   * Returns the next entry. The returned {@link EventBridgeEntry} is reused — its contents are
   * overwritten on each call. Callers must consume or copy the entry before calling {@code next()}
   * again.
   *
   * @throws NoSuchElementException if no more entries are available
   * @throws IllegalStateException if the entry's length field extends beyond the batch boundary
   */
  public EventBridgeEntry next() {
    if (!hasNext()) {
      throw new NoSuchElementException(
          "No more entries: index=" + currentEntryIndex + ", count=" + batchEntryCount);
    }

    // Validate that the entry length field is readable
    if (currentEntryOffset + Integer.BYTES > endOffset) {
      throw new IllegalStateException(
          "Malformed batch: entry length field at offset "
              + currentEntryOffset
              + " extends beyond batch boundary "
              + endOffset);
    }

    entry.wrap(buffer, currentEntryOffset, batchPosition, currentEntryIndex, batchTimestamp);

    // Validate that the entry data doesn't extend beyond the batch
    final int totalEntryLength = entry.getTotalLength();
    if (currentEntryOffset + totalEntryLength > endOffset) {
      throw new IllegalStateException(
          "Malformed batch: entry data at offset "
              + currentEntryOffset
              + " with length "
              + totalEntryLength
              + " extends beyond batch boundary "
              + endOffset);
    }

    currentEntryOffset += totalEntryLength;
    currentEntryIndex++;
    return entry;
  }

  /** Returns the total number of entries in the wrapped batch. */
  public int getEntryCount() {
    return batchEntryCount;
  }

  /** Returns the batch position (position of the first entry). */
  public long getBatchPosition() {
    return batchPosition;
  }

  /** Returns the batch timestamp (broker append time). */
  public long getBatchTimestamp() {
    return batchTimestamp;
  }

  /** Returns the number of entries remaining (not yet consumed by {@link #next()}). */
  public int remaining() {
    return batchEntryCount - currentEntryIndex;
  }

  /**
   * Validates the structure of raw entries. Returns the number of valid entries, or -1 if the data
   * is malformed (truncated length field, negative length, or entry extends beyond boundary).
   *
   * <p>This method is stateless — it does not modify the iterator. Use it to validate entry data
   * before wrapping with {@link #wrapEntries}.
   *
   * @param buffer buffer containing the raw entries
   * @param offset start offset of the first entry
   * @param length total byte length of all entries
   * @return number of valid entries, or -1 if malformed
   */
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
      // An entry must be at least MIN_ENTRY_LENGTH (the keyLength field) — matching what
      // EventBridgeEntry.wrap accepts. A 1..3-byte entryLength would pass a `<= 0` check here but
      // then throw in wrap, so the two validators must agree.
      if (entryLength < EventBridgeEntry.MIN_ENTRY_LENGTH || pos + entryLength > end) {
        return -1;
      }
      pos += entryLength;
      count++;
    }

    return count;
  }
}
