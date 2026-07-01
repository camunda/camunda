/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.util.function.Predicate;

/**
 * A percentage/ratio metric as a mergeable {@link AggregateFunction}: the fraction of facts that
 * satisfy a predicate. Parameterized by the numerator predicate (e.g. {@code durationMs <= slaMs},
 * or "completed without an incident"); the denominator is every fact folded in. The accumulator
 * holds a matched count and a total, both additive, so {@code merge} is commutative and associative
 * and the ratio pre-aggregates and combines across partitions exactly.
 *
 * <p>This is the streaming counterpart of Optimize's {@code PERCENTAGE} process-instance view — the
 * {@code percentSLAMet} and {@code percentNoIncidents} tiles on the default dashboards.
 *
 * @param <F> the fact type
 */
public final class RatioAggregateFunction<F>
    implements AggregateFunction<F, RatioAccumulator, RatioResult> {

  private final Predicate<F> numerator;

  public RatioAggregateFunction(final Predicate<F> numerator) {
    this.numerator = numerator;
  }

  @Override
  public RatioAccumulator createAccumulator() {
    return RatioAccumulator.empty();
  }

  @Override
  public RatioAccumulator add(final F fact, final RatioAccumulator acc) {
    final long matched = acc.matched() + (numerator.test(fact) ? 1L : 0L);
    return new RatioAccumulator(matched, acc.total() + 1L);
  }

  @Override
  public RatioAccumulator merge(final RatioAccumulator a, final RatioAccumulator b) {
    return new RatioAccumulator(a.matched() + b.matched(), a.total() + b.total());
  }

  @Override
  public RatioResult getResult(final RatioAccumulator acc) {
    final double ratio = acc.total() == 0L ? 0.0 : (double) acc.matched() / acc.total();
    return new RatioResult(acc.matched(), acc.total(), ratio);
  }
}
