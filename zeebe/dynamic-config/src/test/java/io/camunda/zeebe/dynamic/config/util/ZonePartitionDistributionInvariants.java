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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared invariant assertions used by {@link ZoneAwareAdditivePartitionReassigner}'s property-based
 * test — the zone-aware counterpart of {@link PartitionDistributionInvariants}.
 */
final class ZonePartitionDistributionInvariants {

  private ZonePartitionDistributionInvariants() {}

  /**
   * @param targetMembers every member eligible to receive a replica, used to zero-initialize leader
   *     counts so a top-priority-zone member that never became primary still counts as 0 rather
   *     than being silently excluded from the balance check
   * @param primaryBalanceTolerance the maximum allowed gap between the most- and least-primary
   *     -loaded member of the zone(s) tied for the highest configured priority. Unlike replica
   *     placement, primary selection for a given partition is restricted to whichever members were
   *     already chosen as that partition's replicas in the top zone(s), so — analogous to {@link
   *     AdditivePartitionReassigner}'s leader-count tolerance — a tight gap of 1 is not always
   *     achievable.
   */
  static void assertSatisfied(
      final Set<PartitionMetadata> result,
      final Set<MemberId> targetMembers,
      final List<PartitionId> targetPartitionIds,
      final List<ZoneSpec> zoneSpecs,
      final int replicationFactor,
      final int primaryBalanceTolerance) {
    // 1. every target partition id is present in the result
    assertThat(result.stream().map(PartitionMetadata::id).collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(targetPartitionIds);

    // 2. every partition has exactly replicationFactor distinct replicas
    result.forEach(
        metadata ->
            assertThat(Set.copyOf(metadata.members()))
                .as(
                    "partition %s must have %s distinct replicas, but found members %s",
                    metadata.id(), replicationFactor, metadata.members())
                .hasSize(replicationFactor));

    // 3. every partition places exactly ZoneSpec#numberOfReplicas() replicas in each zone
    result.forEach(
        metadata ->
            zoneSpecs.forEach(
                spec -> {
                  final long countInZone =
                      metadata.members().stream().filter(m -> m.isInZone(spec.name())).count();
                  assertThat(countInZone)
                      .as(
                          "partition %s must have %s replicas in zone '%s', but found %s",
                          metadata.id(), spec.numberOfReplicas(), spec.name(), countInZone)
                      .isEqualTo(spec.numberOfReplicas());
                }));

    // 4. the primary is always a member of a zone tied for the highest configured priority
    final int topPriority = zoneSpecs.stream().mapToInt(ZoneSpec::priority).max().orElseThrow();
    final Set<String> topZoneNames =
        zoneSpecs.stream()
            .filter(spec -> spec.priority() == topPriority)
            .map(ZoneSpec::name)
            .collect(Collectors.toSet());
    result.forEach(
        metadata -> {
          final var primary = metadata.getPrimary().orElseThrow();
          assertThat(topZoneNames)
              .as(
                  "partition %s's primary %s must belong to one of the top-priority zones %s",
                  metadata.id(), primary, topZoneNames)
              .contains(primary.zone());
        });

    // 5. primary/leader count is balanced across the top-priority zone(s)' members
    final Map<MemberId, Integer> leaderCountByMember = new HashMap<>();
    targetMembers.stream()
        .filter(member -> topZoneNames.contains(member.zone()))
        .forEach(member -> leaderCountByMember.put(member, 0));
    result.forEach(
        metadata ->
            metadata
                .getPrimary()
                .ifPresent(primary -> leaderCountByMember.merge(primary, 1, Integer::sum)));
    assertBalanced(
        leaderCountByMember, "primary count per top-priority-zone", primaryBalanceTolerance);

    // 6. replica count is balanced across every zone's own available brokers — catches a
    // reassigner that keeps piling replicas onto the same subset of a zone's brokers instead of
    // spreading them across all brokers the zone actually has available
    zoneSpecs.forEach(
        spec -> {
          final Map<MemberId, Integer> replicaCountByZoneMember = new HashMap<>();
          targetMembers.stream()
              .filter(member -> member.isInZone(spec.name()))
              .forEach(member -> replicaCountByZoneMember.put(member, 0));
          result.forEach(
              metadata ->
                  metadata.members().stream()
                      .filter(member -> member.isInZone(spec.name()))
                      .forEach(member -> replicaCountByZoneMember.merge(member, 1, Integer::sum)));
          assertBalanced(
              replicaCountByZoneMember, "replica count in zone '" + spec.name() + "'", 1);
        });
  }

  private static void assertBalanced(
      final Map<MemberId, Integer> countByMember, final String kind, final int tolerance) {
    final int max = countByMember.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    final int min = countByMember.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    assertThat(max - min)
        .as("%s must be balanced, but was %s", kind, countByMember)
        .isLessThanOrEqualTo(tolerance);
  }
}
