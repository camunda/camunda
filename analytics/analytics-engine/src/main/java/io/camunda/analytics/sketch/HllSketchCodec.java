/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.Codec;
import org.apache.datasketches.hll.HllSketch;

/**
 * Byte codec for the {@link DistinctCountAggregateFunction} accumulator, so the durable rollup can
 * persist an HLL sketch as a cell value. Uses the compact serialized form; round-tripping yields a
 * functionally equivalent sketch (same estimate). As with the other sketch codecs, equivalence is
 * by estimate rather than by {@code equals}.
 */
public final class HllSketchCodec implements Codec<HllSketch> {

  @Override
  public byte[] encode(final HllSketch sketch) {
    return sketch.toCompactByteArray();
  }

  @Override
  public HllSketch decode(final byte[] bytes) {
    return HllSketch.heapify(bytes);
  }
}
