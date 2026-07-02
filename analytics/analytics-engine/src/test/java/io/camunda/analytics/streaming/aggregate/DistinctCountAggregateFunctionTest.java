/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.apache.datasketches.hll.HllSketch;
import org.junit.jupiter.api.Test;

final class DistinctCountAggregateFunctionTest {

  private final DistinctCountAggregateFunction<String> distinct =
      new DistinctCountAggregateFunction<>(Function.identity());
  private final HllSketchCodec codec = new HllSketchCodec();

  @Test
  void shouldEstimateDistinctCountIgnoringDuplicates() {
    // given — 1000 distinct values, each added twice
    HllSketch acc = distinct.createAccumulator();
    for (int i = 0; i < 1000; i++) {
      acc = distinct.add("value-" + i, acc);
      acc = distinct.add("value-" + i, acc);
    }

    // when
    final DistinctCountResult result = distinct.getResult(acc);

    // then — within HLL error (~1.6% RSE at lgK=12), and the interval brackets the estimate
    assertThat(result.estimate()).isBetween(950L, 1050L);
    assertThat(result.lowerBound()).isLessThanOrEqualTo(result.estimate());
    assertThat(result.upperBound()).isGreaterThanOrEqualTo(result.estimate());
  }

  @Test
  void shouldMergeDisjointSetsAdditively() {
    // given — two disjoint sets of 500 in separate accumulators
    HllSketch a = distinct.createAccumulator();
    HllSketch b = distinct.createAccumulator();
    for (int i = 0; i < 500; i++) {
      a = distinct.add("a-" + i, a);
      b = distinct.add("b-" + i, b);
    }

    // when merged
    final DistinctCountResult result = distinct.getResult(distinct.merge(a, b));

    // then the union is about 1000 distinct
    assertThat(result.estimate()).isBetween(950L, 1050L);
  }

  @Test
  void shouldIgnoreNullValues() {
    // given
    final DistinctCountAggregateFunction<String> nullable =
        new DistinctCountAggregateFunction<>(s -> s.isEmpty() ? null : s);
    HllSketch acc = nullable.createAccumulator();

    // when — empty strings map to null and are skipped
    acc = nullable.add("", acc);
    acc = nullable.add("x", acc);
    acc = nullable.add("", acc);

    // then only "x" counted
    assertThat(nullable.getResult(acc).estimate()).isEqualTo(1L);
  }

  @Test
  void shouldRoundTripThroughCodec() {
    // given
    HllSketch acc = distinct.createAccumulator();
    for (int i = 0; i < 1000; i++) {
      acc = distinct.add("value-" + i, acc);
    }

    // when
    final HllSketch restored = codec.decode(codec.encode(acc));

    // then the estimate is preserved
    assertThat(distinct.getResult(restored).estimate())
        .isEqualTo(distinct.getResult(acc).estimate());
  }
}
