/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import java.util.function.ToLongFunction;

/**
 * A mergeable running sum of a signed long extracted from each value. With a {@code +1 / -1}
 * extractor and a single (all-time) window it is a gauge — e.g. in-flight instances = sum of {@code
 * +1} on start and {@code -1} on completion. {@code merge} is addition, so it pre-aggregates and
 * combines across partitions exactly like {@link CountAggregateFunction}.
 *
 * @param <F> the value type
 */
public final class SumAggregateFunction<F> implements AggregateFunction<F, Long, Long> {

  private final ToLongFunction<F> value;

  public SumAggregateFunction(final ToLongFunction<F> value) {
    this.value = value;
  }

  @Override
  public Long createAccumulator() {
    return 0L;
  }

  @Override
  public Long add(final F item, final Long acc) {
    return acc + value.applyAsLong(item);
  }

  @Override
  public Long merge(final Long a, final Long b) {
    return a + b;
  }

  @Override
  public Long getResult(final Long acc) {
    return acc;
  }
}
