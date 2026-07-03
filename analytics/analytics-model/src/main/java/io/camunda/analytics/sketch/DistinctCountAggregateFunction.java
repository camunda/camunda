/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.function.Function;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.hll.Union;

/**
 * Approximate distinct count (cardinality) of a value as a mergeable {@link AggregateFunction},
 * backed by an HLL sketch. Parameterized by an extractor that maps a fact to the value whose
 * distinct occurrences are counted (e.g. the {@code bpmnProcessId}, an assignee, a variable value);
 * a {@code null} value is ignored. The accumulator is the sketch; its {@code merge} is a register-
 * wise union, so — like HLL generally — the merge is exact, commutative and associative even though
 * the count itself is an estimate. That is what makes distinct-count pre-aggregatable across
 * partitions with fixed-size state.
 *
 * @param <F> the fact type
 */
public final class DistinctCountAggregateFunction<F>
    implements AggregateFunction<F, HllSketch, DistinctCountResult> {

  /** log2 of the number of registers; 12 gives ~1.6% relative standard error at a few KB. */
  private static final int LG_CONFIG_K = 12;

  /** Standard deviations for the returned confidence bounds (2 ≈ 95%). */
  private static final int NUM_STD_DEV = 2;

  private final Function<F, String> item;

  public DistinctCountAggregateFunction(final Function<F, String> item) {
    this.item = item;
  }

  @Override
  public HllSketch createAccumulator() {
    return new HllSketch(LG_CONFIG_K);
  }

  @Override
  public HllSketch add(final F fact, final HllSketch sketch) {
    final String value = item.apply(fact);
    if (value != null) {
      sketch.update(value);
    }
    return sketch;
  }

  @Override
  public HllSketch merge(final HllSketch a, final HllSketch b) {
    final Union union = new Union(LG_CONFIG_K);
    union.update(a);
    union.update(b);
    return union.getResult();
  }

  @Override
  public DistinctCountResult getResult(final HllSketch sketch) {
    return new DistinctCountResult(
        Math.round(sketch.getEstimate()),
        Math.round(sketch.getLowerBound(NUM_STD_DEV)),
        Math.round(sketch.getUpperBound(NUM_STD_DEV)));
  }
}
