/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import io.camunda.eventbridge.batch.BatchReader;

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
  private final byte[] data;
  private final int dataLength;
  private final long highWatermark;
  private final String error;

  private FetchResult(
      final Outcome outcome,
      final long firstBatchPosition,
      final long lastBatchPosition,
      final byte[] data,
      final int dataLength,
      final long highWatermark,
      final String error) {
    this.outcome = outcome;
    this.firstBatchPosition = firstBatchPosition;
    this.lastBatchPosition = lastBatchPosition;
    this.data = data;
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
    // Don't trust the wire length: a negative or overrunning dataLength would blow up the
    // arraycopy.
    if (dataLength < 0 || body.length < HEADER_SIZE + dataLength) {
      return failed(
          "Malformed fetch response: dataLength " + dataLength + " for body " + body.length);
    }

    final var batchData = new byte[dataLength];
    System.arraycopy(body, HEADER_SIZE, batchData, 0, dataLength);
    return new FetchResult(
        Outcome.OK,
        firstBatchPosition,
        lastBatchPosition,
        batchData,
        dataLength,
        highWatermark,
        null);
  }

  /** A valid, empty response with the given high watermark ({@code -1} if unknown). */
  public static FetchResult empty(final long highWatermark) {
    return new FetchResult(Outcome.EMPTY, 0, -1, new byte[0], 0, highWatermark, null);
  }

  /** The cursor is below the earliest retained record — the caller must reset its offset. */
  public static FetchResult outOfRange() {
    return new FetchResult(Outcome.OUT_OF_RANGE, 0, -1, new byte[0], 0, -1, "OFFSET_OUT_OF_RANGE");
  }

  /** The fetch failed (transport error or malformed response). */
  public static FetchResult failed(final String error) {
    return new FetchResult(Outcome.FAILED, 0, -1, new byte[0], 0, -1, error);
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
   * returned {@link FetchedEntry} owns copies of its key/value bytes.
   */
  public Iterable<FetchedEntry> entries(final long startOffset) {
    return BatchReader.read(data, 0, dataLength, startOffset).stream()
        .map(FetchedEntry::new)
        .toList();
  }

  /** Iterates over all entries without skipping. */
  public Iterable<FetchedEntry> entries() {
    return entries(Long.MIN_VALUE);
  }

  /** A single fetched entry: its log position and copies of its key and value bytes. */
  public static final class FetchedEntry {

    private final BatchReader.Entry entry;

    FetchedEntry(final BatchReader.Entry entry) {
      this.entry = entry;
    }

    public long getPosition() {
      return entry.position();
    }

    public byte[] getValueCopy() {
      return entry.value();
    }
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
