/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A batch of {@link EventData} items with binary framing.
 *
 * <p>Two construction modes:
 *
 * <ul>
 *   <li><b>Build mode</b> — created via {@link #create(int, int, int)}. Call {@link
 *       #tryAdd(EventData)} to append events, then {@link #toBytes()} to serialise.
 *   <li><b>Wrap mode</b> — created via {@link #fromBytes(byte[])}. Read-only; {@link #getCount()}
 *       and {@link #getSizeInBytes()} read directly from the framing header without iterating
 *       events.
 * </ul>
 *
 * <h2>Wire format</h2>
 *
 * <pre>
 * [4-byte total-size   (int, big-endian)]  — total byte length of entire buffer (including 12-byte header)
 * [4-byte payload-size (int, big-endian)]  — sum of all EventData.sizeInBytes() (payload bytes only)
 * [4-byte count        (int, big-endian)]  — number of events
 * for each event:
 *   [4-byte size  (int, big-endian)]
 *   [size bytes   body]
 * </pre>
 *
 * <p><b>Not thread-safe.</b> Build-mode instances must not be shared across threads during
 * construction. Wrap-mode instances are read-only after construction and safe for concurrent reads.
 */
public final class EventDataBatch {

  /** Size of the framing header in bytes (total-size + payload-size + count). */
  private static final int HEADER_SIZE = 12;

  // --- Build-mode state ---
  private final int maxBatchBytes;
  private final int maxEventBytes;
  private final int maxBatchSize;
  private List<EventData> events; // null in wrap mode

  // --- Shared metadata (eagerly tracked in build mode; read from header in wrap mode) ---
  private int count;
  private int sizeInBytes;

  // --- Wrap-mode state ---
  private byte[] backingBytes; // null in build mode

  // -------------------------------------------------------------------------
  // Private constructors
  // -------------------------------------------------------------------------

  /** Build-mode constructor. */
  private EventDataBatch(final int maxBatchBytes, final int maxEventBytes, final int maxBatchSize) {
    this.maxBatchBytes = maxBatchBytes;
    this.maxEventBytes = maxEventBytes;
    this.maxBatchSize = maxBatchSize;
    events = new ArrayList<>();
    count = 0;
    sizeInBytes = 0;
  }

  /** Wrap-mode constructor — called after validation in {@link #fromBytes(byte[])}. */
  private EventDataBatch(final byte[] validatedBytes, final int count, final int sizeInBytes) {
    // maxBatch* fields are irrelevant in wrap mode
    maxBatchBytes = 0;
    maxEventBytes = 0;
    maxBatchSize = 0;
    backingBytes = validatedBytes; // already defensively copied by the caller
    this.count = count;
    this.sizeInBytes = sizeInBytes;
  }

  // -------------------------------------------------------------------------
  // Factory methods
  // -------------------------------------------------------------------------

  /**
   * Creates a new empty batch in build mode.
   *
   * @param maxBatchBytes maximum total payload bytes the batch may hold (must be &gt; 0)
   * @param maxEventBytes maximum bytes for a single event (must be &ge; 0; 0 means only zero-length
   *     events are accepted)
   * @param maxBatchSize maximum number of events the batch may hold (must be &gt; 0)
   * @throws IllegalArgumentException if any parameter violates its lower bound
   */
  public static EventDataBatch create(
      final int maxBatchBytes, final int maxEventBytes, final int maxBatchSize) {
    if (maxBatchBytes <= 0) {
      throw new IllegalArgumentException("maxBatchBytes must be positive, was: " + maxBatchBytes);
    }
    if (maxEventBytes < 0) {
      throw new IllegalArgumentException(
          "maxEventBytes must be non-negative, was: " + maxEventBytes);
    }
    if (maxBatchSize <= 0) {
      throw new IllegalArgumentException("maxBatchSize must be positive, was: " + maxBatchSize);
    }
    return new EventDataBatch(maxBatchBytes, maxEventBytes, maxBatchSize);
  }

  /**
   * Creates a batch in wrap mode by deserialising the given bytes.
   *
   * <p>A defensive copy is made so that external mutation of the input array does not affect this
   * instance.
   *
   * @param bytes the serialised batch; must not be {@code null}
   * @throws NullPointerException if {@code bytes} is {@code null}
   * @throws IllegalArgumentException if the header fields are structurally invalid
   */
  public static EventDataBatch fromBytes(final byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");

    if (bytes.length < HEADER_SIZE) {
      throw new IllegalArgumentException(
          "buffer too short: expected at least " + HEADER_SIZE + " bytes, got " + bytes.length);
    }

    final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    final int totalSize = buf.getInt(0);
    final int payloadSize = buf.getInt(4);
    final int count = buf.getInt(8);

    if (totalSize < HEADER_SIZE) {
      throw new IllegalArgumentException(
          "total-size field is less than minimum header size (" + HEADER_SIZE + "): " + totalSize);
    }
    if (totalSize != bytes.length) {
      throw new IllegalArgumentException(
          "total-size field ("
              + totalSize
              + ") does not match buffer length ("
              + bytes.length
              + ")");
    }
    if (payloadSize < 0) {
      throw new IllegalArgumentException(
          "payload-size field must be non-negative, was: " + payloadSize);
    }
    if (count < 0) {
      throw new IllegalArgumentException("count field must be non-negative, was: " + count);
    }
    if (count == 0 && payloadSize != 0) {
      throw new IllegalArgumentException(
          "count is 0 but payload-size field is non-zero: " + payloadSize);
    }

    // Validate framing before cloning to avoid unnecessary memory allocation for malformed input
    validateStructure(bytes, count, payloadSize);
    final byte[] copy = bytes.clone();
    return new EventDataBatch(copy, count, payloadSize);
  }

  // -------------------------------------------------------------------------
  // Build-mode API
  // -------------------------------------------------------------------------

  /**
   * Attempts to add {@code event} to this batch.
   *
   * <p>Returns {@code false} (soft failure) if any capacity limit would be exceeded:
   *
   * <ul>
   *   <li>{@code event.sizeInBytes() > maxEventBytes}
   *   <li>{@code getSizeInBytes() + event.sizeInBytes() > maxBatchBytes}
   *   <li>{@code getCount() + 1 > maxBatchSize}
   * </ul>
   *
   * An event that brings any metric to <em>exactly</em> the respective limit is accepted.
   *
   * @param event the event to add; must not be {@code null}
   * @return {@code true} if the event was added, {@code false} if any capacity limit would be
   *     exceeded
   * @throws NullPointerException if {@code event} is {@code null}
   * @throws IllegalStateException if this batch is in wrap mode
   */
  public boolean tryAdd(final EventData event) {
    Objects.requireNonNull(event, "event must not be null");
    if (backingBytes != null) {
      throw new IllegalStateException("tryAdd() is not supported on a wrap-mode EventDataBatch");
    }

    final int eventSize = event.sizeInBytes();

    if (eventSize > maxEventBytes) {
      return false;
    }
    if ((long) sizeInBytes + eventSize > maxBatchBytes) {
      return false;
    }
    if (count >= maxBatchSize) {
      return false;
    }

    events.add(event);
    count++;
    sizeInBytes += eventSize;
    return true;
  }

  // -------------------------------------------------------------------------
  // Accessors (both modes)
  // -------------------------------------------------------------------------

  /**
   * Returns the number of events in this batch.
   *
   * <p>In build mode, this is the number of successful {@link #tryAdd} calls. In wrap mode, this is
   * read directly from the framing header.
   */
  public int getCount() {
    return count;
  }

  /**
   * Returns the total payload size in bytes (sum of {@link EventData#sizeInBytes()} for all events;
   * excludes header and per-event prefix bytes).
   *
   * <p>In build mode, this is the running total accumulated by {@link #tryAdd}. In wrap mode, this
   * is read directly from the framing header.
   */
  public int getSizeInBytes() {
    return sizeInBytes;
  }

  /**
   * Serialises this batch to the binary wire format.
   *
   * <p>In wrap mode, returns a copy of the backing buffer. In build mode, re-serialises from the
   * in-memory event list. Calling this method before all intended events have been added is
   * supported: the result is a complete, valid batch containing exactly the events added so far.
   */
  public byte[] toBytes() {
    if (backingBytes != null) {
      // Wrap mode: return a copy of the backing buffer
      return backingBytes.clone();
    }

    // Build mode: serialise
    // total-size = 12-byte header + 4-byte length prefix per event + payload bytes
    final int totalSize = Math.toIntExact((long) HEADER_SIZE + 4L * count + sizeInBytes);
    final ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
    buf.putInt(totalSize);
    buf.putInt(sizeInBytes);
    buf.putInt(count);
    for (final EventData event : events) {
      buf.putInt(event.sizeInBytes());
      buf.put(event.body());
    }
    return buf.array();
  }

  /**
   * Returns an {@link Iterable} over the events in this batch.
   *
   * <p>Each call to {@link Iterable#iterator()} (and each call to this method) produces an
   * independent traversal:
   *
   * <ul>
   *   <li><b>Build mode:</b> iterates the in-memory event list from the beginning.
   *   <li><b>Wrap mode:</b> lazily parses frames from the backing buffer from the beginning. After
   *       the last frame, validates that the sum of parsed payload sizes equals the {@code
   *       payload-size} header field; a mismatch throws {@link IllegalArgumentException}.
   *       Structural frame errors (e.g., a frame whose length would read past the buffer) also
   *       throw {@link IllegalArgumentException} lazily during iteration.
   * </ul>
   */
  public Iterable<EventData> getEvents() {
    if (backingBytes != null) {
      return this::wrapModeIterator;
    }
    // Build mode: re-iterable via snapshot of the list (list itself won't shrink; safe to iterate
    // directly but a lambda keeps the semantics clear)
    return () -> List.copyOf(events).iterator();
  }

  // -------------------------------------------------------------------------
  // Wrap-mode structural validation
  // -------------------------------------------------------------------------

  /**
   * Eagerly validates that the framing in {@code bytes} is consistent with the declared {@code
   * count} and {@code payloadSize} header fields.
   *
   * <ul>
   *   <li>Parses exactly {@code count} frames.
   *   <li>Rejects negative or out-of-bounds frame sizes.
   *   <li>Asserts no trailing bytes remain after the last frame.
   *   <li>Asserts the sum of frame sizes equals {@code payloadSize}.
   * </ul>
   */
  private static void validateStructure(
      final byte[] bytes, final int count, final int payloadSize) {
    final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    buf.position(HEADER_SIZE);
    int actualPayloadSum = 0;
    for (int i = 0; i < count; i++) {
      if (buf.remaining() < 4) {
        throw new IllegalArgumentException(
            "Truncated batch: expected 4-byte length prefix for frame "
                + i
                + " but only "
                + buf.remaining()
                + " bytes remain");
      }
      final int frameSize = buf.getInt();
      if (frameSize < 0) {
        throw new IllegalArgumentException("Invalid frame " + i + ": negative size " + frameSize);
      }
      if (frameSize > buf.remaining()) {
        throw new IllegalArgumentException(
            "Truncated batch: frame "
                + i
                + " declares "
                + frameSize
                + " bytes but only "
                + buf.remaining()
                + " bytes remain");
      }
      buf.position(buf.position() + frameSize);
      actualPayloadSum += frameSize;
    }
    if (buf.remaining() != 0) {
      throw new IllegalArgumentException(
          "Trailing bytes after last frame: " + buf.remaining() + " bytes remain");
    }
    if (actualPayloadSum != payloadSize) {
      throw new IllegalArgumentException(
          "Payload-size mismatch: header declared "
              + payloadSize
              + " bytes but frames total "
              + actualPayloadSum
              + " bytes");
    }
  }

  // -------------------------------------------------------------------------
  // Wrap-mode lazy iterator
  // -------------------------------------------------------------------------

  private Iterator<EventData> wrapModeIterator() {
    return new WrapModeIterator(backingBytes, count, sizeInBytes);
  }

  /** Lazily parses frames from the backing buffer for a single traversal. */
  private static final class WrapModeIterator implements Iterator<EventData> {

    private final ByteBuffer buf;
    private final int expectedCount;
    private final int expectedPayloadSize;
    private int remaining;
    private int actualPayloadSum;

    WrapModeIterator(
        final byte[] backingBytes, final int expectedCount, final int expectedPayloadSize) {
      buf = ByteBuffer.wrap(backingBytes).order(ByteOrder.BIG_ENDIAN);
      buf.position(HEADER_SIZE); // skip the 12-byte header
      this.expectedCount = expectedCount;
      this.expectedPayloadSize = expectedPayloadSize;
      remaining = expectedCount;
      actualPayloadSum = 0;
    }

    @Override
    public boolean hasNext() {
      return remaining > 0;
    }

    @Override
    public EventData next() {
      if (!hasNext()) {
        throw new NoSuchElementException("No more events in batch");
      }

      if (buf.remaining() < 4) {
        throw new IllegalArgumentException(
            "Truncated batch: expected a 4-byte frame length at offset "
                + buf.position()
                + " but only "
                + buf.remaining()
                + " bytes remain");
      }

      final int frameSize = buf.getInt();
      if (frameSize < 0) {
        throw new IllegalArgumentException(
            "Invalid frame: negative payload size "
                + frameSize
                + " at offset "
                + (buf.position() - 4));
      }
      if (frameSize > buf.remaining()) {
        throw new IllegalArgumentException(
            "Truncated batch: frame declares "
                + frameSize
                + " bytes but only "
                + buf.remaining()
                + " bytes remain");
      }

      final byte[] body = new byte[frameSize];
      buf.get(body);
      actualPayloadSum += frameSize;
      remaining--;

      // After the last frame, validate payload-size consistency and no trailing bytes
      if (remaining == 0 && actualPayloadSum != expectedPayloadSize) {
        throw new IllegalArgumentException(
            "Payload-size mismatch: header declared "
                + expectedPayloadSize
                + " bytes but iterated frames total "
                + actualPayloadSum
                + " bytes");
      }
      if (remaining == 0 && buf.remaining() != 0) {
        throw new IllegalArgumentException(
            "Trailing bytes after last frame: " + buf.remaining() + " bytes remain");
      }

      return new EventData(body);
    }
  }
}
