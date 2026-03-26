/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Builds an EventBridge batch from individual entries.
 *
 * <p>Sets {@code totalLength} at build time. {@code batchPosition} and {@code timestamp} are
 * placeholders (0) — the broker assigns them.
 */
public final class EventBridgeBatchBuilder {

  private final ExpandableArrayBuffer entriesBuffer = new ExpandableArrayBuffer();
  private int entriesOffset;
  private int entryCount;

  public EventBridgeBatchBuilder() {
    reset();
  }

  public EventBridgeBatchBuilder addEntry(final byte[] value, final int offset, final int length) {
    entriesBuffer.putInt(entriesOffset, length);
    entriesOffset += Integer.BYTES;
    entriesBuffer.putBytes(entriesOffset, value, offset, length);
    entriesOffset += length;
    entryCount++;
    return this;
  }

  public EventBridgeBatchBuilder addEntry(final byte[] value) {
    return addEntry(value, 0, value.length);
  }

  public int getEntryCount() {
    return entryCount;
  }

  public int getEntriesLength() {
    return entriesOffset;
  }

  /**
   * Builds the full batch including header. {@code totalLength} is computed from the entries.
   * {@code batchPosition} and {@code timestamp} are 0 (broker assigns them).
   */
  public byte[] build() {
    final int totalLength = EventBridgeBatch.batchLength(entriesOffset);
    final var output = new byte[totalLength];
    final var buffer = new UnsafeBuffer(output);

    EventBridgeBatch.writeHeader(buffer, 0, entriesOffset, 0L, 0L, entryCount);
    buffer.putBytes(EventBridgeBatch.ENTRIES_OFFSET, entriesBuffer, 0, entriesOffset);

    return output;
  }

  public void reset() {
    entriesOffset = 0;
    entryCount = 0;
  }
}
