/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link PartitionDistributor} that distributes partitions across brokers in a region-aware
 * manner.
 *
 * <p>Regions are ranked by their configured {@link RegionSpec#priority()} in descending order
 * (higher priority = preferred leader location). For each partition, replicas are assigned
 * region-by-region in priority order, using a round-robin offset within each region so that
 * different partitions are spread evenly across the brokers in that region.
 *
 * <p>Raft election priorities are assigned sequentially from {@code replicationFactor} down to
 * {@code 1}, iterating regions from highest to lowest priority and brokers within each region in
 * round-robin order. This means:
 *
 * <ul>
 *   <li>The broker selected first from the highest-priority region receives Raft priority {@code
 *       replicationFactor} and becomes the preferred leader.
 *   <li>If all brokers in the primary region become unavailable, Raft's priority-decrement
 *       mechanism naturally falls over to the next region without any additional code.
 * </ul>
 *
 * <p>Regions must have distinct priority values; equal priorities produce undefined failover
 * ordering.
 *
 * <p>Example distribution with 3 regions (us-east1 prio=1000 × 2 replicas/2 brokers,
 * us-west1 prio=500 × 2 replicas/2 brokers, euro-east1 prio=10 × 1 replica/1 broker),
 * 5 partitions, RF=5:
 *
 * <pre>
 * +------------------+-------------+-------------+-------------+-------------+---------------+
 * | Partition \ Node | us-east1-0  | us-east1-1  | us-west1-0  | us-west1-1  | euro-east1-0  |
 * +------------------+-------------+-------------+-------------+-------------+---------------+
 * |                1 |      5      |      4      |      3      |      2      |       1       |
 * |                2 |      4      |      5      |      2      |      3      |       1       |
 * |                3 |      5      |      4      |      3      |      2      |       1       |
 * |                4 |      4      |      5      |      2      |      3      |       1       |
 * |                5 |      5      |      4      |      3      |      2      |       1       |
 * +------------------+-------------+-------------+-------------+-------------+---------------+
 * </pre>
 *
 * (Numbers are Raft priorities; the member with priority == RF is the preferred leader.)
 */
public final class RegionAwarePartitionDistributor implements PartitionDistributor {

  /** Regions sorted by {@link RegionSpec#priority()} descending (highest priority first). */
  private final List<RegionSpec> regionSpecs;

  /**
   * @param regionSpecs the region specifications. May be in any order; the constructor sorts them
   *     by {@link RegionSpec#priority()} descending so that the highest-priority region's brokers
   *     always receive the highest Raft priorities.
   */
  public RegionAwarePartitionDistributor(final List<RegionSpec> regionSpecs) {
    this.regionSpecs =
        regionSpecs.stream()
            .sorted(Comparator.comparingInt(RegionSpec::priority).reversed())
            .toList();
  }

  @Override
  public Set<PartitionMetadata> distributePartitions(
      final Set<MemberId> clusterMembers,
      final List<PartitionId> sortedPartitionIds,
      final int replicationFactor) {

    validateReplicaSum(replicationFactor);
    validateBrokerIds(clusterMembers);

    final Set<PartitionMetadata> result = new HashSet<>();

    for (int i = 0; i < sortedPartitionIds.size(); i++) {
      final PartitionId partitionId = sortedPartitionIds.get(i);

      // priorityCounter starts at RF (highest Raft priority) and counts down to 1.
      // The first replica assigned — always from the highest-priority region — gets RF,
      // ensuring it wins Raft elections and becomes the partition leader.
      int priorityCounter = replicationFactor;
      final List<MemberId> orderedMembers = new ArrayList<>(replicationFactor);
      final Map<MemberId, Integer> priorityMap = new HashMap<>(replicationFactor);

      for (final RegionSpec spec : regionSpecs) {
        final int regionBrokerCount = spec.brokers().size();
        for (int r = 0; r < spec.numberOfReplicas(); r++) {
          final int brokerIndex = (i + r) % regionBrokerCount;
          final MemberId broker = spec.brokers().get(brokerIndex);
          orderedMembers.add(broker);
          priorityMap.put(broker, priorityCounter--);
        }
      }

      // The first member always belongs to the highest-priority region and holds Raft
      // priority == replicationFactor, making it the preferred partition leader.
      final MemberId primary = orderedMembers.getFirst();

      result.add(
          new PartitionMetadata(
              partitionId,
              Set.copyOf(orderedMembers),
              Map.copyOf(priorityMap),
              replicationFactor,
              primary));
    }

    return result;
  }

  private void validateReplicaSum(final int replicationFactor) {
    final int totalReplicas = regionSpecs.stream().mapToInt(RegionSpec::numberOfReplicas).sum();
    if (totalReplicas != replicationFactor) {
      throw new IllegalStateException(
          "RegionAwarePartitionDistributor: sum of numberOfReplicas across all regions (%d) does not match replicationFactor (%d)"
              .formatted(totalReplicas, replicationFactor));
    }
  }

  private void validateBrokerIds(final Set<MemberId> clusterMembers) {
    for (final RegionSpec spec : regionSpecs) {
      for (final MemberId broker : spec.brokers()) {
        if (!clusterMembers.contains(broker)) {
          throw new IllegalStateException(
              ("RegionAwarePartitionDistributor: broker '%s' (region '%s') is not present in "
                      + "clusterMembers — the region config may be out of sync with the actual cluster")
                  .formatted(broker, spec.name()));
        }
      }
    }
  }

  /**
   * Describes a single region's participation in the cluster.
   *
   * @param name the region name (e.g. {@code "us-east1"})
   * @param numberOfReplicas how many replicas of each partition are placed in this region; must be
   *     {@code <= brokers.size()}
   * @param priority the region's preferred-leader ranking; higher values are preferred. Must be
   *     unique across all regions.
   * @param brokers the ordered list of {@link MemberId}s belonging to this region, in the same
   *     insertion order as the configuration (i.e. local node IDs 0, 1, … within the region)
   */
  public record RegionSpec(
      String name, int numberOfReplicas, int priority, List<MemberId> brokers) {}
}
