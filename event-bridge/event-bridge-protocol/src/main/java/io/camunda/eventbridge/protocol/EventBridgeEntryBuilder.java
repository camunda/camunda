/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Client-friendly builder for a single EventBridge entry. Produces a {@code byte[]} that can be
 * passed to {@link EventBridgeBatchBuilder#addEntry(byte[])}.
 *
 * <p>Reusable — call {@link #reset()} between entries.
 */
public final class EventBridgeEntryBuilder {

  private byte[] key;
  private int keyOffset;
  private int keyLength;

  private byte[] value;
  private int valueOffset;
  private int valueLength;

  public EventBridgeEntryBuilder key(final String key) {
    final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
    return key(bytes, 0, bytes.length);
  }

  public EventBridgeEntryBuilder key(final byte[] key) {
    return key(key, 0, key.length);
  }

  public EventBridgeEntryBuilder key(final byte[] key, final int offset, final int length) {
    this.key = key;
    keyOffset = offset;
    keyLength = length;
    return this;
  }

  public EventBridgeEntryBuilder value(final byte[] value) {
    return value(value, 0, value.length);
  }

  public EventBridgeEntryBuilder value(final String value) {
    final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return value(bytes, 0, bytes.length);
  }

  public EventBridgeEntryBuilder value(final byte[] value, final int offset, final int length) {
    this.value = value;
    valueOffset = offset;
    valueLength = length;
    return this;
  }

  /** Builds the entry as a {@code byte[]}. */
  public byte[] build() {
    if (value == null) {
      throw new IllegalStateException("Entry value must be set before building");
    }

    final int kLen = key != null ? keyLength : 0;

    // Total physical size of this entry payload
    final int entryLength = EventBridgeEntry.KEY_LENGTH_SIZE + kLen + valueLength;

    // Total physical size of the ENTIRE entry (Header + Payload)
    final int totalLength = EventBridgeEntry.ENTRY_LENGTH_SIZE + entryLength;

    final var output = new byte[totalLength];
    final var buffer = new UnsafeBuffer(output);
    int pos = 0;

    // Use Agrona to write the integers.
    // This perfectly aligns with Little-Endian reading on the broker side!
    buffer.putInt(pos, entryLength, ByteOrder.LITTLE_ENDIAN);
    pos += Integer.BYTES;

    buffer.putInt(pos, kLen, ByteOrder.LITTLE_ENDIAN);
    pos += Integer.BYTES;

    if (kLen > 0) {
      buffer.putBytes(pos, key, keyOffset, kLen);
      pos += kLen;
    }

    buffer.putBytes(pos, value, valueOffset, valueLength);

    return output;
  }

  public EventBridgeEntryBuilder reset() {
    key = null;
    keyOffset = 0;
    keyLength = 0;
    value = null;
    valueOffset = 0;
    valueLength = 0;
    return this;
  }
}
