/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.tracing;

import io.opentracing.propagation.Binary;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;

public class SbeTracingAdapter implements Binary {
  private final DirectBuffer view;

  public SbeTracingAdapter(final DirectBuffer view) {
    this.view = view;
  }

  @Override
  public ByteBuffer extractionBuffer() {
    final var offset = view.wrapAdjustment();
    final var buffer = view.byteBuffer();
    if (buffer == null) {
      return ByteBuffer.wrap(view.byteArray(), offset, view.capacity()).order(ByteOrder.BIG_ENDIAN).slice();
    }

    return buffer.position(offset).slice().order(ByteOrder.BIG_ENDIAN);
  }

  @Override
  public ByteBuffer injectionBuffer(final int length) {
    final var buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.BIG_ENDIAN);
    view.wrap(buffer, 0, length);
    return buffer;
  }
}
