/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import java.nio.ByteBuffer;

/** Byte codec for the {@link RatioAggregateFunction} accumulator (matched and total, two longs). */
public final class RatioAccumulatorCodec implements Codec<RatioAccumulator> {

  private static final int SIZE = Long.BYTES * 2;

  @Override
  public byte[] encode(final RatioAccumulator acc) {
    return ByteBuffer.allocate(SIZE).putLong(acc.matched()).putLong(acc.total()).array();
  }

  @Override
  public RatioAccumulator decode(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new RatioAccumulator(buffer.getLong(), buffer.getLong());
  }
}
