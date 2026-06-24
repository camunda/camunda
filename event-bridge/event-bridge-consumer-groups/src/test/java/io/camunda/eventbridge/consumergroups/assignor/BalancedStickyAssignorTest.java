/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.assignor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.consumergroups.assignor.PartitionAssignor.PartitionAssignmentContext;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class BalancedStickyAssignorTest {

  private static final List<TopicPartition> FOUR_PARTITIONS =
      List.of(tp("t", 1), tp("t", 2), tp("t", 3), tp("t", 4));
  private final BalancedStickyAssignor assignor = new BalancedStickyAssignor();

  @Test
  void shouldAssignAllPartitionsToSingleConsumer() {
    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0"), new PartitionAssignment(Map.of()), FOUR_PARTITIONS));

    // then
    assertThat(result.forConsumer("c0"))
        .containsExactlyInAnyOrder(tp("t", 1), tp("t", 2), tp("t", 3), tp("t", 4));
  }

  @Test
  void shouldSplitEvenlyAcrossTwoConsumers() {
    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0", "c1"), new PartitionAssignment(Map.of()), FOUR_PARTITIONS));

    // then
    assertBalanced(result, List.of("c0", "c1"), FOUR_PARTITIONS);
  }

  @Test
  void shouldAssignPartitionToNewlyJoinedThirdConsumer() {
    // given — two consumers already own everything (2/2), a third joins
    final var previous =
        new PartitionAssignment(
            Map.of("c0", List.of(tp("t", 3), tp("t", 4)), "c1", List.of(tp("t", 1), tp("t", 2))));

    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(List.of("c0", "c1", "c2"), previous, FOUR_PARTITIONS));

    // then — the new consumer must not be left empty; assignment is balanced [2,1,1]
    assertThat(result.forConsumer("c2")).isNotEmpty();
    assertBalanced(result, List.of("c0", "c1", "c2"), FOUR_PARTITIONS);
  }

  @Test
  void shouldGiveEachOfFourConsumersOnePartition() {
    // given — three consumers own everything, a fourth joins
    final var previous =
        new PartitionAssignment(
            Map.of(
                "c0",
                List.of(tp("t", 3), tp("t", 4)),
                "c1",
                List.of(tp("t", 2)),
                "c2",
                List.of(tp("t", 1))));

    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0", "c1", "c2", "c3"), previous, FOUR_PARTITIONS));

    // then
    assertBalanced(result, List.of("c0", "c1", "c2", "c3"), FOUR_PARTITIONS);
    result.assignments().values().forEach(owned -> assertThat(owned).hasSize(1));
  }

  @Test
  void shouldKeepStickyPartitionsForExistingConsumers() {
    // given
    final var previous =
        new PartitionAssignment(
            Map.of("c0", List.of(tp("t", 3), tp("t", 4)), "c1", List.of(tp("t", 1), tp("t", 2))));

    // when a third joins
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(List.of("c0", "c1", "c2"), previous, FOUR_PARTITIONS));

    // then — c0 keeps both its partitions (it is already at its target load of 2)
    assertThat(result.forConsumer("c0")).containsExactlyInAnyOrder(tp("t", 3), tp("t", 4));
  }

  @Test
  void shouldBalancePartitionsAcrossMultipleTopics() {
    // given — a group subscribed to two topics (orders: 1..3, payments: 1..2), three consumers
    final var partitions =
        List.of(tp("orders", 1), tp("orders", 2), tp("orders", 3), tp("pay", 1), tp("pay", 2));

    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0", "c1", "c2"), new PartitionAssignment(Map.of()), partitions));

    // then — all five (topic, partition) pairs assigned once, loads balanced within one
    assertBalanced(result, List.of("c0", "c1", "c2"), partitions);
  }

  private static TopicPartition tp(final String topic, final int partition) {
    return new TopicPartition(topic, partition);
  }

  /** Asserts every partition is owned by exactly one consumer and loads differ by at most one. */
  private static void assertBalanced(
      final PartitionAssignment result,
      final List<String> consumers,
      final List<TopicPartition> all) {
    final var union = new TreeSet<TopicPartition>();
    int total = 0;
    int min = Integer.MAX_VALUE;
    int max = 0;
    for (final var consumer : consumers) {
      final var owned = result.forConsumer(consumer);
      union.addAll(owned);
      total += owned.size();
      min = Math.min(min, owned.size());
      max = Math.max(max, owned.size());
    }
    assertThat(union)
        .as("every partition assigned exactly once")
        .containsExactlyInAnyOrderElementsOf(all);
    assertThat(total).as("no partition assigned twice").isEqualTo(all.size());
    assertThat(max - min).as("loads balanced within one").isLessThanOrEqualTo(1);
  }
}
