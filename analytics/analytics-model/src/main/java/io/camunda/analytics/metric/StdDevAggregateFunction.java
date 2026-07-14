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
 * The {@code stddev} meter as a mergeable {@link AggregateFunction}: folds a fact's measured value
 * into the additive {@code (count, sum, sumSq)} moments and derives the <em>population</em>
 * standard deviation on read (see {@link StdDevAccumulator} for the population-vs-sample choice and
 * the exact recompose formula the pushdown shares).
 *
 * @param <F> the fact type
 */
public final class StdDevAggregateFunction<F>
    implements AggregateFunction<F, StdDevAccumulator, Double> {

  private final ToLongFunction<F> value;

  public StdDevAggregateFunction(final ToLongFunction<F> value) {
    this.value = value;
  }

  @Override
  public StdDevAccumulator createAccumulator() {
    return StdDevAccumulator.empty();
  }

  @Override
  public StdDevAccumulator add(final F fact, final StdDevAccumulator acc) {
    final long observed = value.applyAsLong(fact);
    return new StdDevAccumulator(
        acc.count() + 1, acc.sum() + observed, acc.sumSq() + observed * observed);
  }

  @Override
  public StdDevAccumulator merge(final StdDevAccumulator a, final StdDevAccumulator b) {
    return new StdDevAccumulator(a.count() + b.count(), a.sum() + b.sum(), a.sumSq() + b.sumSq());
  }

  @Override
  public Double getResult(final StdDevAccumulator acc) {
    return StdDevAccumulator.populationStdDev(acc.count(), acc.sum(), acc.sumSq());
  }
}
