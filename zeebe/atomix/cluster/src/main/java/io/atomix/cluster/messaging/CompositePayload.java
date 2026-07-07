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

/**
 * Payload composed of a small serialized header followed by a bulk body that is referenced, not
 * copied: large heap-backed bodies are appended to the outbound channel as wrapped buffer
 * components. This lets senders frame a request around an existing buffer (e.g. a record batch
 * relayed by a gateway) without re-serializing it.
 *
 * <p>Both the header array and the body buffer are handed over on construction and must stay
 * unmodified until the send completes; the I/O thread may read them after this call returns.
 */
public final class CompositePayload implements ManagedPayload {

  private final byte[] header;
  private final DirectBuffer body;

  public CompositePayload(final byte[] header, final DirectBuffer body) {
    this.header = header;
    this.body = body;
  }

  @Override
  public int length() {
    return header.length + body.capacity();
  }

  @Override
  public void encode(final ByteBuf buffer, final List<Object> out) {
    buffer.writeBytes(header);

    final int bodyLength = body.capacity();
    if (bodyLength == 0) {
      return;
    }

    final byte[] array = body.byteArray();
    if (array != null && bodyLength >= WRAP_THRESHOLD) {
      out.add(Unpooled.wrappedBuffer(array, body.wrapAdjustment(), bodyLength));
    } else if (array != null) {
      buffer.writeBytes(array, body.wrapAdjustment(), bodyLength);
    } else {
      // no backing array to wrap: materialize and copy into the frame buffer
      final byte[] copy = new byte[bodyLength];
      body.getBytes(0, copy, 0, bodyLength);
      buffer.writeBytes(copy);
    }
  }

  @Override
  public byte[] toBytes() {
    final byte[] bytes = new byte[length()];
    System.arraycopy(header, 0, bytes, 0, header.length);
    body.getBytes(0, bytes, header.length, body.capacity());
    return bytes;
  }
}
