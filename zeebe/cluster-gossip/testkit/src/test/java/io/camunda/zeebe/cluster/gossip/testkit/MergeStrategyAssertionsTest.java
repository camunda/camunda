/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class MergeStrategyAssertionsTest {

  @Test
  void shouldAcceptMergeThatConverges() {
    // given
    final var key = GossipKey.global("max", TestCodecs.LONG, MergeStrategy.merging(Math::max), 0L);

    // when - then
    assertThatCode(() -> MergeStrategyAssertions.assertConverges(key, List.of(1L, 5L, 3L)))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectMergeThatIsNotIdempotent() {
    // given - summing counts a duplicate delivery twice
    final var key = GossipKey.global("sum", TestCodecs.LONG, MergeStrategy.merging(Long::sum), 0L);

    // when - then
    assertThatThrownBy(() -> MergeStrategyAssertions.assertConverges(key, List.of(1L, 2L)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("idempotent");
  }

  @Test
  void shouldRejectMergeThatIsNotIdempotentEvenIfItMutatesItsOperand() {
    // given - adds into its left operand and returns it, so both sides of a check are the same
    // object
    final var codec =
        new GossipCodec<AtomicLong>() {
          @Override
          public byte[] encode(final AtomicLong value) {
            return TestCodecs.LONG.encode(value.get());
          }

          @Override
          public AtomicLong decode(final byte[] bytes) {
            return new AtomicLong(TestCodecs.LONG.decode(bytes));
          }
        };
    final var key =
        GossipKey.global(
            "mutating-sum",
            codec,
            MergeStrategy.<AtomicLong>merging(
                (left, right) -> {
                  left.addAndGet(right.get());
                  return left;
                }),
            new AtomicLong());

    // when - then
    assertThatThrownBy(
            () -> MergeStrategyAssertions.assertConverges(key, List.of(new AtomicLong(1))))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("idempotent");
  }

  @Test
  void shouldRejectMergeThatIsNotCommutative() {
    // given
    final var key =
        GossipKey.global("first", TestCodecs.LONG, MergeStrategy.merging((a, b) -> a), 0L);

    // when - then
    assertThatThrownBy(() -> MergeStrategyAssertions.assertConverges(key, List.of(1L, 2L)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("commutative");
  }

  @Test
  void shouldRejectLastWriterWinsKey() {
    // given
    final var key =
        GossipKey.global("lww", TestCodecs.LONG, MergeStrategy.<Long>lastWriterWins(), 0L);

    // when - then
    assertThatThrownBy(() -> MergeStrategyAssertions.assertConverges(key, List.of(1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
