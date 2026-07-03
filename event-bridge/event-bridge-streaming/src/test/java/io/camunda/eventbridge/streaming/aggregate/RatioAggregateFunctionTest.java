/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

final class RatioAggregateFunctionTest {

  // "SLA met" = value at or below 100
  private final RatioAggregateFunction<Integer> slaMet =
      new RatioAggregateFunction<>(v -> v <= 100);
  private final RatioAccumulatorRecordValue codec = new RatioAccumulatorRecordValue();

  @Test
  void shouldComputeFractionSatisfyingThePredicate() {
    // given — 3 of 4 within the threshold
    RatioAccumulator acc = slaMet.createAccumulator();
    for (final int v : new int[] {50, 80, 100, 250}) {
      acc = slaMet.add(v, acc);
    }

    // when
    final RatioResult result = slaMet.getResult(acc);

    // then
    assertThat(result.matched()).isEqualTo(3L);
    assertThat(result.total()).isEqualTo(4L);
    assertThat(result.ratio()).isCloseTo(0.75, within(1e-9));
  }

  @Test
  void shouldMergePartialsAdditively() {
    // given — two batches folded separately
    RatioAccumulator a = slaMet.add(250, slaMet.add(50, slaMet.createAccumulator()));
    RatioAccumulator b = slaMet.add(100, slaMet.add(80, slaMet.createAccumulator()));

    // when
    final RatioResult result = slaMet.getResult(slaMet.merge(a, b));

    // then — 3 matched of 4 total across both
    assertThat(result.matched()).isEqualTo(3L);
    assertThat(result.total()).isEqualTo(4L);
  }

  @Test
  void shouldBeZeroWhenEmpty() {
    // when
    final RatioResult result = slaMet.getResult(slaMet.createAccumulator());

    // then
    assertThat(result.total()).isZero();
    assertThat(result.ratio()).isZero();
  }

  @Test
  void shouldRoundTripThroughRecordValue() {
    // given
    final RatioAccumulator acc = new RatioAccumulator(7L, 10L);

    // when / then
    assertThat(codec.fromBytes(codec.toBytes(acc))).isEqualTo(acc);
  }
}
