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
 * <p>Entry format: {@code [entryLen (4b)][data (variable)]}. Position is derived from the batch
 * position plus entry index.
 *
 * <p>Reusable — call {@link #wrap} per entry.
 */
public final class EventBridgeEntry {

  public static final int LENGTH_PREFIX_SIZE = Integer.BYTES;

  private DirectBuffer buffer;
  private int offset;
  private long position;
  private long timestamp;

  public void wrap(
      final DirectBuffer buffer,
      final int offset,
      final long batchPosition,
      final int entryIndex,
      final long timestamp) {
    this.buffer = buffer;
    this.offset = offset;
    position = batchPosition + entryIndex;
    this.timestamp = timestamp;
  }

  public long getPosition() {
    return position;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getValueLength() {
    return buffer.getInt(offset);
  }

  public DirectBuffer getValueBuffer() {
    return buffer;
  }

  public int getValueOffset() {
    return offset + LENGTH_PREFIX_SIZE;
  }

  public int getTotalLength() {
    return LENGTH_PREFIX_SIZE + getValueLength();
  }

  public static int entryTotalLength(final DirectBuffer buffer, final int offset) {
    return LENGTH_PREFIX_SIZE + buffer.getInt(offset);
  }
}
