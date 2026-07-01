/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import io.camunda.analytics.streaming.aggregate.Codec;
import java.nio.ByteBuffer;

/** Byte codec for {@link DurationBucketAccumulator}: started, band count, then each band. */
public final class DurationBucketAccumulatorCodec implements Codec<DurationBucketAccumulator> {

  @Override
  public byte[] encode(final DurationBucketAccumulator acc) {
    final long[] buckets = acc.buckets();
    final ByteBuffer buffer =
        ByteBuffer.allocate(Long.BYTES + Integer.BYTES + buckets.length * Long.BYTES);
    buffer.putLong(acc.started());
    buffer.putInt(buckets.length);
    for (final long b : buckets) {
      buffer.putLong(b);
    }
    return buffer.array();
  }

  @Override
  public DurationBucketAccumulator decode(final byte[] bytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(bytes);
    final long started = buffer.getLong();
    final long[] buckets = new long[buffer.getInt()];
    for (int i = 0; i < buckets.length; i++) {
      buckets[i] = buffer.getLong();
    }
    return new DurationBucketAccumulator(started, buckets);
  }
}
