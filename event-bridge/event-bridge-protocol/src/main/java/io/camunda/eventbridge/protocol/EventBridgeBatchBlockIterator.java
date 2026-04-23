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
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Iterates over multiple EventBridge batches contained within a single opaque data block (e.g., a
 * Raft application entry).
 *
 * <p><b>Zero Allocation:</b> This iterator maintains primitive state and yields the offsets and
 * lengths of each batch without allocating objects on the hot path. It is designed to be used
 * directly by the Journal to populate the Segment Index.
 */
public final class EventBridgeBatchBlockIterator {

  private final DirectBuffer buffer = new UnsafeBuffer(0, 0);

  private int currentBlockOffset;
  private int endOffset;

  private long currentLowestPosition;
  private long currentHighestPosition;
  private int currentBatchLength;

  /**
   * Wraps an opaque buffer containing one or more EventBridge batches.
   *
   * @param buffer The buffer containing the data (e.g., the payload of a JournalRecord).
   * @param offset The offset inside the buffer where the batches actually start (i.e., immediately
   *     after any Raft/Atomix headers).
   * @param length The total length of the batches to parse.
   */
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    this.buffer.wrap(buffer, offset, length);

    currentBlockOffset = offset;
    endOffset = offset + length;

    // Reset state
    currentLowestPosition = -1;
    currentHighestPosition = -1;
    currentBatchLength = -1;
  }

  /** Returns {@code true} if there is at least one more batch to read. */
  public boolean hasNext() {
    return currentBlockOffset + EventBridgeBatch.HEADER_LENGTH <= endOffset;
  }

  /**
   * Parses the next batch's header and advances the internal pointers. Must be called before
   * reading the current offsets or length.
   *
   * @throws NoSuchElementException if no more batches are available
   * @throws IllegalStateException if the batch extends beyond the block boundary
   */
  public void next() {
    if (!hasNext()) {
      throw new NoSuchElementException(
          "No more batches available in block. Offset: " + currentBlockOffset);
    }

    final long batchPosition = EventBridgeBatch.getPosition(buffer, currentBlockOffset);
    final int batchDataLen = EventBridgeBatch.getBatchLength(buffer, currentBlockOffset);
    final int entryCount = EventBridgeBatch.getEntryCount(buffer, currentBlockOffset);
    final int totalBatchSize = EventBridgeBatch.totalSize(batchDataLen);

    if (currentBlockOffset + totalBatchSize > endOffset) {
      throw new IllegalStateException(
          "Malformed block: batch at offset "
              + currentBlockOffset
              + " with total size "
              + totalBatchSize
              + " extends beyond block boundary "
              + endOffset);
    }

    currentLowestPosition = batchPosition;
    currentHighestPosition = batchPosition + entryCount - 1;
    currentBatchLength = totalBatchSize;
    currentBlockOffset += totalBatchSize;
  }

  public long currentLowestPosition() {
    return currentLowestPosition;
  }

  public long currentHighestPosition() {
    return currentHighestPosition;
  }

  /**
   * Returns the total byte length of the current batch (header + entries). Valid only after calling
   * {@link #next()}.
   */
  public int currentLength() {
    return currentBatchLength;
  }
}
