/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.placement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class SpreadPlacementTest {

  private final SpreadPlacement placement = new SpreadPlacement();

  private static List<Integer> brokers(final int n) {
    return IntStream.range(0, n).boxed().toList();
  }

  @Test
  void shouldPlaceReplicationFactorReplicasPerPartition() {
    // when
    final var assignment = placement.assign("orders", 6, 3, brokers(5));

    // then
    assertThat(assignment).hasSize(6);
    assertThat(assignment.values()).allSatisfy(replicas -> assertThat(replicas).hasSize(3));
  }

  @Test
  void shouldNotRepeatABrokerWithinAPartition() {
    // when
    final var assignment = placement.assign("orders", 20, 4, brokers(6));

    // then — every replica of a partition is a distinct broker, and none is placed twice
    assertThat(assignment.values())
        .allSatisfy(replicas -> assertThat(new HashSet<>(replicas)).hasSameSizeAs(replicas));
  }

  @Test
  void shouldCapReplicationFactorAtBrokerCount() {
    // when — ask for more replicas than there are brokers
    final var assignment = placement.assign("orders", 3, 5, brokers(3));

    // then — capped at the broker count, still distinct
    assertThat(assignment.values())
        .allSatisfy(replicas -> assertThat(new HashSet<>(replicas)).hasSize(3));
  }

  @Test
  void shouldBeDeterministicForTheSameInputs() {
    // when
    final var first = placement.assign("orders", 8, 3, brokers(5));
    final var second = placement.assign("orders", 8, 3, brokers(5));

    // then — no randomness; the layout is reproducible (the planner re-derives it after failover)
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldRotateLeadersAcrossPartitions() {
    // when — partitions <= brokers, replication factor 1
    final var assignment = placement.assign("orders", 5, 1, brokers(5));

    // then — every partition gets a distinct leader (leadership is spread, not piled on one broker)
    final var leaders = assignment.values().stream().map(List::getFirst).distinct().toList();
    assertThat(leaders).hasSize(5);
  }

  @Test
  void shouldStartDifferentTopicsOnDifferentBrokers() {
    // given — single-partition topics; with a fixed start every topic would land on broker 0 (gap
    // 2)
    final var n = 7;
    final var leaders = new HashSet<Integer>();
    for (final var topic : List.of("orders", "payments", "shipments", "users", "events", "audit")) {
      leaders.add(placement.assign(topic, 1, 1, brokers(n)).get(1).getFirst());
    }

    // then — the single-partition topics do not all pile their leader onto the same broker
    assertThat(leaders)
        .as("single-partition topics spread their leader across brokers")
        .hasSizeGreaterThan(1);
  }

  @Test
  void shouldSpreadReplicaPairingsAcrossTheCluster() {
    // given — consecutive placement makes a ring: each broker pairs only with its two neighbours,
    // so
    // a broker failure concentrates recovery on those two (gap 1). Aggregate the co-replica set of
    // one broker across several topics.
    final var n = 6;
    final var coReplicas = new HashSet<Integer>();
    for (final var topic : List.of("orders", "payments", "shipments", "users", "events")) {
      final var assignment = placement.assign(topic, n, 3, brokers(n));
      for (final Set<Integer> replicas : assignment.values().stream().map(Set::copyOf).toList()) {
        if (replicas.contains(0)) {
          coReplicas.addAll(replicas);
        }
      }
    }
    coReplicas.remove(0);

    // then — broker 0 co-hosts with more than just two ring neighbours, so its failure load fans
    // out
    assertThat(coReplicas)
        .as("a broker's replica partners span more than the two ring neighbours")
        .hasSizeGreaterThan(2);
  }
}
