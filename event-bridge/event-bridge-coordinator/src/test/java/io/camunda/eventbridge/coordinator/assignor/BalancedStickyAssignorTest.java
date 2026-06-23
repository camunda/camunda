/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.assignor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.coordinator.assignor.PartitionAssignor.PartitionAssignmentContext;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class BalancedStickyAssignorTest {

  private static final List<Integer> FOUR_PARTITIONS = List.of(1, 2, 3, 4);
  private final BalancedStickyAssignor assignor = new BalancedStickyAssignor();

  @Test
  void shouldAssignAllPartitionsToSingleConsumer() {
    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0"), new PartitionAssignment(Map.of()), FOUR_PARTITIONS));

    // then
    assertThat(result.forConsumer("c0")).containsExactlyInAnyOrder(1, 2, 3, 4);
  }

  @Test
  void shouldSplitEvenlyAcrossTwoConsumers() {
    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0", "c1"), new PartitionAssignment(Map.of()), FOUR_PARTITIONS));

    // then
    assertBalanced(result, List.of("c0", "c1"));
  }

  @Test
  void shouldAssignPartitionToNewlyJoinedThirdConsumer() {
    // given — two consumers already own everything (2/2), a third joins
    final var previous = new PartitionAssignment(Map.of("c0", List.of(3, 4), "c1", List.of(1, 2)));

    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(List.of("c0", "c1", "c2"), previous, FOUR_PARTITIONS));

    // then — the new consumer must not be left empty; assignment is balanced [2,1,1]
    assertThat(result.forConsumer("c2")).isNotEmpty();
    assertBalanced(result, List.of("c0", "c1", "c2"));
  }

  @Test
  void shouldGiveEachOfFourConsumersOnePartition() {
    // given — three consumers own everything, a fourth joins
    final var previous =
        new PartitionAssignment(Map.of("c0", List.of(3, 4), "c1", List.of(2), "c2", List.of(1)));

    // when
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(
                List.of("c0", "c1", "c2", "c3"), previous, FOUR_PARTITIONS));

    // then
    assertBalanced(result, List.of("c0", "c1", "c2", "c3"));
    result.assignments().values().forEach(owned -> assertThat(owned).hasSize(1));
  }

  @Test
  void shouldKeepStickyPartitionsForExistingConsumers() {
    // given
    final var previous = new PartitionAssignment(Map.of("c0", List.of(3, 4), "c1", List.of(1, 2)));

    // when a third joins
    final var result =
        assignor.assign(
            new PartitionAssignmentContext(List.of("c0", "c1", "c2"), previous, FOUR_PARTITIONS));

    // then — c0 keeps both its partitions (it is already at its target load of 2)
    assertThat(result.forConsumer("c0")).containsExactlyInAnyOrder(3, 4);
  }

  /** Asserts every partition is owned by exactly one consumer and loads differ by at most one. */
  private static void assertBalanced(
      final PartitionAssignment result, final List<String> consumers) {
    final var union = new TreeSet<Integer>();
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
    assertThat(union).as("every partition assigned exactly once").containsExactly(1, 2, 3, 4);
    assertThat(total).as("no partition assigned twice").isEqualTo(4);
    assertThat(max - min).as("loads balanced within one").isLessThanOrEqualTo(1);
  }
}
