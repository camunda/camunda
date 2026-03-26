/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeEntry;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Reads EventBridge entries from LogStorage. Each LogStorage block may contain multiple
 * concatenated {@link EventBridgeBatch} records (batch of batches).
 *
 * <p>The reader walks:
 *
 * <ol>
 *   <li>Blocks from LogStorage
 *   <li>Batches within each block
 *   <li>Entries within each batch
 * </ol>
 */
public final class EventStreamReader implements AutoCloseable {

  private final LogStorageReader storageReader;
  private final EventBridgeEntry currentEntry = new EventBridgeEntry();

  // Current block state
  private DirectBuffer blockBuffer = new UnsafeBuffer(0, 0);
  private int blockLength;
  private int blockOffset;

  // Current batch state
  private long batchPosition;
  private long batchTimestamp;
  private int batchEntryCount;
  private int currentEntryIndex;
  private int currentEntryOffset;

  private boolean hasNextEntry;
  private boolean initialized;

  public EventStreamReader(final LogStorageReader storageReader) {
    this.storageReader = storageReader;
    hasNextEntry = false;
    initialized = false;
    batchPosition = -1;
  }

  public void seekToFirstEvent() {
    seek(Long.MIN_VALUE);
  }

  public void seek(final long position) {
    storageReader.seek(position);
    initialized = true;
    hasNextEntry = false;

    if (!loadNextBlock()) {
      return;
    }

    // Walk blocks, batches, and entries to find the target position
    do {
      while (hasNextEntry || loadNextBatchInBlock()) {
        while (hasNextEntry) {
          if (entryPosition() >= position) {
            return;
          }
          advanceWithinBatch();
        }
      }
    } while (loadNextBlock());
  }

  public long seekToEnd() {
    batchPosition = -1;
    batchTimestamp = 0;
    batchEntryCount = 0;
    hasNextEntry = false;
    initialized = true;

    storageReader.seek(Long.MAX_VALUE);

    while (storageReader.hasNext()) {
      final var block = storageReader.next();
      if (block != null && block.capacity() >= EventBridgeBatch.HEADER_LENGTH) {
        int offset = 0;
        while (offset + EventBridgeBatch.HEADER_LENGTH <= block.capacity()) {
          final var totalLength = EventBridgeBatch.getTotalLength(block, offset);
          if (totalLength <= 0 || offset + totalLength > block.capacity()) {
            break;
          }

          batchPosition = EventBridgeBatch.getBatchPosition(block, offset);
          batchTimestamp = EventBridgeBatch.getTimestamp(block, offset);
          batchEntryCount = EventBridgeBatch.getEntryCount(block, offset);

          // Skip to next batch — O(1)
          offset += totalLength;
        }
      }
    }

    if (batchPosition < 0) {
      return -1;
    }

    return batchPosition + batchEntryCount - 1;
  }

  public boolean hasNext() {
    if (!initialized) {
      throw new IllegalStateException(
          "Reader not initialized — call seekToFirstEvent(), seek(), or seekToEnd()");
    }
    return hasNextEntry;
  }

  public EventBridgeEntry next() {
    if (!hasNextEntry) {
      throw new NoSuchElementException("No more entries");
    }

    wrapCurrentEntry();
    advanceWithinBatch();

    // If batch exhausted, try next batch in block
    if (!hasNextEntry && !loadNextBatchInBlock()) {
      // If block exhausted, try next block
      if (loadNextBlock()) {
        loadNextBatchInBlock();
      }
    }

    return currentEntry;
  }

  public long getPosition() {
    if (batchPosition < 0) {
      return -1;
    }
    return currentEntry.getPosition();
  }

  public long peekPosition() {
    if (!hasNextEntry) {
      return -1;
    }
    return entryPosition();
  }

  // --- Internal ---

  private long entryPosition() {
    return batchPosition + currentEntryIndex;
  }

  private void wrapCurrentEntry() {
    currentEntry.wrap(
        blockBuffer, currentEntryOffset, batchPosition, currentEntryIndex, batchTimestamp);
  }

  private void advanceWithinBatch() {
    currentEntryOffset += EventBridgeEntry.entryTotalLength(blockBuffer, currentEntryOffset);
    currentEntryIndex++;
    hasNextEntry = currentEntryIndex < batchEntryCount;
  }

  /** Loads the next batch within the current block. Returns false if the block is exhausted. */
  private boolean loadNextBatchInBlock() {
    if (blockOffset >= blockLength) {
      return false;
    }

    if (blockOffset + EventBridgeBatch.HEADER_LENGTH > blockLength) {
      return false;
    }

    final var totalLength = EventBridgeBatch.getTotalLength(blockBuffer, blockOffset);
    if (totalLength <= 0 || blockOffset + totalLength > blockLength) {
      hasNextEntry = false;
      return false;
    }

    batchPosition = EventBridgeBatch.getBatchPosition(blockBuffer, blockOffset);
    batchTimestamp = EventBridgeBatch.getTimestamp(blockBuffer, blockOffset);
    batchEntryCount = EventBridgeBatch.getEntryCount(blockBuffer, blockOffset);

    if (batchEntryCount <= 0) {
      hasNextEntry = false;
      return false;
    }

    currentEntryIndex = 0;
    currentEntryOffset = EventBridgeBatch.entriesOffset(blockOffset);

    // Advance block offset past this batch — O(1)
    blockOffset += totalLength;

    hasNextEntry = true;
    return true;
  }

  /** Loads the next block from LogStorage. Returns false if the log is exhausted. */
  private boolean loadNextBlock() {
    if (!storageReader.hasNext()) {
      hasNextEntry = false;
      return false;
    }

    blockBuffer = storageReader.next();

    if (blockBuffer == null || blockBuffer.capacity() < EventBridgeBatch.HEADER_LENGTH) {
      hasNextEntry = false;
      return false;
    }

    blockLength = blockBuffer.capacity();
    blockOffset = 0;

    return true;
  }

  @Override
  public void close() {
    storageReader.close();
  }
}
