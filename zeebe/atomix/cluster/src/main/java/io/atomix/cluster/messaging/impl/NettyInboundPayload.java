/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.InboundPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Inbound payload backed by a retained slice of the network receive buffer. Reading happens in
 * place through {@link #view()}; the slice (and with it the underlying pooled receive buffer) is
 * pinned until {@link #release()}.
 */
final class NettyInboundPayload implements InboundPayload {

  private final ByteBuf slice;
  private final UnsafeBuffer view;

  NettyInboundPayload(final ByteBuf slice) {
    this.slice = slice;
    view = new UnsafeBuffer(slice.nioBuffer(slice.readerIndex(), slice.readableBytes()));
  }

  @Override
  public DirectBuffer view() {
    return view;
  }

  @Override
  public int length() {
    return slice.readableBytes();
  }

  @Override
  public byte[] toBytes() {
    return ByteBufUtil.getBytes(slice, slice.readerIndex(), slice.readableBytes());
  }

  @Override
  public void encode(final ByteBuf buffer, final List<Object> out) {
    if (length() >= WRAP_THRESHOLD) {
      out.add(slice.retainedSlice());
    } else if (length() > 0) {
      buffer.writeBytes(slice, slice.readerIndex(), slice.readableBytes());
    }
  }

  @Override
  public void release() {
    slice.release();
  }
}
