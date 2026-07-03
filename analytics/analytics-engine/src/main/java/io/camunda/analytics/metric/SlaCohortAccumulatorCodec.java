/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.Codec;
import java.nio.ByteBuffer;

/** Byte codec for the {@link SlaCohortAccumulator}: started, met, settled (three longs). */
public final class SlaCohortAccumulatorCodec implements Codec<SlaCohortAccumulator> {

  private static final int SIZE = Long.BYTES * 3;

  @Override
  public byte[] encode(final SlaCohortAccumulator acc) {
    return ByteBuffer.allocate(SIZE)
        .putLong(acc.started())
        .putLong(acc.met())
        .putLong(acc.settled())
        .array();
  }

  @Override
  public SlaCohortAccumulator decode(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new SlaCohortAccumulator(buffer.getLong(), buffer.getLong(), buffer.getLong());
  }
}
