/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.collections.MutableLong;
import org.junit.jupiter.api.Test;

final class SumAggregateFunctionTest {

  private final SumAggregateFunction<Long> sum = new SumAggregateFunction<>(Long::longValue);

  @Test
  void shouldFoldInPlaceWithoutReplacingTheAccumulator() {
    // given
    final MutableLong acc = sum.createAccumulator();

    // when: folding values that exceed the small-box cache (the boxed path allocated here)
    final MutableLong afterFirst = sum.add(1_000_000L, acc);
    final MutableLong afterSecond = sum.add(2_000_000L, afterFirst);

    // then: the same instance is mutated and returned — steady-state add allocates nothing
    assertThat(afterFirst).isSameAs(acc);
    assertThat(afterSecond).isSameAs(acc);
    assertThat(acc.value).isEqualTo(3_000_000L);
  }

  @Test
  void shouldMergePurelyAndMergeIntoInPlace() {
    // given two partial accumulators
    final MutableLong a = new MutableLong(5L);
    final MutableLong b = new MutableLong(7L);

    // when / then: merge is pure — neither input is mutated
    final MutableLong merged = sum.merge(a, b);
    assertThat(merged.value).isEqualTo(12L);
    assertThat(merged).isNotSameAs(a).isNotSameAs(b);
    assertThat(a.value).isEqualTo(5L);
    assertThat(b.value).isEqualTo(7L);

    // and: mergeInto folds into the target in place, leaving the delta untouched (it may be a
    // read-only view reused across calls)
    final MutableLong target = sum.mergeInto(a, b);
    assertThat(target).isSameAs(a);
    assertThat(a.value).isEqualTo(12L);
    assertThat(b.value).isEqualTo(7L);
  }

  @Test
  void shouldDeriveTheResultFromTheAccumulator() {
    // given
    final MutableLong acc = sum.add(-3L, sum.createAccumulator());

    // then
    assertThat(sum.getResult(acc)).isEqualTo(-3L);
  }
}
