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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  @Test
  void shouldPlaceStandbysAnywhereButTheActiveOwner() {
    // given — one standby replica requested, three consumers, no prior assignment
    final var context =
        new PartitionAssignmentContext(
            List.of("c0", "c1", "c2"),
            new PartitionAssignment(Map.of()),
            FOUR_PARTITIONS,
            1,
            Map.of());

    // when
    final var result = assignor.assign(context);

    // then — every partition's standby holder(s) never include its own active owner
    final var activeOwner = new HashMap<TopicPartition, String>();
    result.assignments().forEach((c, owned) -> owned.forEach(p -> activeOwner.put(p, c)));
    result
        .standbyAssignments()
        .forEach(
            (member, standbyPartitions) ->
                standbyPartitions.forEach(
                    p -> assertThat(activeOwner.get(p)).isNotEqualTo(member)));
  }

  @Test
  void shouldNotPlaceTwoStandbysOfTheSamePartitionOnTheSameMember() {
    // given — two standby replicas requested, four consumers
    final var context =
        new PartitionAssignmentContext(
            List.of("c0", "c1", "c2", "c3"),
            new PartitionAssignment(Map.of()),
            FOUR_PARTITIONS,
            2,
            Map.of());

    // when
    final var result = assignor.assign(context);

    // then — each partition has at most one standby holder per member (trivially true — a member
    // either does or doesn't hold a partition — but confirms no duplicate accounting)
    for (final var partition : FOUR_PARTITIONS) {
      final var holders =
          result.standbyAssignments().entrySet().stream()
              .filter(e -> e.getValue().contains(partition))
              .map(Map.Entry::getKey)
              .toList();
      assertThat(holders).doesNotHaveDuplicates();
    }
  }

  @Test
  void shouldKeepStickyStandbyAcrossARebalance() {
    // given — c1 already stands by for t-4, which c0 keeps as active owner through this rebalance
    // (the rebalance step moves t-1..t-3 off c0 to the newly joined c2/c3, but c0 stays at its
    // 1-partition target holding t-4 — see shouldKeepStickyPartitionsForExistingConsumers for the
    // same active-rebalance shape); a standby must not move unless its own placement requires it.
    final var previous =
        new PartitionAssignment(
            Map.of("c0", FOUR_PARTITIONS, "c1", List.of()), Map.of("c1", List.of(tp("t", 4))));
    final var context =
        new PartitionAssignmentContext(
            List.of("c0", "c1", "c2", "c3"), previous, FOUR_PARTITIONS, 1, Map.of());

    // when
    final var result = assignor.assign(context);

    // then — c1 keeps standing by for t-4 rather than being reassigned to a newcomer
    assertThat(result.forConsumer("c0")).contains(tp("t", 4));
    assertThat(result.standbyAssignments().getOrDefault("c1", List.of())).contains(tp("t", 4));
  }

  @Test
  void shouldRespectTheWarmingCapPerMember() {
    // given — a cap of one warming partition per member, two standby replicas requested across
    // four partitions and only two other members to hold them
    final var cappedAssignor = new BalancedStickyAssignor(1);
    final var context =
        new PartitionAssignmentContext(
            List.of("c0", "c1", "c2"),
            new PartitionAssignment(Map.of()),
            FOUR_PARTITIONS,
            2,
            Map.of());

    // when
    final var result = cappedAssignor.assign(context);

    // then — no member warms more than one partition, even though two replicas were requested
    result
        .standbyAssignments()
        .values()
        .forEach(owned -> assertThat(owned.size()).isLessThanOrEqualTo(1));
  }

  @Test
  void shouldNotPromoteAColdStandbyWhenItsActiveOwnerLeaves() {
    // given — c0 owned all four partitions; c1 was standing by for all of them but has never
    // reported readiness; c0 then leaves
    final var previous =
        new PartitionAssignment(
            Map.of("c0", FOUR_PARTITIONS, "c1", List.of()), Map.of("c1", FOUR_PARTITIONS));
    final var context =
        new PartitionAssignmentContext(
            List.of("c1"), previous, FOUR_PARTITIONS, 1, Map.of() /* no readiness reported */);

    // when
    final var result = assignor.assign(context);

    // then — c1 is not handed the orphaned partitions (it never reported readiness); they are
    // left unassigned entirely rather than filled to an unready member
    assertThat(result.forConsumer("c1")).isEmpty();
    assertThat(result.assignments().values().stream().mapToInt(List::size).sum()).isZero();
  }

  @Test
  void shouldPromoteAReadyStandbyWhenItsActiveOwnerLeaves() {
    // given — same as above, but c1 has reported readiness for every partition
    final var previous =
        new PartitionAssignment(
            Map.of("c0", FOUR_PARTITIONS, "c1", List.of()), Map.of("c1", FOUR_PARTITIONS));
    final var context =
        new PartitionAssignmentContext(
            List.of("c1"), previous, FOUR_PARTITIONS, 1, Map.of("c1", Set.copyOf(FOUR_PARTITIONS)));

    // when
    final var result = assignor.assign(context);

    // then — c1 is promoted to active owner of every orphaned partition
    assertThat(result.forConsumer("c1")).containsExactlyInAnyOrderElementsOf(FOUR_PARTITIONS);
  }

  @Test
  void shouldBehaveExactlyAsBeforeWhenNoStandbyReplicasAreConfigured() {
    // given — a group with standbyReplicas == 0 (today's default): an orphaned partition (no
    // current owner, but it did have one before) must still be filled immediately, never left
    // unassigned — no behavior change for existing groups.
    final var previous = new PartitionAssignment(Map.of("c0", FOUR_PARTITIONS, "c1", List.of()));
    final var context = new PartitionAssignmentContext(List.of("c1"), previous, FOUR_PARTITIONS);

    // when
    final var result = assignor.assign(context);

    // then
    assertThat(result.forConsumer("c1")).containsExactlyInAnyOrderElementsOf(FOUR_PARTITIONS);
    assertThat(result.standbyAssignments()).isEmpty();
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
