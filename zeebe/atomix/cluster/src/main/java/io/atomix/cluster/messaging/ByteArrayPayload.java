/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Payload backed by a heap byte array. The array is handed over on construction and must not be
 * mutated afterwards: large payloads are wrapped (not copied) into the outbound channel and may be
 * read by the I/O thread after this call returns.
 */
public class ByteArrayPayload implements InboundPayload {

  private final byte[] bytes;
  private UnsafeBuffer view;

  public ByteArrayPayload(final byte[] bytes) {
    this.bytes = bytes == null ? new byte[0] : bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

  @Override
  public DirectBuffer view() {
    if (view == null) {
      view = new UnsafeBuffer(bytes);
    }
    return view;
  }

  @Override
  public int length() {
    return bytes.length;
  }

  @Override
  public byte[] toBytes() {
    return bytes;
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
