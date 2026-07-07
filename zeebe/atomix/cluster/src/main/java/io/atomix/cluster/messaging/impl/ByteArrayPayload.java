/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.ManagedPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;

/**
 * Payload backed by a heap byte array. The array is handed over on construction and must not be
 * mutated afterwards: large payloads are wrapped (not copied) into the outbound channel and may be
 * read by the I/O thread after this call returns.
 */
public class ByteArrayPayload implements ManagedPayload {

  /**
   * Below this size, copying into the (pooled) frame buffer is cheaper than the extra buffer
   * component and write vector a wrapped array costs; above it, wrapping avoids duplicating the
   * payload and keeps pooled frame buffers small.
   */
  private static final int WRAP_THRESHOLD = 4 * 1024;

  private final byte[] bytes;

  public ByteArrayPayload(final byte[] bytes) {
    this.bytes = bytes == null ? new byte[0] : bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

  @Override
  public int length() {
    return bytes.length;
  }

  @Override
  public void encode(final ByteBuf buffer, final List<Object> out) {
    if (bytes.length >= WRAP_THRESHOLD) {
      out.add(Unpooled.wrappedBuffer(bytes));
    } else if (bytes.length > 0) {
      buffer.writeBytes(bytes);
    }
  }
}
