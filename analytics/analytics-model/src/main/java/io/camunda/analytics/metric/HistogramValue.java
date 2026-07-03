/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteOrder;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Record flyweight for the {@link HistogramAggregateFunction} accumulator: the bucket-count {@code
 * long[]} packed into a single {@link BinaryProperty} as fixed little-endian longs, so the wire
 * layout is deterministic and stable across nodes. The bucket count is recovered from the buffer
 * length; the thresholds themselves live in the meter declaration, not per cell.
 */
public final class HistogramValue extends UnpackedObject implements RecordValue<long[]> {

  private final BinaryProperty bucketsProp = new BinaryProperty("buckets");

  public HistogramValue() {
    super(1);
    declareProperty(bucketsProp);
  }

  @Override
  public HistogramValue wrapValue(final long[] buckets) {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[Long.BYTES * buckets.length]);
    for (int i = 0; i < buckets.length; i++) {
      buffer.putLong(i * Long.BYTES, buckets[i], ByteOrder.LITTLE_ENDIAN);
    }
    bucketsProp.setValue(buffer);
    return this;
  }

  @Override
  public long[] value() {
    final byte[] bytes = BufferUtil.bufferAsArray(bucketsProp.getValue());
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
    final long[] buckets = new long[bytes.length / Long.BYTES];
    for (int i = 0; i < buckets.length; i++) {
      buckets[i] = buffer.getLong(i * Long.BYTES, ByteOrder.LITTLE_ENDIAN);
    }
    return buckets;
  }
}
