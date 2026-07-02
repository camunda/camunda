/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.memory.Memory;

/**
 * Byte codec for the {@link QuantileAggregateFunction} accumulator, so the durable rollup can
 * persist a KLL sketch as a cell value. The sketch's own serialized form is compact and
 * deterministic; round-tripping yields a functionally equivalent sketch (same observations, same
 * estimates) — DataSketches sketches do not implement value {@code equals}, so equivalence is by
 * estimate rather than by {@code equals}, unlike the record-based codecs.
 */
public final class KllDoublesSketchCodec implements Codec<KllDoublesSketch> {

  @Override
  public byte[] encode(final KllDoublesSketch sketch) {
    return sketch.toByteArray();
  }

  @Override
  public KllDoublesSketch decode(final byte[] bytes) {
    return KllDoublesSketch.heapify(Memory.wrap(bytes));
  }
}
