/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The system partition is a metadata-only Raft group replicated on every broker of the cluster,
 * including brokers of control zones which host no data partitions. It does not run an engine,
 * exporters or a state database; it only replicates cluster coordination records.
 *
 * <p>It lives in its own partition group, so its Raft messaging subjects ({@code
 * system-partition-0-*}) and data directory ({@code <data>/system/partitions/0}) never collide with
 * data partitions.
 *
 * <p>Its voters are derived from the static cluster configuration: every broker of every configured
 * zone (zone-aware partitioning), or node ids {@code 0..clusterSize-1} otherwise. Changing this
 * membership at runtime is out of scope for now.
 */
public final class SystemPartition {
  public static final String GROUP = "system";
  public static final PartitionId PARTITION_ID = new PartitionId(GROUP, 0);

  private final RaftPartition raftPartition;
  private final FileBasedSnapshotStore snapshotStore;
  private final MeterRegistry meterRegistry;
  private volatile SystemPartitionSnapshotter snapshotter;

  SystemPartition(
      final RaftPartition raftPartition,
      final FileBasedSnapshotStore snapshotStore,
      final MeterRegistry meterRegistry) {
    this.raftPartition = raftPartition;
    this.snapshotStore = snapshotStore;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Starts the local Raft server of the system partition. The returned future completes once the
   * server has started, not once a leader is elected.
   */
  public CompletableFuture<RaftPartition> bootstrap(
      final PartitionManagementService managementService) {
    return raftPartition.bootstrap(managementService, snapshotStore);
  }

  /**
   * Restores the state from the latest snapshot and snapshots it once the partition commits. Must
   * be called after {@link #bootstrap(PartitionManagementService)} completed.
   */
  public ActorFuture<Void> startSnapshotter(final ActorSchedulingService scheduler) {
    snapshotter = new SystemPartitionSnapshotter(raftPartition, snapshotStore);
    return scheduler.submitActor(snapshotter);
  }

  /** Returns the state restored from the latest snapshot, or empty before the snapshotter ran. */
  public Optional<SystemPartitionState> state() {
    return Optional.ofNullable(snapshotter).map(SystemPartitionSnapshotter::state);
  }

  public Optional<PersistedSnapshot> latestSnapshot() {
    return snapshotStore.getLatestSnapshot();
  }

  public RaftPartition raftPartition() {
    return raftPartition;
  }

  FileBasedSnapshotStore snapshotStore() {
    return snapshotStore;
  }

  SystemPartitionSnapshotter snapshotter() {
    return snapshotter;
  }

  MeterRegistry meterRegistry() {
    return meterRegistry;
  }

  /** Every broker of the cluster is a voter of the system partition. */
  public static Set<MemberId> members(final BrokerCfg brokerCfg) {
    final var partitioning = brokerCfg.getExperimental().getPartitioning();
    if (partitioning.getScheme() == Scheme.ZONE_AWARE) {
      return partitioning.getZoneAware().zones().stream()
          .flatMap(
              zone ->
                  IntStream.range(0, zone.numberOfBrokers())
                      .mapToObj(nodeId -> MemberId.from(zone.name(), nodeId)))
          .collect(Collectors.toUnmodifiableSet());
    }

    return IntStream.range(0, brokerCfg.getCluster().getClusterSize())
        .mapToObj(nodeId -> MemberId.from(Integer.toString(nodeId)))
        .collect(Collectors.toUnmodifiableSet());
  }

  static PartitionMetadata metadata(final BrokerCfg brokerCfg) {
    final var members = members(brokerCfg).stream().sorted().toList();
    // Priority election derives election timeouts from priorities: voters with equal priorities
    // time out together and keep splitting the vote. Distinct priorities avoid that; which voter
    // is preferred as leader does not matter for the system partition.
    final var priorities = new HashMap<MemberId, Integer>();
    for (int i = 0; i < members.size(); i++) {
      priorities.put(members.get(i), members.size() - i);
    }
    return new PartitionMetadata(
        PARTITION_ID,
        Set.copyOf(members),
        Map.copyOf(priorities),
        members.size(),
        members.getFirst());
  }
}
