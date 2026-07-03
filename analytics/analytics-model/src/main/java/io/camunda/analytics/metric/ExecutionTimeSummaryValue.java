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
 * Record flyweight for the composite {@link ExecutionTimeSummary}: the exact count/total/min/max as
 * msgpack longs plus the KLL sketch in a single {@link BinaryProperty} as its own serialized form.
 * Round-tripping yields exact stats and a functionally equivalent sketch (equal estimates).
 */
public final class ExecutionTimeSummaryValue extends UnpackedObject
    implements RecordValue<ExecutionTimeSummary> {

  private final LongProperty countProp = new LongProperty("count", 0L);
  private final LongProperty totalProp = new LongProperty("total", 0L);
  private final LongProperty minProp = new LongProperty("min", Long.MAX_VALUE);
  private final LongProperty maxProp = new LongProperty("max", Long.MIN_VALUE);
  private final BinaryProperty sketchProp = new BinaryProperty("sketch");

  public ExecutionTimeSummaryValue() {
    super(5);
    declareProperty(countProp)
        .declareProperty(totalProp)
        .declareProperty(minProp)
        .declareProperty(maxProp)
        .declareProperty(sketchProp);
  }

  @Override
  public ExecutionTimeSummaryValue wrapValue(final ExecutionTimeSummary summary) {
    countProp.setValue(summary.count());
    totalProp.setValue(summary.totalMs());
    minProp.setValue(summary.minMs());
    maxProp.setValue(summary.maxMs());
    sketchProp.setValue(new UnsafeBuffer(summary.sketch().toByteArray()));
    return this;
  }

  @Override
  public ExecutionTimeSummary value() {
    final KllDoublesSketch sketch =
        KllDoublesSketch.heapify(Memory.wrap(BufferUtil.bufferAsArray(sketchProp.getValue())));
    return new ExecutionTimeSummary(
        countProp.getValue(), totalProp.getValue(), minProp.getValue(), maxProp.getValue(), sketch);
  }
}
