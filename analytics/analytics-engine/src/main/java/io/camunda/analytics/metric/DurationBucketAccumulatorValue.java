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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.ArrayList;
import java.util.List;

/** Record flyweight for {@link DurationBucketAccumulator}: started plus one count per band. */
public final class DurationBucketAccumulatorValue extends UnpackedObject
    implements RecordValue<DurationBucketAccumulator> {

  private final LongProperty startedProp = new LongProperty("started", 0L);
  private final ArrayProperty<LongValue> bucketsProp =
      new ArrayProperty<>("buckets", LongValue::new);

  public DurationBucketAccumulatorValue() {
    super(2);
    declareProperty(startedProp).declareProperty(bucketsProp);
  }

  @Override
  public DurationBucketAccumulatorValue wrapValue(final DurationBucketAccumulator acc) {
    reset();
    startedProp.setValue(acc.started());
    for (final long bucket : acc.buckets()) {
      bucketsProp.add().setValue(bucket);
    }
    return this;
  }

  @Override
  public DurationBucketAccumulator value() {
    final List<Long> buckets = new ArrayList<>();
    for (final LongValue bucket : bucketsProp) {
      buckets.add(bucket.getValue());
    }
    final long[] out = new long[buckets.size()];
    for (int i = 0; i < out.length; i++) {
      out[i] = buckets.get(i);
    }
    return new DurationBucketAccumulator(startedProp.getValue(), out);
  }
}
