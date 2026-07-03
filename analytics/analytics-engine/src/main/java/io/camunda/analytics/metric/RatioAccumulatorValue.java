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

/** Record flyweight for {@link RatioAccumulator} — the two additive longs the rollup persists. */
public final class RatioAccumulatorValue extends UnpackedObject
    implements RecordValue<RatioAccumulator> {

  private final LongProperty matchedProp = new LongProperty("matched", 0L);
  private final LongProperty totalProp = new LongProperty("total", 0L);

  public RatioAccumulatorValue() {
    super(2);
    declareProperty(matchedProp).declareProperty(totalProp);
  }

  @Override
  public RatioAccumulatorValue wrapValue(final RatioAccumulator acc) {
    matchedProp.setValue(acc.matched());
    totalProp.setValue(acc.total());
    return this;
  }

  @Override
  public RatioAccumulator value() {
    return new RatioAccumulator(matchedProp.getValue(), totalProp.getValue());
  }
}
