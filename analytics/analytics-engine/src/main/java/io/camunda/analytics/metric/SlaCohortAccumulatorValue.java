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

/** Record flyweight for the {@link SlaCohortAccumulator}: started, met, settled (three longs). */
public final class SlaCohortAccumulatorValue extends UnpackedObject
    implements RecordValue<SlaCohortAccumulator> {

  private final LongProperty startedProp = new LongProperty("started", 0L);
  private final LongProperty metProp = new LongProperty("met", 0L);
  private final LongProperty settledProp = new LongProperty("settled", 0L);

  public SlaCohortAccumulatorValue() {
    super(3);
    declareProperty(startedProp).declareProperty(metProp).declareProperty(settledProp);
  }

  @Override
  public SlaCohortAccumulatorValue wrapValue(final SlaCohortAccumulator acc) {
    startedProp.setValue(acc.started());
    metProp.setValue(acc.met());
    settledProp.setValue(acc.settled());
    return this;
  }

  @Override
  public SlaCohortAccumulator value() {
    return new SlaCohortAccumulator(
        startedProp.getValue(), metProp.getValue(), settledProp.getValue());
  }
}
