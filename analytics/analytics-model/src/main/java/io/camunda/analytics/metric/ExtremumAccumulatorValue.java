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

/**
 * Record flyweight for {@link ExtremumAccumulator} — the count/extremum pair the rollup persists.
 */
public final class ExtremumAccumulatorValue extends UnpackedObject
    implements RecordValue<ExtremumAccumulator> {

  private final LongProperty countProp = new LongProperty("count", 0L);
  private final LongProperty extremumProp = new LongProperty("extremum", 0L);

  public ExtremumAccumulatorValue() {
    super(2);
    declareProperty(countProp).declareProperty(extremumProp);
  }

  @Override
  public ExtremumAccumulatorValue wrapValue(final ExtremumAccumulator acc) {
    countProp.setValue(acc.count());
    extremumProp.setValue(acc.extremum());
    return this;
  }

  @Override
  public ExtremumAccumulator value() {
    return new ExtremumAccumulator(countProp.getValue(), extremumProp.getValue());
  }
}
