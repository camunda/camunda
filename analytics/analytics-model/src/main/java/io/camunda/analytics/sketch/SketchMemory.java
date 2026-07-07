/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.apache.datasketches.memory.Memory;

/**
 * Bridges an Agrona {@link DirectBuffer} view (a msgpack {@code BinaryProperty}'s value) to a
 * DataSketches {@link Memory} without copying: a heap-array-backed buffer — the case for every
 * record decoded from a {@code byte[]} — is wrapped in place at its offset. Only a buffer with no
 * accessible backing array (off-heap) falls back to an array copy, since {@link Memory} cannot
 * address it directly.
 */
public final class SketchMemory {

  private SketchMemory() {}

  /** A read-only {@link Memory} over the buffer's bytes — zero-copy when heap-array-backed. */
  public static Memory memoryOf(final DirectBuffer buffer) {
    final byte[] array = buffer.byteArray();
    if (array != null) {
      return Memory.wrap(
          array, buffer.wrapAdjustment(), buffer.capacity(), ByteOrder.nativeOrder());
    }
    return Memory.wrap(BufferUtil.bufferAsArray(buffer));
  }
}
