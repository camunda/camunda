/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.function.ToDoubleFunction;

/**
 * A mergeable part-of-whole ratio: every folded fact increments {@code total}, and a fact whose
 * measured value satisfies the {@link Comparison numerator predicate} (a comparison against a
 * threshold) also increments {@code matched}. The classic Optimize cohorts are ratio declarations —
 * an SLA-compliant share is {@code duration <= sla}, a no-incident share is {@code incidentCount ==
 * 0} — so there is one generic meter, not a class per cohort.
 *
 * <p>{@code merge} is component-wise addition of the two counters: commutative and associative, so
 * it pre-aggregates per source partition and combines across partitions exactly, like {@code
 * count}. The ratio itself is derived only on read ({@link RatioResult}).
 *
 * @param <F> the input fact type
 */
public final class RatioAggregateFunction<F>
    implements AggregateFunction<F, RatioAccumulator, RatioResult> {

  /** How a fact's measured value is compared against the threshold to qualify for the numerator. */
  public enum Comparison {
    LE,
    LT,
    GE,
    GT,
    EQ,
    NE;

    boolean test(final double measure, final double threshold) {
      return switch (this) {
        case LE -> measure <= threshold;
        case LT -> measure < threshold;
        case GE -> measure >= threshold;
        case GT -> measure > threshold;
        case EQ -> measure == threshold;
        case NE -> measure != threshold;
      };
    }
  }

  private final ToDoubleFunction<F> measure;
  private final Comparison comparison;
  private final double threshold;

  public RatioAggregateFunction(
      final ToDoubleFunction<F> measure, final Comparison comparison, final double threshold) {
    this.measure = measure;
    this.comparison = comparison;
    this.threshold = threshold;
  }

  @Override
  public RatioAccumulator createAccumulator() {
    return RatioAccumulator.empty();
  }

  @Override
  public RatioAccumulator add(final F value, final RatioAccumulator acc) {
    final long matched = comparison.test(measure.applyAsDouble(value), threshold) ? 1L : 0L;
    return new RatioAccumulator(acc.matched() + matched, acc.total() + 1L);
  }

  @Override
  public RatioAccumulator merge(final RatioAccumulator a, final RatioAccumulator b) {
    return new RatioAccumulator(a.matched() + b.matched(), a.total() + b.total());
  }

  @Override
  public RatioResult getResult(final RatioAccumulator acc) {
    return RatioResult.of(acc);
  }
}
