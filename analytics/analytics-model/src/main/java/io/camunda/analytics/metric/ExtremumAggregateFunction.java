/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.function.ToLongFunction;

/**
 * The standalone {@code min}/{@code max} meters as one mergeable {@link AggregateFunction},
 * parameterized by direction: the running extremum of a fact's measured value, with a count so an
 * empty accumulator reads {@code 0} rather than its identity sentinel. Min and max are commutative,
 * associative and additive-pushable ({@code MIN}/{@code MAX} columns), so they pre-aggregate across
 * partitions and roll up in the store exactly like {@code sum}.
 *
 * @param <F> the fact type
 */
public final class ExtremumAggregateFunction<F>
    implements AggregateFunction<F, ExtremumAccumulator, Long> {

  /** Which extremum this meter tracks. */
  public enum Direction {
    MIN,
    MAX
  }

  private final ToLongFunction<F> value;
  private final Direction direction;

  public ExtremumAggregateFunction(final ToLongFunction<F> value, final Direction direction) {
    this.value = value;
    this.direction = direction;
  }

  @Override
  public ExtremumAccumulator createAccumulator() {
    return direction == Direction.MIN
        ? ExtremumAccumulator.emptyMin()
        : ExtremumAccumulator.emptyMax();
  }

  @Override
  public ExtremumAccumulator add(final F fact, final ExtremumAccumulator acc) {
    return new ExtremumAccumulator(acc.count() + 1, pick(acc.extremum(), value.applyAsLong(fact)));
  }

  @Override
  public ExtremumAccumulator merge(final ExtremumAccumulator a, final ExtremumAccumulator b) {
    return new ExtremumAccumulator(a.count() + b.count(), pick(a.extremum(), b.extremum()));
  }

  @Override
  public Long getResult(final ExtremumAccumulator acc) {
    return acc.count() == 0L ? 0L : acc.extremum();
  }

  private long pick(final long a, final long b) {
    return direction == Direction.MIN ? Math.min(a, b) : Math.max(a, b);
  }
}
