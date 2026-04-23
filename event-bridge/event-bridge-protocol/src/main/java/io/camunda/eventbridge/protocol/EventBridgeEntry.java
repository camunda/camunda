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
 * Flyweight reader over a single entry within an EventBridge batch.
 *
 * <p>Entry format:
 *
 * <pre>
 * offset  size  field
 * 0       4     entryLength    bytes after this field (keyLength field + key + value)
 * 4       4     keyLength      key byte length (0 = no key)
 * 8       var   key            key bytes (absent if keyLength == 0)
 * 8+kLen  var   value          value bytes (raw payload — JSON, Protobuf, etc.)
 * </pre>
 *
 * <p>The key is optional. When {@code keyLength == 0}, there is no key and the value starts
 * immediately after the keyLength field.
 *
 * <p>The value is the raw event payload. The broker treats it as opaque bytes. The content type
 * (JSON, Protobuf, etc.) is defined per-batch in the batch header's {@code attributes} field — not
 * per-entry. All entries in a batch share the same content type.
 *
 * <p>Position and timestamp are derived from the containing batch — not stored per entry. Position
 * is {@code batchPosition + entryIndex}. Timestamp is the broker append time on the batch header.
 *
 * <p>Reusable flyweight — call {@link #wrap} per entry. Not thread-safe. Typically owned by a
 * single {@link EventBridgeBatchIterator} which reuses the same entry instance across iterations.
 * Callers must consume or copy entry data before calling {@link #wrap} again.
 */
public final class EventBridgeEntry {

  /** Size of the entry length prefix. */
  public static final int ENTRY_LENGTH_SIZE = Integer.BYTES;

  /** Size of the key length field. */
  public static final int KEY_LENGTH_SIZE = Integer.BYTES;

  /** Fixed overhead per entry (entryLength + keyLength). */
  public static final int ENTRY_HEADER_SIZE = ENTRY_LENGTH_SIZE + KEY_LENGTH_SIZE;

  /** Minimum valid entryLength value — must at least contain the keyLength field. */
  public static final int MIN_ENTRY_LENGTH = KEY_LENGTH_SIZE;

  private DirectBuffer buffer;
  private int offset;
  private int entryLength;
  private int keyLength;
  private int valueLength;
  private long position;
  private long timestamp;

  /**
   * Wraps this flyweight over an entry at the given offset in the buffer.
   *
   * @param buffer the buffer containing the entry
   * @param offset byte offset of the entry's length prefix within the buffer
   * @param batchPosition position of the first entry in the containing batch
   * @param entryIndex zero-based index of this entry within the batch
   * @param timestamp broker append timestamp of the containing batch
   * @throws IllegalArgumentException if the entry header fields are invalid
   */
  public void wrap(
      final DirectBuffer buffer,
      final int offset,
      final long batchPosition,
      final int entryIndex,
      final long timestamp) {
    this.buffer = buffer;
    this.offset = offset;
    entryLength = buffer.getInt(offset);
    keyLength = buffer.getInt(offset + ENTRY_LENGTH_SIZE);

    if (entryLength < MIN_ENTRY_LENGTH) {
      throw new IllegalArgumentException(
          "Invalid entry at offset "
              + offset
              + ": entryLength="
              + entryLength
              + " is less than minimum ("
              + MIN_ENTRY_LENGTH
              + ")");
    }
    if (keyLength < 0 || keyLength > entryLength - KEY_LENGTH_SIZE) {
      throw new IllegalArgumentException(
          "Invalid entry at offset "
              + offset
              + ": keyLength="
              + keyLength
              + " is out of range for entryLength="
              + entryLength);
    }

    valueLength = entryLength - KEY_LENGTH_SIZE - keyLength;
    position = batchPosition + entryIndex;
    this.timestamp = timestamp;
  }

  // -- Position and timestamp (derived from batch) --

  /** Returns the log position of this entry (batchPosition + entryIndex). */
  public long getPosition() {
    return position;
  }

  /** Returns the broker append timestamp of the containing batch (millis since epoch). */
  public long getTimestamp() {
    return timestamp;
  }

  // -- Key access --

  /** Returns {@code true} if this entry has a key (keyLength &gt; 0). */
  public boolean hasKey() {
    return keyLength > 0;
  }

  /** Returns the byte length of the key (0 if no key). */
  public int getKeyLength() {
    return keyLength;
  }

  /** Returns the byte offset of the key data within the buffer. */
  public int getKeyOffset() {
    return offset + ENTRY_HEADER_SIZE;
  }

  /**
   * Returns the buffer containing the key data. Use with {@link #getKeyOffset()} and {@link
   * #getKeyLength()} to read the key.
   */
  public DirectBuffer getKeyBuffer() {
    return buffer;
  }

  /**
   * Copies the key data into a new byte array.
   *
   * @return a new byte array containing the key, or an empty array if no key
   */
  public byte[] getKeyCopy() {
    if (keyLength == 0) {
      return new byte[0];
    }
    final var copy = new byte[keyLength];
    buffer.getBytes(getKeyOffset(), copy, 0, keyLength);
    return copy;
  }

  /**
   * Copies the key data into the target byte array.
   *
   * @param target destination byte array
   * @param targetOffset offset within the target array
   */
  public void getKeyBytes(final byte[] target, final int targetOffset) {
    if (keyLength > 0) {
      buffer.getBytes(getKeyOffset(), target, targetOffset, keyLength);
    }
  }

  // -- Value access --

  /** Returns the byte length of the value data (raw event payload). */
  public int getValueLength() {
    return valueLength;
  }

  /** Returns the byte offset of the value data within the buffer. */
  public int getValueOffset() {
    return offset + ENTRY_HEADER_SIZE + keyLength;
  }

  /**
   * Returns the buffer containing the value data. Use with {@link #getValueOffset()} and {@link
   * #getValueLength()} to read the value.
   */
  public DirectBuffer getValueBuffer() {
    return buffer;
  }

  /**
   * Copies the value data into a new byte array.
   *
   * @return a new byte array containing the value data
   */
  public byte[] getValueCopy() {
    final var copy = new byte[valueLength];
    buffer.getBytes(getValueOffset(), copy, 0, valueLength);
    return copy;
  }

  /**
   * Copies the value data into the target byte array.
   *
   * @param target destination byte array
   * @param targetOffset offset within the target array
   */
  public void getValueBytes(final byte[] target, final int targetOffset) {
    buffer.getBytes(getValueOffset(), target, targetOffset, valueLength);
  }

  // -- Total length --

  /**
   * Returns the total byte length of this entry on the wire/disk (entryLength field + entryLength
   * value). Used by the iterator to advance to the next entry.
   */
  public int getTotalLength() {
    return ENTRY_LENGTH_SIZE + entryLength;
  }

  /**
   * Computes the total byte length of an entry at the given offset without wrapping. Useful for
   * scanning or skipping entries without constructing a flyweight.
   *
   * @param buffer the buffer containing the entry
   * @param offset byte offset of the entry's length prefix
   * @return total entry length (length prefix + entry data)
   */
  public static int entryTotalLength(final DirectBuffer buffer, final int offset) {
    return ENTRY_LENGTH_SIZE + buffer.getInt(offset);
  }

  /**
   * Computes the total byte size of an entry given key and value lengths. Useful for pre-computing
   * buffer sizes before writing.
   *
   * @param keyLength byte length of the key (0 for no key)
   * @param valueLength byte length of the value
   * @return total entry size including all framing overhead
   */
  public static int computeTotalLength(final int keyLength, final int valueLength) {
    return ENTRY_HEADER_SIZE + keyLength + valueLength;
  }
}
