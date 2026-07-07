/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.datasketches.hll.HllSketch;

/**
 * Record flyweight for the {@link DistinctCountAggregateFunction} accumulator: an HLL sketch held
 * in a single {@link BinaryProperty} as its own compact serialized form. Round-tripping yields a
 * functionally equivalent sketch (same estimate); equivalence is by estimate rather than {@code
 * equals}.
 */
public final class HllSketchValue extends UnpackedObject implements RecordValue<HllSketch> {

  private final BinaryProperty sketchProp = new BinaryProperty("sketch");

  public HllSketchValue() {
    super(1);
    declareProperty(sketchProp);
  }

  @Override
  public HllSketchValue wrapValue(final HllSketch sketch) {
    sketchProp.setValue(new UnsafeBuffer(sketch.toCompactByteArray()));
    return this;
  }

  @Override
  public HllSketch value() {
    return HllSketch.heapify(SketchMemory.memoryOf(sketchProp.getValue()));
  }

  /**
   * Merge-only decode: a read-only sketch wrapped directly over the serialized compact bytes — no
   * array copy, no heap materialization. Valid only as the delta argument of a merge (a {@code
   * Union} reads it fine); it aliases {@code bytes} and rejects updates.
   */
  @Override
  public HllSketch fromBytesForMerge(final byte[] bytes) {
    wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return HllSketch.wrap(SketchMemory.memoryOf(sketchProp.getValue()));
  }
}
