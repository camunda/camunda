/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import io.camunda.eventbridge.batch.BatchReader;
import java.util.List;

/**
 * Result of a fetch: zero or more complete batches plus the partition's high watermark, classified
 * by a semantic {@link Outcome}. This is a pure value type — it does <em>not</em> know about HTTP
 * status codes. The transport maps a response to an outcome ({@link #parse(byte[])} for a body,
 * {@link #outOfRange()} for a below-earliest cursor, {@link #failed(String)} otherwise), keeping
 * transport concerns out of the payload.
 *
 * <p>Entry-level iteration is client-side via {@link #entries(long)} using the pure {@link
 * BatchReader} (no Agrona / broker-protocol dependency).
 *
 * <p>Wire format of a successful body:
 *
 * <pre>firstBatchPosition(8) | lastBatchPosition(8) | highWatermark(8) | dataLength(4) | data</pre>
 */
public final class FetchResult {

  /** What a fetch produced. */
  public enum Outcome {
    /** Batches were returned. */
    OK,
    /** A valid response with no new data (caught up / long-poll timed out). */
    EMPTY,
    /** The requested offset is below the earliest retained record; the caller must reset. */
    OUT_OF_RANGE,
    /** The fetch failed (transport error or malformed response); see {@link #error()}. */
    FAILED
  }

  private static final int HEADER_SIZE = Long.BYTES * 3 + Integer.BYTES;

  private final Outcome outcome;
  private final long firstBatchPosition;
  private final long lastBatchPosition;

  /**
   * The full response body (including the header) for an {@link Outcome#OK} result, read from
   * {@link #dataOffset} onward — the payload is never copied into a separate array; only per-entry
   * value copies are made on demand. Empty for the non-OK factory results.
   */
  private final byte[] body;

  private final int dataOffset;
  private final int dataLength;
  private final long highWatermark;
  private final String error;

  private FetchResult(
      final Outcome outcome,
      final long firstBatchPosition,
      final long lastBatchPosition,
      final byte[] body,
      final int dataOffset,
      final int dataLength,
      final long highWatermark,
      final String error) {
    this.outcome = outcome;
    this.firstBatchPosition = firstBatchPosition;
    this.lastBatchPosition = lastBatchPosition;
    this.body = body;
    this.dataOffset = dataOffset;
    this.dataLength = dataLength;
    this.highWatermark = highWatermark;
    this.error = error;
  }

  /**
   * Parses a successful fetch body (the transport has already established a 200 response). A null
   * or empty body, or a header advertising no data, is {@link Outcome#EMPTY}; a body that is
   * present but truncated or overrunning is {@link Outcome#FAILED} (malformed) rather than silently
   * treated as empty.
   */
  public static FetchResult parse(final byte[] body) {
    if (body == null || body.length == 0) {
      return empty(-1);
    }
    if (body.length < HEADER_SIZE) {
      return failed("Malformed fetch response: " + body.length + " bytes < header " + HEADER_SIZE);
    }

    final long firstBatchPosition = readLong(body, 0);
    final long lastBatchPosition = readLong(body, 8);
    final long highWatermark = readLong(body, 16);
    final int dataLength = readInt(body, 24);

    if (dataLength == 0) {
      return empty(highWatermark);
    }
    // Don't trust the wire length: a negative or overrunning dataLength would blow up the reader.
    if (dataLength < 0 || body.length < HEADER_SIZE + dataLength) {
      return failed(
          "Malformed fetch response: dataLength " + dataLength + " for body " + body.length);
    }

    // Keep the response array as-is and read the batch region in place (offset HEADER_SIZE); the
    // payload is never copied, only per-entry value copies are made when entries are iterated.
    return new FetchResult(
        Outcome.OK,
        firstBatchPosition,
        lastBatchPosition,
        body,
        HEADER_SIZE,
        dataLength,
        highWatermark,
        null);
  }

  /** A valid, empty response with the given high watermark ({@code -1} if unknown). */
  public static FetchResult empty(final long highWatermark) {
    return new FetchResult(Outcome.EMPTY, 0, -1, new byte[0], 0, 0, highWatermark, null);
  }

  /** The cursor is below the earliest retained record — the caller must reset its offset. */
  public static FetchResult outOfRange() {
    return new FetchResult(
        Outcome.OUT_OF_RANGE, 0, -1, new byte[0], 0, 0, -1, "OFFSET_OUT_OF_RANGE");
  }

  /** The fetch failed (transport error or malformed response). */
  public static FetchResult failed(final String error) {
    return new FetchResult(Outcome.FAILED, 0, -1, new byte[0], 0, 0, -1, error);
  }

  public Outcome outcome() {
    return outcome;
  }

  /** True if the fetch yielded a valid response (batches or a legitimate empty). */
  public boolean isSuccess() {
    return outcome == Outcome.OK || outcome == Outcome.EMPTY;
  }

  public boolean isEmpty() {
    return dataLength == 0;
  }

  public boolean isOutOfRange() {
    return outcome == Outcome.OUT_OF_RANGE;
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

  public String error() {
    return error;
  }

  /**
   * Iterates over individual entries, skipping those before {@code startOffset}. The broker returns
   * complete batches, so the first batch may contain entries before the requested offset. Each
   * returned {@link BatchReader.Entry} owns a copy of its value bytes (it must outlive the shared
   * response array); keys are not needed on the fetch path, so {@link BatchReader.Entry#key()} is
   * always empty here and the per-entry key copy is skipped.
   */
  public List<BatchReader.Entry> entries(final long startOffset) {
    return BatchReader.readSkippingKeys(body, dataOffset, dataLength, startOffset);
  }

  /** Iterates over all entries without skipping. */
  public List<BatchReader.Entry> entries() {
    return entries(Long.MIN_VALUE);
  }

  /**
   * Visits each entry at or after {@code startOffset} in place — the cursor counterpart of {@link
   * #entries(long)} for the hot fetch path: no list and no per-entry object are allocated; the
   * visitor reads position and value coordinates directly against the shared response array and
   * must copy the value itself if it retains it.
   */
  public void forEachEntry(final long startOffset, final BatchReader.EntryVisitor visitor) {
    BatchReader.forEachSkippingKeys(body, dataOffset, dataLength, startOffset, visitor);
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
}
