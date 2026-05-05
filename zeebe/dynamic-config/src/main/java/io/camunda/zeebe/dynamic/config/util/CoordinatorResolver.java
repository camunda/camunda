/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Resolves the coordinator member from a {@link ClusterConfiguration}.
 *
 * <p>In a zone-aware cluster the coordinator is the member with nodeId 0 in the <em>primary
 * zone</em> — the zone whose members hold the highest total Raft election priorities across all
 * partitions.
 *
 * <p>In a non-zone-aware cluster (all MemberIds in bare {@code "$nodeId"} form) the coordinator is
 * simply the member with the lowest id (MemberId "0"), preserving legacy behavior.
 */
public final class CoordinatorResolver {

  private static final int COORDINATOR_NODE_ID = 0;

  private CoordinatorResolver() {}

  /**
   * Returns the coordinator {@link MemberId} derived from the given configuration. Returns {@link
   * Optional#empty()} when no coordinator can be determined (e.g. an uninitialized or empty
   * configuration).
   */
  public static Optional<MemberId> resolveCoordinator(
      final ClusterConfiguration clusterConfiguration) {
    final var members = clusterConfiguration.members();
    if (members.isEmpty()) {
      return Optional.empty();
    }

    if (isZoneAware(members.keySet())) {
      return resolveZoneAwareCoordinator(members);
    } else {
      // Legacy: coordinator is the member with the lowest id
      return members.keySet().stream().min(MemberId::compareTo);
    }
  }

  /**
   * Returns {@code true} when the given {@code localMemberId} is the coordinator according to the
   * given configuration.
   */
  public static boolean isCoordinator(
      final MemberId localMemberId, final ClusterConfiguration clusterConfiguration) {
    return resolveCoordinator(clusterConfiguration).map(localMemberId::equals).orElse(false);
  }

  /**
   * Returns the coordinator member from the given collection of members, using the same zone-aware
   * logic. This is useful for selecting the "next coordinator" from a subset of members.
   *
   * <p>When no zone information is present, falls back to the member with the lowest id.
   */
  public static Optional<MemberId> resolveCoordinatorFrom(final Collection<MemberId> members) {
    if (members.isEmpty()) {
      return Optional.empty();
    }
    if (isZoneAware(members)) {
      // Without priority info, pick nodeId 0 from the alphabetically first zone
      return members.stream()
          .filter(m -> m.nodeIdx() == COORDINATOR_NODE_ID)
          .min(MemberId::compareTo);
    }
    return members.stream().min(MemberId::compareTo);
  }

  /**
   * Determines the primary zone — the zone whose active members hold the highest total partition
   * priorities.
   */
  static Optional<String> determinePrimaryZone(final Map<MemberId, MemberState> members) {
    final var zonePriorities = new HashMap<String, Long>();

    for (final Entry<MemberId, MemberState> entry : members.entrySet()) {
      final MemberId memberId = entry.getKey();
      final MemberState memberState = entry.getValue();
      final String zone = memberId.zone();

      if (zone == null) {
        continue;
      }
      if (memberState.state() == State.LEFT || memberState.state() == State.UNINITIALIZED) {
        continue;
      }

      final long totalPriority =
          memberState.partitions().values().stream().mapToLong(p -> p.priority()).sum();
      zonePriorities.merge(zone, totalPriority, Long::sum);
    }

    if (zonePriorities.isEmpty()) {
      return Optional.empty();
    }

    // Select the zone with the highest total priority.
    // On ties, pick the alphabetically first zone for determinism.
    return zonePriorities.entrySet().stream()
        .max(
            Comparator.<Entry<String, Long>, Long>comparing(Entry::getValue)
                .thenComparing(Entry::getKey, Comparator.reverseOrder()))
        .map(Entry::getKey);
  }

  private static Optional<MemberId> resolveZoneAwareCoordinator(
      final Map<MemberId, MemberState> members) {
    final var primaryZone = determinePrimaryZone(members);
    if (primaryZone.isEmpty()) {
      // Fallback: no priority data available, pick lowest member
      return members.keySet().stream().min(MemberId::compareTo);
    }
    final var zone = primaryZone.get();
    // The coordinator is the member with nodeId 0 in the primary zone
    final var coordinatorId = MemberId.from(zone, COORDINATOR_NODE_ID);
    if (members.containsKey(coordinatorId)) {
      return Optional.of(coordinatorId);
    }
    // If nodeId 0 doesn't exist in the primary zone, fall back to the lowest member in that zone
    return members.keySet().stream().filter(m -> m.isInZone(zone)).min(MemberId::compareTo);
  }

  private static boolean isZoneAware(final Collection<MemberId> members) {
    return members.stream().anyMatch(m -> m.zone() != null);
  }
}
