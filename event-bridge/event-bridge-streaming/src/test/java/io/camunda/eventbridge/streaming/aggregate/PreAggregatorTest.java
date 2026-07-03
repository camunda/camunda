/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class PreAggregatorTest {

  /** A minimal mergeable aggregate: sum + count of integers. */
  private record Acc(long sum, long count) {}

  private static final AggregateFunction<Integer, Acc, Long> SUM =
      new AggregateFunction<>() {
        @Override
        public Acc createAccumulator() {
          return new Acc(0, 0);
        }

        @Override
        public Acc add(final Integer value, final Acc acc) {
          return new Acc(acc.sum() + value, acc.count() + 1);
        }

        @Override
        public Acc merge(final Acc a, final Acc b) {
          return new Acc(a.sum() + b.sum(), a.count() + b.count());
        }

        @Override
        public Long getResult(final Acc acc) {
          return acc.sum();
        }
      };

  @Test
  void shouldCombineFactsPerKey() {
    // given
    final PreAggregator<Integer, String, Acc> combiner = new PreAggregator<>(SUM);

    // when — three facts for "a", one for "b"
    combiner.add("a", 1);
    combiner.add("a", 2);
    combiner.add("a", 4);
    combiner.add("b", 10);

    // then — one partial per key, facts folded in
    final Map<String, Acc> partials = combiner.drain();
    assertThat(partials).containsOnlyKeys("a", "b");
    assertThat(partials.get("a")).isEqualTo(new Acc(7, 3));
    assertThat(partials.get("b")).isEqualTo(new Acc(10, 1));
  }

  @Test
  void shouldClearAfterDrain() {
    // given
    final PreAggregator<Integer, String, Acc> combiner = new PreAggregator<>(SUM);
    combiner.add("a", 1);

    // when
    combiner.drain();

    // then — buffer emptied; a second drain yields nothing
    assertThat(combiner.isEmpty()).isTrue();
    assertThat(combiner.size()).isZero();
    assertThat(combiner.drain()).isEmpty();
  }

  @Test
  void shouldReportSizeAsDistinctKeyCount() {
    // given
    final PreAggregator<Integer, String, Acc> combiner = new PreAggregator<>(SUM);

    // when
    combiner.add("a", 1);
    combiner.add("a", 2);
    combiner.add("b", 3);

    // then
    assertThat(combiner.size()).isEqualTo(2);
  }

  @Test
  void shouldMergePartialsCommutativelyAndAssociatively() {
    // given — three partials, as if from three flush rounds
    final Acc p1 = new Acc(7, 3);
    final Acc p2 = new Acc(10, 1);
    final Acc p3 = new Acc(5, 2);

    // then — order does not change the merged result
    assertThat(SUM.merge(p1, p2)).isEqualTo(SUM.merge(p2, p1)); // commutative
    assertThat(SUM.merge(SUM.merge(p1, p2), p3))
        .isEqualTo(SUM.merge(p1, SUM.merge(p2, p3))); // associative
    assertThat(SUM.merge(SUM.merge(p1, p2), p3)).isEqualTo(new Acc(22, 6));
  }
}
