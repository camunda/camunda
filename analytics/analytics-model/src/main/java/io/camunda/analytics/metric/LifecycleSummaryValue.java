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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.memory.Memory;

/**
 * Record flyweight for the composite {@link LifecycleSummary}: the three transition counters and
 * the duration summary's exact stats as msgpack longs, plus the duration KLL sketch in a single
 * {@link BinaryProperty}. Round-tripping yields exact counts/stats and a functionally equivalent
 * sketch.
 */
public final class LifecycleSummaryValue extends UnpackedObject
    implements RecordValue<LifecycleSummary> {

  private final LongProperty activatedProp = new LongProperty("activated", 0L);
  private final LongProperty completedProp = new LongProperty("completed", 0L);
  private final LongProperty terminatedProp = new LongProperty("terminated", 0L);
  private final LongProperty countProp = new LongProperty("count", 0L);
  private final LongProperty totalProp = new LongProperty("total", 0L);
  private final LongProperty minProp = new LongProperty("min", Long.MAX_VALUE);
  private final LongProperty maxProp = new LongProperty("max", Long.MIN_VALUE);
  private final BinaryProperty sketchProp = new BinaryProperty("sketch");

  public LifecycleSummaryValue() {
    super(8);
    declareProperty(activatedProp)
        .declareProperty(completedProp)
        .declareProperty(terminatedProp)
        .declareProperty(countProp)
        .declareProperty(totalProp)
        .declareProperty(minProp)
        .declareProperty(maxProp)
        .declareProperty(sketchProp);
  }

  @Override
  public LifecycleSummaryValue wrapValue(final LifecycleSummary summary) {
    final ExecutionTimeSummary duration = summary.duration();
    activatedProp.setValue(summary.activated());
    completedProp.setValue(summary.completed());
    terminatedProp.setValue(summary.terminated());
    countProp.setValue(duration.count());
    totalProp.setValue(duration.totalMs());
    minProp.setValue(duration.minMs());
    maxProp.setValue(duration.maxMs());
    sketchProp.setValue(new UnsafeBuffer(duration.sketch().toByteArray()));
    return this;
  }

  @Override
  public LifecycleSummary value() {
    final KllDoublesSketch sketch =
        KllDoublesSketch.heapify(Memory.wrap(BufferUtil.bufferAsArray(sketchProp.getValue())));
    final ExecutionTimeSummary duration =
        new ExecutionTimeSummary(
            countProp.getValue(),
            totalProp.getValue(),
            minProp.getValue(),
            maxProp.getValue(),
            sketch);
    return new LifecycleSummary(
        activatedProp.getValue(), completedProp.getValue(), terminatedProp.getValue(), duration);
  }
}
