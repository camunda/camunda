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
import java.util.stream.Collectors;
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
 * {@link MemberId#nodeIdx()} ascending. All members in {@code clusterMembers} must have a zone set.
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
 * <p>Zones with equal priority values are allowed; the ordering between them falls back to {@link
 * RoundRobinPartitionDistributor}, with brokers ordered by (nodeId, zone).
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

  /** Regions sorted by {@link ZoneSpec#priority()} descending (highest priority first). */
  private final List<ZoneSpec> zoneSpecs;

  /**
   * @param zoneSpecs the zone specifications. May be in any order; the constructor sorts them by
   *     {@link ZoneSpec#priority()} descending so that the highest-priority zone's brokers always
   *     receive the highest Raft priorities.
   */
  public ZoneAwarePartitionDistributor(final List<ZoneSpec> zoneSpecs) {
    this.zoneSpecs =
        zoneSpecs.stream().sorted(Comparator.comparingInt(ZoneSpec::priority).reversed()).toList();
    if (this.zoneSpecs.size() == 1) {
      LOG.warn(
          "ZoneAwarePartitionDistributor is configured with only one zone ('{}'). "
              + "Zone-aware distribution requires at least two zones to provide fault isolation "
              + "across availability zones. This is likely a misconfiguration.",
          this.zoneSpecs.getFirst().name());
    }

    validateZoneSpecs(zoneSpecs);
  }

  @Override
  public Set<PartitionMetadata> distributePartitions(
      final Set<MemberId> clusterMembers,
      final List<PartitionId> sortedPartitionIds,
      final int replicationFactor) {

    validateMemberZones(clusterMembers);
    validateReplicaSum(replicationFactor);
    validateZoneHasSufficientBrokers(clusterMembers);

    // all zones have the same priority
    if (zoneSpecs.stream().map(ZoneSpec::priority).distinct().count() == 1) {
      // use RoundRobinDistributor instead
      return new RoundRobinPartitionDistributor()
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

      for (final var spec : zoneSpecs) {
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
    return zoneSpecs;
  }

  private void validateMemberZones(final Set<MemberId> clusterMembers) {
    for (final var member : clusterMembers) {
      if (member.zone() == null) {
        throw new IllegalStateException(
            "ZoneAwarePartitionDistributor: member '%s' has no zone configured; all cluster members must have a zone"
                .formatted(member));
      }
    }
  }

  private void validateReplicaSum(final int replicationFactor) {

    final var totalReplicas = zoneSpecs.stream().mapToInt(ZoneSpec::numberOfReplicas).sum();
    if (totalReplicas != replicationFactor) {
      throw new IllegalStateException(
          "ZoneAwarePartitionDistributor: sum of numberOfReplicas across all zones (%d) does not match replicationFactor (%d)"
              .formatted(totalReplicas, replicationFactor));
    }
  }

  private void validateZoneHasSufficientBrokers(final Set<MemberId> clusterMembers) {
    for (final var spec : zoneSpecs) {
      final long zoneCount = clusterMembers.stream().filter(m -> m.isInZone(spec.name())).count();
      if (zoneCount < spec.numberOfReplicas()) {
        throw new IllegalStateException(
            "ZoneAwarePartitionDistributor: zone '%s' needs %d replicas but only has %d broker(s) in clusterMembers"
                .formatted(spec.name(), spec.numberOfReplicas(), zoneCount));
      }
    }
  }

  private void validateZoneSpecs(final List<ZoneSpec> zoneSpecs) {
    zoneSpecs.stream()
        .collect(Collectors.groupingBy(ZoneSpec::name))
        .forEach(
            (name, zones) -> {
              if (zones.size() > 1) {
                throw new IllegalArgumentException(
                    "Expected zone names to be unique, but got " + zones);
              }
            });
  }

  /**
   * Describes a single zone's participation in the cluster.
   *
   * @param name the zone name (e.g. {@code "us-east1"})
   * @param numberOfReplicas how many replicas of each partition are placed in this zone
   * @param priority the zone's preferred-leader ranking; higher values are preferred.
   */
  public record ZoneSpec(String name, int numberOfReplicas, int priority) {
    public ZoneSpec {
      if (name.isEmpty()) {
        throw new IllegalArgumentException(
            "ZoneAwarePartitionDistributor: expected non-empty name, but got empty string");
      }
      if (numberOfReplicas <= 0) {
        throw new IllegalArgumentException(
            "ZoneAwarePartitionDistributor: expected numberOfReplicas >= 1, but got "
                + numberOfReplicas);
      }
      if (priority <= 0) {
        throw new IllegalArgumentException(
            "ZoneAwarePartitionDistributor: expected priority > 0, but got " + priority);
      }
    }

    public ZoneSpec withPriority(final int priority) {
      return new ZoneSpec(name, numberOfReplicas, priority);
    }
  }
}
