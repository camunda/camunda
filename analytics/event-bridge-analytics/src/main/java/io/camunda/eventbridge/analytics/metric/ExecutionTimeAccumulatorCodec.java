/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.Codec;
import java.nio.ByteBuffer;

/**
 * Byte codec for {@link ExecutionTimeAccumulator} — the four longs (count, total, min, max) the
 * durable rollup persists as a cell value.
 */
public final class ExecutionTimeAccumulatorCodec implements Codec<ExecutionTimeAccumulator> {

  private static final int SIZE = Long.BYTES * 4;

  @Override
  public byte[] encode(final ExecutionTimeAccumulator acc) {
    return ByteBuffer.allocate(SIZE)
        .putLong(acc.count())
        .putLong(acc.totalMs())
        .putLong(acc.minMs())
        .putLong(acc.maxMs())
        .array();
  }

  @Override
  public ExecutionTimeAccumulator decode(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new ExecutionTimeAccumulator(
        buffer.getLong(), buffer.getLong(), buffer.getLong(), buffer.getLong());
  }
}
