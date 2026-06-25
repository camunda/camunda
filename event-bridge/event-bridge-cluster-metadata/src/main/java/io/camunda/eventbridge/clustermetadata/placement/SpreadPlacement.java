/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic placement that spreads both leadership and replica pairings across the broker set.
 *
 * <p>Two properties, both seeded by the topic name so the layout is reproducible and re-derivable
 * after a leader failover (no randomness):
 *
 * <ul>
 *   <li><b>Rotated start.</b> A topic's partition 1 does not always begin on the first broker; the
 *       starting broker is offset by a per-topic seed. Without this, every small topic (fewer
 *       partitions than brokers) piles its leaders and replicas onto the front of the broker list,
 *       making the first brokers hot.
 *   <li><b>Shifting follower stride.</b> A partition's followers do not sit on the brokers
 *       immediately after the leader; they step away by a stride that varies per topic and as the
 *       partitions wrap the broker list. Placing followers consecutively makes the "who replicates
 *       with whom" graph a ring — each broker pairs only with its two neighbours — so when a broker
 *       fails, all of its leadership takeover and re-replication load lands on those two
 *       neighbours. Varying the stride turns the ring into a mesh, so a failed broker's recovery
 *       load fans out across all survivors instead of concentrating on a fixed pair.
 * </ul>
 *
 * <p>Operates only on the broker node ids it is given, so it behaves the same for a fixed or a
 * dynamic broker set. The first replica of each partition is its preferred leader.
 */
public final class SpreadPlacement implements PlacementStrategy {

  @Override
  public Map<Integer, List<Integer>> assign(
      final String topic,
      final int partitionCount,
      final int replicationFactor,
      final List<Integer> brokers) {
    final var n = brokers.size();
    if (n == 0) {
      return Map.of();
    }
    final var rf = Math.max(1, Math.min(replicationFactor, n));
    // Per-topic seed: spreads the starting broker (across topics) and the follower stride, so the
    // layout differs from topic to topic. Deterministic — String.hashCode is stable — so the same
    // topic always yields the same layout, which the reconfiguration planner relies on.
    final var seed = Math.floorMod(topic.hashCode(), n);

    final Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
    for (var partition = 0; partition < partitionCount; partition++) {
      final var leader = Math.floorMod(seed + partition, n);
      final List<Integer> replicaSet = new ArrayList<>(rf);
      replicaSet.add(brokers.get(leader));

      if (rf > 1) {
        // n >= 2 here (rf is capped at n), so n - 1 >= 1. The stride bumps each time the partitions
        // wrap the broker list, so successive waves of partitions don't reuse the same pairings.
        final var stride = 1 + Math.floorMod(seed + partition / n, n - 1);
        for (var follower = 0; follower < rf - 1; follower++) {
          // Offset in [1, n-1] (never 0, so never the leader); distinct for distinct followers
          // because rf - 1 <= n - 1, so the rf replicas of a partition are all different brokers.
          final var offset = 1 + Math.floorMod(stride + follower, n - 1);
          replicaSet.add(brokers.get(Math.floorMod(leader + offset, n)));
        }
      }
      assignment.put(partition + 1, replicaSet);
    }
    return assignment;
  }
}
