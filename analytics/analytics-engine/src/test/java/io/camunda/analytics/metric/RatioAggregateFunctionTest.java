/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.metric.RatioAggregateFunction.Comparison;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

final class RatioAggregateFunctionTest {

  private static final ToDoubleFunction<Long> IDENTITY = Long::doubleValue;

  @Test
  void shouldCountMatchedUnderThreshold() {
    // given — SLA compliance: duration <= 300
    final RatioAggregateFunction<Long> fn =
        new RatioAggregateFunction<>(IDENTITY, Comparison.LE, 300.0);

    // when — three within SLA, two breaching
    RatioAccumulator acc = fn.createAccumulator();
    for (final long duration : new long[] {100, 250, 300, 400, 500}) {
      acc = fn.add(duration, acc);
    }

    // then
    assertThat(acc.matched()).isEqualTo(3L);
    assertThat(acc.total()).isEqualTo(5L);
    assertThat(fn.getResult(acc).ratio()).isCloseTo(0.6, within(1e-9));
  }

  @Test
  void shouldMergeCommutativelyAndAssociatively() {
    // given
    final RatioAggregateFunction<Long> fn =
        new RatioAggregateFunction<>(IDENTITY, Comparison.LE, 300.0);
    final RatioAccumulator a = new RatioAccumulator(2, 3);
    final RatioAccumulator b = new RatioAccumulator(1, 4);

    // when / then — merge is component-wise addition, order-independent
    assertThat(fn.merge(a, b)).isEqualTo(new RatioAccumulator(3, 7));
    assertThat(fn.merge(a, b)).isEqualTo(fn.merge(b, a));
  }

  @Test
  void shouldYieldZeroRatioForEmptyAccumulator() {
    // given
    final RatioAggregateFunction<Long> fn =
        new RatioAggregateFunction<>(IDENTITY, Comparison.EQ, 0.0);

    // when / then
    assertThat(fn.getResult(fn.createAccumulator()).ratio()).isEqualTo(0.0);
  }
}
