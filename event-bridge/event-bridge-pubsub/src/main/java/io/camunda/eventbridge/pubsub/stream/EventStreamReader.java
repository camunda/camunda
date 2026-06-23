/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.stream;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.util.IndexScanResult;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Reads complete EventBridge batches from LogStorage. The reader is a flyweight — after calling
 * {@link #next()}, the current batch's properties are accessible via getter methods. No allocation
 * on the read path.
 *
 * <p>Each LogStorage block may contain multiple concatenated {@link EventBridgeBatch} records
 * (written by a single {@code EventStreamAppender} flush). The reader walks two levels:
 *
 * <ol>
 *   <li>Blocks from LogStorage (one per {@code logStorage.append()} call)
 *   <li>Batches within each block (one per client-submitted {@code EventBridgeBatch})
 * </ol>
 *
 * <p>Entry-level iteration within a batch is the consumer SDK's responsibility, using {@link
 * io.camunda.eventbridge.protocol.EventBridgeBatchIterator}. The broker returns complete batches —
 * never splits them.
 *
 * <p>Must be initialized via {@link #seekToFirstBatch()}, {@link #seek(long)}, or {@link
 * #seekToEnd()} before calling {@link #hasNext()} or {@link #next()}.
 *
 * <h3>Allocation-free read path</h3>
 *
 * <p>The reader holds no per-batch allocations. After {@link #next()}, batch data is accessible as
 * a region within LogStorage's own {@link DirectBuffer} via {@link #batchBuffer()}, {@link
 * #batchOffset()}, and {@link #batchTotalSize()}. Callers copy batch bytes into their response
 * buffer using {@link #copyTo(MutableDirectBuffer, int)}.
 */
public final class EventStreamReader implements AutoCloseable {

  private final LogStorageReader storageReader;

  // Current block state
  private DirectBuffer blockBuffer = new UnsafeBuffer(0, 0);
  private int blockLength;
  private int blockOffset;

  // Current batch state (set by next(), read by callers)
  private long position;
  private long timestamp;
  private int entryCount;
  private int batchLength;
  private int batchTotalSize;
  private int batchStartOffset;

  private boolean hasNext;
  private boolean initialized;

  public EventStreamReader(final LogStorageReader storageReader) {
    this.storageReader = storageReader;
    hasNext = false;
    initialized = false;
    position = -1;
  }

  // -- Seek operations --

  /** Seeks to the first batch in the log. */
  public void seekToFirstBatch() {
    seek(Long.MIN_VALUE);
  }

  /**
   * Seeks to the first batch that contains or follows the given position. If the position falls
   * within a batch (not at its start), the reader is positioned at that batch — the batch is
   * returned whole, and the consumer SDK handles entry-level skipping.
   *
   * @param position the target position (inclusive)
   */
  public void seek(final long position) {
    storageReader.seek(position);
    initialized = true;
    hasNext = false;

    if (!loadNextBlock()) {
      return;
    }

    // Walk blocks and batches to find the first batch containing or after the target position
    do {
      while (loadNextBatchInBlock()) {
        final long batchLastPosition = this.position + entryCount - 1;
        if (batchLastPosition >= position) {
          // This batch contains or follows the target position
          hasNext = true;
          return;
        }
      }
    } while (loadNextBlock());
  }

  /**
   * Seeks to the end of the log and returns the position of the last entry across all batches. Does
   * not position the reader for iteration.
   *
   * <p>Used to determine the initial position counter for the {@code EventStreamAppender}.
   *
   * @return the position of the last entry in the log, or -1 if the log is empty
   */
  public long seekToEnd() {
    position = -1;
    entryCount = 0;
    hasNext = false;
    initialized = true;

    storageReader.seek(Long.MAX_VALUE);

    while (storageReader.hasNext()) {
      final var block = storageReader.next();
      if (block == null || block.capacity() < EventBridgeBatch.HEADER_LENGTH) {
        continue;
      }

      int offset = 0;
      while (offset + EventBridgeBatch.HEADER_LENGTH <= block.capacity()) {
        final int bl = EventBridgeBatch.getBatchLength(block, offset);
        final int ts = EventBridgeBatch.totalSize(bl);

        if (bl <= 0 || offset + ts > block.capacity()) {
          break;
        }

        position = EventBridgeBatch.getPosition(block, offset);
        entryCount = EventBridgeBatch.getEntryCount(block, offset);

        offset += ts;
      }
    }

    if (position < 0) {
      return -1;
    }

    return position + entryCount - 1;
  }

  /**
   * Returns {@code true} if there is another batch to read.
   *
   * @throws IllegalStateException if the reader has not been initialized via a seek method
   */
  public boolean hasNext() {
    if (!initialized) {
      throw new IllegalStateException(
          "Reader not initialized — call seekToFirstBatch(), seek(), or seekToEnd()");
    }
    return hasNext;
  }

  /**
   * Advances to the next batch. After this call, the batch's properties are accessible via {@link
   * #position()}, {@link #timestamp()}, {@link #entryCount()}, {@link #batchTotalSize()}, {@link
   * #batchBuffer()}, and {@link #batchOffset()}.
   *
   * <p>The returned data is valid until the next call to {@link #next()} or any seek method.
   *
   * @throws NoSuchElementException if no more batches are available
   */
  public void next() {
    if (!hasNext) {
      throw new NoSuchElementException("No more batches");
    }

    // Advance: try next batch in current block, then next block
    hasNext = loadNextBatchInBlock() || (loadNextBlock() && loadNextBatchInBlock());
  }

  // -- Current batch accessors (valid after next()) --

  /** Position of the first entry in the current batch. */
  public long position() {
    return position;
  }

  /** Position of the last entry in the current batch (inclusive). */
  public long lastPosition() {
    return position + entryCount - 1;
  }

  /** Broker append timestamp of the current batch (millis since epoch). */
  public long timestamp() {
    return timestamp;
  }

  /** Number of entries in the current batch. */
  public int entryCount() {
    return entryCount;
  }

  /** Total byte size of the current batch (header + entries). */
  public int batchTotalSize() {
    return batchTotalSize;
  }

  /**
   * The buffer containing the current batch's bytes. The batch starts at {@link #batchOffset()} and
   * spans {@link #batchTotalSize()} bytes. This is a direct reference to LogStorage's internal
   * buffer — do not modify.
   */
  public DirectBuffer batchBuffer() {
    return blockBuffer;
  }

  /** Byte offset of the current batch within {@link #batchBuffer()}. */
  public int batchOffset() {
    return batchStartOffset;
  }

  /**
   * Copies the current batch's bytes into the target buffer. This is the one copy on the fetch read
   * path: LogStorage's mmap page cache → response buffer.
   *
   * @param target the destination buffer
   * @param targetOffset write position within the target buffer
   * @return number of bytes copied (same as {@link #batchTotalSize()})
   */
  public int copyTo(final MutableDirectBuffer target, final int targetOffset) {
    target.putBytes(targetOffset, blockBuffer, batchStartOffset, batchTotalSize);
    return batchTotalSize;
  }

  /**
   * Copies the current batch's bytes into the target byte array.
   *
   * @param target the destination array
   * @param targetOffset write position within the target array
   * @return number of bytes copied
   */
  public int copyTo(final byte[] target, final int targetOffset) {
    blockBuffer.getBytes(batchStartOffset, target, targetOffset, batchTotalSize);
    return batchTotalSize;
  }

  // -- Internal --

  /**
   * Loads the next batch within the current block. Sets all batch-level fields. Returns false if
   * the block is exhausted or the remaining bytes are malformed.
   */
  private boolean loadNextBatchInBlock() {
    if (blockOffset + EventBridgeBatch.HEADER_LENGTH > blockLength) {
      return false;
    }

    final int bl = EventBridgeBatch.getBatchLength(blockBuffer, blockOffset);
    final int ts = EventBridgeBatch.totalSize(bl);

    if (bl <= 0 || blockOffset + ts > blockLength) {
      return false;
    }

    final int version = EventBridgeBatch.getVersion(blockBuffer, blockOffset);
    if (version != EventBridgeBatch.VERSION_1) {
      throw new IllegalStateException(
          "Unsupported batch version "
              + version
              + " at block offset "
              + blockOffset
              + ", expected "
              + EventBridgeBatch.VERSION_1);
    }

    // Set batch state — no allocation, just field reads
    batchStartOffset = blockOffset;
    batchLength = bl;
    batchTotalSize = ts;
    position = EventBridgeBatch.getPosition(blockBuffer, blockOffset);
    timestamp = EventBridgeBatch.getTimestamp(blockBuffer, blockOffset);
    entryCount = EventBridgeBatch.getEntryCount(blockBuffer, blockOffset);

    // Advance block cursor past this batch
    blockOffset += ts;

    return entryCount > 0;
  }

  /** Loads the next block from LogStorage. Returns false if the log is exhausted. */
  private boolean loadNextBlock() {
    if (!storageReader.hasNext()) {
      hasNext = false;
      return false;
    }

    blockBuffer = storageReader.next();

    if (blockBuffer == null || blockBuffer.capacity() < EventBridgeBatch.HEADER_LENGTH) {
      hasNext = false;
      return false;
    }

    blockLength = blockBuffer.capacity();
    blockOffset = 0;

    return true;
  }

  public IndexScanResult scan(final long fromPosition, final int maxBytes) {
    return storageReader.scan(fromPosition, maxBytes);
  }

  @Override
  public void close() {
    storageReader.close();
  }
}
