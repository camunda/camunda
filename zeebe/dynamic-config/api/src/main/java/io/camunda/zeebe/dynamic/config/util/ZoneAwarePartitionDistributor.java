/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PartitionDistributor} that distributes partitions across brokers in a zone-aware manner.
 *
 * <p>Regions are ranked by their configured {@link ZoneSpec#priority()} in descending order (higher
 * priority = preferred leader location). For each partition, replicas are assigned zone-by-zone in
 * priority order, using a round-robin offset within each zone so that different partitions are
 * spread evenly across the brokers in that zone.
 *
 * <p>The broker list for each zone is derived at distribution time from the {@code clusterMembers}
 * set by filtering members whose {@link MemberId#zone()} matches the zone name and sorting them by
 * {@link MemberId#nodeIdx()} ascending.
 *
 * <p>Whenever any bare (not-yet-migrated) member is present — a fully bare cluster whose zone-aware
 * config has been persisted but whose migration has not started, or a cluster mid-migration with a
 * mix of bare and zoned members — the distributor falls back to a zone-order-steered {@link
 * RoundRobinPartitionDistributor} so the original slot layout is preserved until the migration is
 * complete. On a fully bare cluster this reproduces plain round-robin, so recomputing the
 * distribution before migration is a no-op.
 *
 * <p>Raft election priorities are assigned sequentially from {@code replicationFactor} down to
 * {@code 1}, iterating zones from highest to lowest priority and brokers within each zone in
 * round-robin order. This means:
 *
 * <ul>
 *   <li>The broker selected first from the highest-priority zone receives Raft priority {@code
 *       replicationFactor} and becomes the preferred leader.
 *   <li>If all brokers in the primary zone become unavailable, Raft's priority-decrement mechanism
 *       naturally falls over to the next zone without any additional code.
 * </ul>
 *
 * <p>Zones with equal priority values are allowed; the distribution then also falls back to {@link
 * RoundRobinPartitionDistributor}, with brokers ordered by the configured zone order rather than by
 * zone name, so an equal-priority zone-aware config reproduces the exact placement a
 * zone-order-steered migration materialised.
 *
 * <p>Example distribution with 3 zones (us-east1 prio=1000 × 2 replicas/2 brokers, us-west1
 * prio=500 × 2 replicas/2 brokers, euro-east1 prio=10 × 1 replica/1 broker), 5 partitions, RF=5:
 *
 * <pre>
 * +------------------+-------------+-------------+-------------+-------------+---------------+
 * | Partition \ Node | us-east1_0  | us-east1_1  | us-west1_0  | us-west1_1  | euro-east1_0  |
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
@NullMarked
public final class ZoneAwarePartitionDistributor implements PartitionDistributor {

  private static final Logger LOG = LoggerFactory.getLogger(ZoneAwarePartitionDistributor.class);

  /** Zone specs in the configured request order, used for slot-preserving round-robin fallback. */
  private final List<ZoneSpec> zoneSpecs;

  /** Regions sorted by {@link ZoneSpec#priority()} descending (highest priority first). */
  private final List<ZoneSpec> zoneSpecsByPriority;

  /**
   * @param zoneSpecs the zone specifications. May be in any order; the constructor sorts them by
   *     {@link ZoneSpec#priority()} descending so that the highest-priority zone's brokers always
   *     receive the highest Raft priorities.
   */
  public ZoneAwarePartitionDistributor(final List<ZoneSpec> zoneSpecs) {
    this.zoneSpecs = List.copyOf(zoneSpecs);
    zoneSpecsByPriority =
        zoneSpecs.stream().sorted(Comparator.comparingInt(ZoneSpec::priority).reversed()).toList();
    if (this.zoneSpecs.size() == 1) {
      LOG.warn(
          "ZoneAwarePartitionDistributor is configured with only one zone ('{}'). "
              + "Zone-aware distribution requires at least two zones to provide fault isolation "
              + "across availability zones. This is likely a misconfiguration.",
          this.zoneSpecs.getFirst().name());
    }

    ZoneSpecValidation.validateZoneSpecs(zoneSpecs);
  }

  @Override
  public Set<PartitionMetadata> distributePartitions(
      final Set<MemberId> clusterMembers,
      final List<PartitionId> sortedPartitionIds,
      final int replicationFactor) {

    ZoneSpecValidation.validateReplicaSum(zoneSpecs, replicationFactor);

    // As long as any bare (not-yet-migrated) member is present the cluster is either fully bare
    // (config persisted but migration not started) or mid-migration. In both cases the zone-aware
    // placement cannot be materialised yet, so fall back to a zone-order-steered round-robin that
    // preserves the original slot layout. On a fully bare cluster this is identical to plain
    // round-robin, so recomputing the distribution before migration is a no-op.
    if (hasBareMembers(clusterMembers)) {
      ZoneSpecValidation.validateKnownZonedMembers(zoneSpecs, clusterMembers);
      return new RoundRobinPartitionDistributor(zoneSpecs.stream().map(ZoneSpec::name).toList())
          .distributePartitions(clusterMembers, sortedPartitionIds, replicationFactor);
    }

    ZoneSpecValidation.validateZoneHasSufficientBrokers(zoneSpecs, clusterMembers);

    // all zones have the same priority
    if (zoneSpecsByPriority.stream().map(ZoneSpec::priority).distinct().count() == 1) {
      return new RoundRobinPartitionDistributor(zoneSpecs.stream().map(ZoneSpec::name).toList())
          .distributePartitions(clusterMembers, sortedPartitionIds, replicationFactor);
    }

    final var result = new HashSet<PartitionMetadata>();

    for (int i = 0; i < sortedPartitionIds.size(); i++) {
      final var partitionId = sortedPartitionIds.get(i);

      // priorityCounter starts at RF (highest Raft priority) and counts down to 1.
      // The first replica assigned — always from the highest-priority zone — gets RF,
      // ensuring it wins Raft elections and becomes the partition leader.
      int priorityCounter = replicationFactor;
      final List<MemberId> orderedMembers = new ArrayList<>(replicationFactor);
      final Map<MemberId, Integer> priorityMap = new HashMap<>(replicationFactor);

      for (final var spec : zoneSpecsByPriority) {
        final var zoneBrokers =
            clusterMembers.stream()
                .filter(m -> m.isInZone(spec.name()))
                .sorted(Comparator.comparingInt(MemberId::nodeIdx))
                .toList();
        final int zoneBrokerCount = zoneBrokers.size();
        for (int r = 0; r < spec.numberOfReplicas(); r++) {
          final int brokerIndex = (i + r) % zoneBrokerCount;
          final var broker = zoneBrokers.get(brokerIndex);
          orderedMembers.add(broker);
          priorityMap.put(broker, priorityCounter--);
        }
      }

      // The first member always belongs to the highest-priority zone and holds Raft
      // priority == replicationFactor, making it the preferred partition leader.
      final var primary = orderedMembers.getFirst();

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

  public List<ZoneSpec> zoneSpecs() {
    return zoneSpecsByPriority;
  }

  private boolean hasBareMembers(final Set<MemberId> clusterMembers) {
    return clusterMembers.stream().anyMatch(member -> member.zone() == null);
  }
}
