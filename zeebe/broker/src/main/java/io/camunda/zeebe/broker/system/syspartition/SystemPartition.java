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
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The system partition is a metadata-only Raft group replicated on every broker of the cluster,
 * including brokers of control zones which host no data partitions. It does not run an engine,
 * exporters or a state database; it only replicates cluster coordination data.
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
  private final Duration snapshotPeriod;
  private final SystemKeyValueStore keyValueStore = new SystemKeyValueStore();
  // listeners added before start, handed to the role controller when it is created
  private final List<SystemPartitionListener> pendingListeners = new ArrayList<>();
  // actors built on top of the partition, started after its own
  private final List<Actor> components = new ArrayList<>();
  private volatile SystemPartitionStateMachine stateMachine;
  private volatile SystemPartitionSnapshotDirector snapshotDirector;
  private volatile SystemPartitionRoleController roleController;

  SystemPartition(
      final RaftPartition raftPartition,
      final FileBasedSnapshotStore snapshotStore,
      final MeterRegistry meterRegistry,
      final Duration snapshotPeriod) {
    this.raftPartition = raftPartition;
    this.snapshotStore = snapshotStore;
    this.meterRegistry = meterRegistry;
    this.snapshotPeriod = snapshotPeriod;
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
   * Recovers the key-value store from the latest snapshot, then starts applying the committed log
   * to it, snapshotting it periodically, and reacting to role transitions. Must be called after
   * {@link #bootstrap(PartitionManagementService)} completed.
   */
  public synchronized void start(final ActorSchedulingService scheduler) {
    final var stateController =
        new SystemPartitionStateController(
            snapshotStore,
            new AtomixRecordEntrySupplierImpl(raftPartition.getServer()),
            keyValueStore);
    final var recoveredPosition = stateController.recover();
    stateMachine = new SystemPartitionStateMachine(raftPartition, keyValueStore, recoveredPosition);
    snapshotDirector =
        new SystemPartitionSnapshotDirector(
            stateController, stateMachine, raftPartition, snapshotPeriod);
    roleController =
        new SystemPartitionRoleController(raftPartition, stateMachine, pendingListeners);
    pendingListeners.clear();
    scheduler.submitActor(stateMachine);
    scheduler.submitActor(snapshotDirector);
    scheduler.submitActor(roleController);
    components.forEach(scheduler::submitActor);
  }

  /**
   * Adds an actor built on top of the partition, such as a failure detector. It is started by
   * {@link #start(ActorSchedulingService)}, after the partition's own actors, and closed along with
   * them. Must be called before {@link #start(ActorSchedulingService)}.
   */
  public synchronized void addComponent(final Actor component) {
    if (stateMachine != null) {
      throw new IllegalStateException("Expected to add components before the partition started");
    }
    components.add(component);
  }

  /**
   * Returns a future completed once this broker applied every entry it had committed when the
   * partition started. Values watched before replay is done may be historical. Must be called after
   * {@link #start(ActorSchedulingService)}.
   */
  public ActorFuture<Void> awaitReplayed() {
    final var started = stateMachine;
    if (started == null) {
      throw new IllegalStateException("Expected the system partition to be started");
    }
    return started.awaitReplayed();
  }

  /**
   * Adds a listener of this broker's role transitions, which hands out the key-value writer while
   * this broker is the leader. A listener added while this broker is the leader is notified right
   * away.
   */
  public synchronized void addListener(final SystemPartitionListener listener) {
    if (roleController == null) {
      pendingListeners.add(listener);
    } else {
      roleController.addListener(listener);
    }
  }

  public synchronized void removeListener(final SystemPartitionListener listener) {
    if (roleController == null) {
      pendingListeners.remove(listener);
    } else {
      roleController.removeListener(listener);
    }
  }

  /** Returns the reader of the key-value store as replicated to this broker. */
  public SystemKeyValueReader keyValueReader() {
    return keyValueStore;
  }

  /** Returns the state as of the latest applied entry, or empty before the partition started. */
  public Optional<SystemPartitionState> state() {
    return Optional.ofNullable(stateMachine).map(SystemPartitionStateMachine::state);
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

  /** Returns the actors started by {@link #start(ActorSchedulingService)}, if any. */
  synchronized List<Actor> actors() {
    if (stateMachine == null) {
      return List.of();
    }
    return Stream.concat(
            components.stream(), Stream.<Actor>of(roleController, snapshotDirector, stateMachine))
        .filter(Objects::nonNull)
        .toList();
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
