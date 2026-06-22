/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import io.camunda.eventbridge.protocol.EventBridgeBatch;
import io.camunda.eventbridge.protocol.EventBridgeBatchIterator;
import io.camunda.eventbridge.protocol.EventBridgeEntry;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Result of a fetch. Contains zero or more complete {@link EventBridgeBatch} instances packed
 * contiguously. Entry-level iteration is handled client-side via {@link #entries(long)}.
 *
 * <p>Wire format parsed from the gateway:
 *
 * <pre>firstBatchPosition(8) | lastBatchPosition(8) | highWatermark(8) | dataLength(4) | data</pre>
 */
public final class FetchResult {

  private static final int HEADER_SIZE = Long.BYTES * 3 + Integer.BYTES;

  private final boolean success;
  private final long firstBatchPosition;
  private final long lastBatchPosition;
  private final byte[] data;
  private final int dataLength;
  private final long highWatermark;
  private final int statusCode;
  private final String error;

  private FetchResult(
      final boolean success,
      final long firstBatchPosition,
      final long lastBatchPosition,
      final byte[] data,
      final int dataLength,
      final long highWatermark,
      final int statusCode,
      final String error) {
    this.success = success;
    this.firstBatchPosition = firstBatchPosition;
    this.lastBatchPosition = lastBatchPosition;
    this.data = data;
    this.dataLength = dataLength;
    this.highWatermark = highWatermark;
    this.statusCode = statusCode;
    this.error = error;
  }

  /** Parses a binary fetch response body. */
  static FetchResult parse(final int statusCode, final byte[] body) {
    if (statusCode != 200) {
      return error(statusCode, "Fetch failed: HTTP " + statusCode);
    }
    if (body == null || body.length < HEADER_SIZE) {
      return empty(0);
    }

    final long firstBatchPosition = readLong(body, 0);
    final long lastBatchPosition = readLong(body, 8);
    final long highWatermark = readLong(body, 16);
    final int dataLength = readInt(body, 24);

    if (dataLength == 0) {
      return empty(highWatermark);
    }

    final var batchData = new byte[dataLength];
    System.arraycopy(body, HEADER_SIZE, batchData, 0, dataLength);

    return new FetchResult(
        true,
        firstBatchPosition,
        lastBatchPosition,
        batchData,
        dataLength,
        highWatermark,
        200,
        null);
  }

  static FetchResult empty(final long highWatermark) {
    return new FetchResult(true, 0, -1, new byte[0], 0, highWatermark, 200, null);
  }

  static FetchResult error(final int statusCode, final String error) {
    return new FetchResult(false, 0, -1, new byte[0], 0, -1, statusCode, error);
  }

  public boolean isSuccess() {
    return success;
  }

  public boolean isEmpty() {
    return dataLength == 0;
  }

  public long firstBatchPosition() {
    return firstBatchPosition;
  }

  public long lastBatchPosition() {
    return lastBatchPosition;
  }

  public long highWatermark() {
    return highWatermark;
  }

  public int statusCode() {
    return statusCode;
  }

  public String error() {
    return error;
  }

  /**
   * Consumer lag — positions between the last fetched position and the high watermark. 0 means
   * fully caught up.
   */
  public long lag() {
    if (lastBatchPosition < 0) {
      return highWatermark;
    }
    return highWatermark - lastBatchPosition;
  }

  /**
   * The offset to use in the next fetch (one past the last entry returned). If the fetch was empty,
   * returns {@code requestedOffset} unchanged.
   */
  public long nextOffset(final long requestedOffset) {
    if (lastBatchPosition < 0) {
      return requestedOffset;
    }
    return lastBatchPosition + 1;
  }

  /**
   * Iterates over individual entries, skipping those before {@code startOffset}. The broker returns
   * complete batches, so the first batch may contain entries before the requested offset.
   *
   * <p>Uses the flyweight pattern — one iterator and one {@link EventBridgeEntry} are reused. Copy
   * entry data before advancing.
   */
  public Iterable<EventBridgeEntry> entries(final long startOffset) {
    return () -> new EntryIterator(data, dataLength, startOffset);
  }

  /** Iterates over all entries without skipping. */
  public Iterable<EventBridgeEntry> entries() {
    return () -> new EntryIterator(data, dataLength, Long.MIN_VALUE);
  }

  private static long readLong(final byte[] data, final int offset) {
    return ((long) readInt(data, offset)) << 32 | (readInt(data, offset + 4) & 0xFFFFFFFFL);
  }

  private static int readInt(final byte[] data, final int offset) {
    return (data[offset] & 0xFF) << 24
        | (data[offset + 1] & 0xFF) << 16
        | (data[offset + 2] & 0xFF) << 8
        | (data[offset + 3] & 0xFF);
  }

  /**
   * Iterates over individual entries across multiple contiguous batches, handling batch boundaries
   * transparently. Entry data is only valid until the next {@link #next()} call.
   */
  private static final class EntryIterator implements Iterator<EventBridgeEntry> {

    private final UnsafeBuffer buffer;
    private final int dataLength;
    private final long startOffset;
    private final EventBridgeBatchIterator batchIterator;

    private int batchCursor;
    private boolean started;

    EntryIterator(final byte[] data, final int dataLength, final long startOffset) {
      buffer = new UnsafeBuffer(data, 0, dataLength);
      this.dataLength = dataLength;
      this.startOffset = startOffset;
      batchIterator = new EventBridgeBatchIterator();
      batchCursor = 0;
      started = false;
    }

    @Override
    public boolean hasNext() {
      ensureStarted();
      if (batchIterator.hasNext()) {
        return true;
      }
      return loadNextBatch();
    }

    @Override
    public EventBridgeEntry next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No more entries");
      }
      return batchIterator.next();
    }

    private void ensureStarted() {
      if (!started) {
        started = true;
        if (loadNextBatch() && startOffset > Long.MIN_VALUE) {
          batchIterator.skipTo(startOffset);
        }
      }
    }

    private boolean loadNextBatch() {
      while (batchCursor + EventBridgeBatch.HEADER_LENGTH <= dataLength) {
        final int batchLength = EventBridgeBatch.getBatchLength(buffer, batchCursor);
        final int totalSize = EventBridgeBatch.totalSize(batchLength);

        if (batchLength <= 0 || batchCursor + totalSize > dataLength) {
          return false;
        }

        batchIterator.wrap(buffer, batchCursor, totalSize);
        batchCursor += totalSize;

        if (startOffset > Long.MIN_VALUE) {
          final long batchLastPosition =
              batchIterator.getBatchPosition() + batchIterator.getEntryCount() - 1;
          if (batchLastPosition < startOffset) {
            continue;
          }
        }

        if (batchIterator.hasNext()) {
          return true;
        }
      }
      return false;
    }
  }
}
