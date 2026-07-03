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
import io.camunda.zeebe.msgpack.property.LongProperty;

/** Record flyweight for {@link ExecutionTimeAccumulator} — the four longs the rollup persists. */
public final class ExecutionTimeAccumulatorValue extends UnpackedObject
    implements RecordValue<ExecutionTimeAccumulator> {

  private final LongProperty countProp = new LongProperty("count", 0L);
  private final LongProperty totalMsProp = new LongProperty("totalMs", 0L);
  private final LongProperty minMsProp = new LongProperty("minMs", 0L);
  private final LongProperty maxMsProp = new LongProperty("maxMs", 0L);

  public ExecutionTimeAccumulatorValue() {
    super(4);
    declareProperty(countProp)
        .declareProperty(totalMsProp)
        .declareProperty(minMsProp)
        .declareProperty(maxMsProp);
  }

  @Override
  public ExecutionTimeAccumulatorValue wrapValue(final ExecutionTimeAccumulator acc) {
    countProp.setValue(acc.count());
    totalMsProp.setValue(acc.totalMs());
    minMsProp.setValue(acc.minMs());
    maxMsProp.setValue(acc.maxMs());
    return this;
  }

  @Override
  public ExecutionTimeAccumulator value() {
    return new ExecutionTimeAccumulator(
        countProp.getValue(), totalMsProp.getValue(), minMsProp.getValue(), maxMsProp.getValue());
  }
}
