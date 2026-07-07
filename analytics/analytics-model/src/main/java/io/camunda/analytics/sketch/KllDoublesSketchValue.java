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
import org.apache.datasketches.kll.KllDoublesSketch;

/**
 * Record flyweight for the {@link QuantileAggregateFunction} accumulator: a KLL sketch held in a
 * single {@link BinaryProperty} as its own compact serialized form. Round-tripping yields a
 * functionally equivalent sketch (same estimates); equivalence is by estimate rather than {@code
 * equals}.
 */
public final class KllDoublesSketchValue extends UnpackedObject
    implements RecordValue<KllDoublesSketch> {

  private final BinaryProperty sketchProp = new BinaryProperty("sketch");

  public KllDoublesSketchValue() {
    super(1);
    declareProperty(sketchProp);
  }

  @Override
  public KllDoublesSketchValue wrapValue(final KllDoublesSketch sketch) {
    sketchProp.setValue(new UnsafeBuffer(sketch.toByteArray()));
    return this;
  }

  @Override
  public KllDoublesSketch value() {
    return KllDoublesSketch.heapify(SketchMemory.memoryOf(sketchProp.getValue()));
  }

  /**
   * Merge-only decode: a read-only sketch wrapped directly over the serialized bytes — no array
   * copy, no heap materialization. Valid only as the delta argument of a merge; it aliases {@code
   * bytes} and rejects updates.
   */
  @Override
  public KllDoublesSketch fromBytesForMerge(final byte[] bytes) {
    wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return KllDoublesSketch.wrap(SketchMemory.memoryOf(sketchProp.getValue()));
  }
}
