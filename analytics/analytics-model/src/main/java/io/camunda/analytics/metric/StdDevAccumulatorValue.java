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

/** Record flyweight for {@link StdDevAccumulator} — the three moments the rollup persists. */
public final class StdDevAccumulatorValue extends UnpackedObject
    implements RecordValue<StdDevAccumulator> {

  private final LongProperty countProp = new LongProperty("count", 0L);
  private final LongProperty sumProp = new LongProperty("sum", 0L);
  private final LongProperty sumSqProp = new LongProperty("sumSq", 0L);

  public StdDevAccumulatorValue() {
    super(3);
    declareProperty(countProp).declareProperty(sumProp).declareProperty(sumSqProp);
  }

  @Override
  public StdDevAccumulatorValue wrapValue(final StdDevAccumulator acc) {
    countProp.setValue(acc.count());
    sumProp.setValue(acc.sum());
    sumSqProp.setValue(acc.sumSq());
    return this;
  }

  @Override
  public StdDevAccumulator value() {
    return new StdDevAccumulator(countProp.getValue(), sumProp.getValue(), sumSqProp.getValue());
  }
}
