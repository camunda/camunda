/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.tracing;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.spi.Codec;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SbeTracingCodec implements Codec<DirectBuffer> {
  private static final Format<DirectBuffer> FORMAT = new Format<>() {};
  private static final Logger LOGGER = LoggerFactory.getLogger(SbeTracingCodec.class);
  private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
  private static final int SERIALIZED_LENGTH = Long.BYTES * 4 + 1;

  private SbeTracingCodec() {}

  public static SbeTracingCodec codec() {
    return Singleton.INSTANCE;
  }

  public static Format<DirectBuffer> format() {
    return FORMAT;
  }

  public SpanContext decode(final DirectBuffer serialized) {
    if (serialized.capacity() == 0) {
      return null;
    }

    if (serialized.capacity() != SERIALIZED_LENGTH) {
      LOGGER.warn(
          "Expected serialized span context to be of length '{}', but was '{}'",
          SERIALIZED_LENGTH,
          serialized.capacity());
      return null;
    }

    return new JaegerSpanContext(
        serialized.getLong(0, BYTE_ORDER),
        serialized.getLong(Long.BYTES, BYTE_ORDER),
        serialized.getLong(Long.BYTES * 2, BYTE_ORDER),
        serialized.getLong(Long.BYTES * 3, BYTE_ORDER),
        serialized.getByte(Long.BYTES * 4));
  }

  public void encode(final SpanContext context, final DirectBuffer dest) {
    if (!(context instanceof JaegerSpanContext)) {
      LOGGER.warn("Only JaegerSpanContext is supported, but received {}", context.getClass());
      return;
    }

    final var spanContext = (JaegerSpanContext) context;
    final var buffer = newWriteBuffer();
    buffer
        .putLong(spanContext.getTraceIdHigh())
        .putLong(spanContext.getTraceIdLow())
        .putLong(spanContext.getSpanId())
        .putLong(spanContext.getParentId())
        .put(spanContext.getFlags());

    dest.wrap(new UnsafeBuffer(buffer.flip()), 0, buffer.capacity());
  }

  @Override
  public JaegerSpanContext extract(final DirectBuffer carrier) {
    return (JaegerSpanContext) decode(carrier);
  }

  @Override
  public void inject(final JaegerSpanContext spanContext, final DirectBuffer carrier) {
    encode(spanContext, carrier);
  }

  private ByteBuffer newWriteBuffer() {
    return ByteBuffer.allocate(SERIALIZED_LENGTH).order(BYTE_ORDER);
  }

  private static final class Singleton {
    private static final SbeTracingCodec INSTANCE = new SbeTracingCodec();
  }
}
