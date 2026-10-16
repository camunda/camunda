/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared invariant assertions used by {@link AdditivePartitionReassigner}'s property-based test:
 * given a resulting distribution, verifies it actually satisfies the four properties every valid
 * reassignment must have, regardless of the specific input sizes exercised by a given property run.
 */
final class PartitionDistributionInvariants {

  private PartitionDistributionInvariants() {}

  /**
   * @param leaderBalanceTolerance the maximum allowed gap between the most- and least-leader-loaded
   *     target member. {@link AdditivePartitionReassigner} cannot always guarantee a tight gap of 1
   *     here: it never touches an existing partition, so when few new partitions are added relative
   *     to a pre-existing skew, there can be no valid output that both keeps replica count balanced
   *     (gap 1) and fully corrects leader count — see {@link AdditivePartitionReassigner}'s class
   *     javadoc. The worst case is bounded by the replication factor: a single new partition can
   *     shift at most one member's leader count by one relative to the others already at the
   *     replication-factor-driven ceiling.
   */
  static void assertSatisfied(
      final Set<PartitionMetadata> result,
      final Set<MemberId> targetMembers,
      final List<PartitionId> targetPartitionIds,
      final int replicationFactor,
      final int leaderBalanceTolerance) {
    // 1. every target partition id is present in the result
    assertThat(result.stream().map(PartitionMetadata::id).collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(targetPartitionIds);

    // 4. no partition has more than one replica on the same broker
    result.forEach(
        metadata ->
            assertThat(Set.copyOf(metadata.members()))
                .as(
                    "partition %s must have %s distinct replicas, but found members %s",
                    metadata.id(), replicationFactor, metadata.members())
                .hasSize(replicationFactor));

    final Map<MemberId, Integer> replicaCountByMember = new HashMap<>();
    final Map<MemberId, Integer> leaderCountByMember = new HashMap<>();
    targetMembers.forEach(
        member -> {
          replicaCountByMember.put(member, 0);
          leaderCountByMember.put(member, 0);
        });
    result.forEach(
        metadata -> {
          metadata.members().forEach(member -> replicaCountByMember.merge(member, 1, Integer::sum));
          metadata
              .getPrimary()
              .ifPresent(primary -> leaderCountByMember.merge(primary, 1, Integer::sum));
        });

    // 2. replica count per broker is balanced
    assertBalanced(replicaCountByMember, "replica", 1);
    // 3. leader count per broker is balanced
    assertBalanced(leaderCountByMember, "leader", leaderBalanceTolerance);
  }

  private static void assertBalanced(
      final Map<MemberId, Integer> countByMember, final String kind, final int tolerance) {
    final int max = countByMember.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    final int min = countByMember.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    assertThat(max - min)
        .as("%s count per broker must be balanced, but was %s", kind, countByMember)
        .isLessThanOrEqualTo(tolerance);
  }
}
