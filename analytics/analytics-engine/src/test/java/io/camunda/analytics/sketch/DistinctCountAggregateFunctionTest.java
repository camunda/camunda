/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.apache.datasketches.hll.HllSketch;
import org.junit.jupiter.api.Test;

final class DistinctCountAggregateFunctionTest {

  private final DistinctCountAggregateFunction<String> distinct =
      new DistinctCountAggregateFunction<>(Function.identity());
  private final HllSketchValue codec = new HllSketchValue();

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
  void shouldRoundTripThroughRecordValue() {
    // given
    HllSketch acc = distinct.createAccumulator();
    for (int i = 0; i < 1000; i++) {
      acc = distinct.add("value-" + i, acc);
    }

    // when
    final HllSketch restored = codec.fromBytes(codec.toBytes(acc));

    // then the estimate is preserved
    assertThat(distinct.getResult(restored).estimate())
        .isEqualTo(distinct.getResult(acc).estimate());
  }

  @Test
  void shouldMergeIntoMatchingThePureMerge() {
    // given two disjoint partials (HLL cannot fold in place, so mergeInto is the pure union)
    final HllSketch a = distinctSet("a-", 500);
    final HllSketch b = distinctSet("b-", 500);
    final long pure = distinct.getResult(distinct.merge(a, b)).estimate();

    // when
    final long viaMergeInto = distinct.getResult(distinct.mergeInto(a, b)).estimate();

    // then the estimates are identical (the union is deterministic)
    assertThat(viaMergeInto).isEqualTo(pure);
  }

  @Test
  void shouldMergeAWrappedDeltaLikeAHeapifiedDelta() {
    // given a delta serialized through the codec and one target
    final HllSketch target = distinctSet("a-", 500);
    final byte[] deltaBytes = codec.toBytes(distinctSet("b-", 500));

    // when the target unions the heap decode and the zero-copy merge-only view
    final long viaHeap =
        distinct.getResult(distinct.merge(target, codec.fromBytes(deltaBytes))).estimate();
    final long viaWrap =
        distinct
            .getResult(distinct.merge(target, new HllSketchValue().fromBytesForMerge(deltaBytes)))
            .estimate();

    // then both paths yield the identical estimate
    assertThat(viaWrap).isEqualTo(viaHeap);
  }

  private HllSketch distinctSet(final String prefix, final int size) {
    HllSketch acc = distinct.createAccumulator();
    for (int i = 0; i < size; i++) {
      acc = distinct.add(prefix + i, acc);
    }
    return acc;
  }
}
