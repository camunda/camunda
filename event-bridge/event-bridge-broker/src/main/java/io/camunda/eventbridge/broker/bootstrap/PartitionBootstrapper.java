/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.camunda.eventbridge.broker.logstreams.LogRetentionCompactor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory.CreatedPartition;
import io.camunda.eventbridge.broker.partitioning.PartitionLifecycle;
import io.camunda.eventbridge.broker.partitioning.RoundRobinPartitionDistributor;
import io.camunda.eventbridge.coordinator.stream.CoordinatorPartition;
import io.camunda.eventbridge.coordinator.stream.TopicAssignmentGossip;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.agrona.concurrent.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the raft partitions assigned to the local node by the cluster configuration. Mirrors
 * Zeebe's {@code PartitionManagerImpl}: the partition distribution is derived from the (single
 * source of truth) cluster configuration, and the local node starts the partitions whose members
 * include it.
 */
final class PartitionBootstrapper {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionBootstrapper.class);
  private static final int CLOSE_TIMEOUT_SECONDS = 30;

  private final AtomixCluster cluster;
  private final ActorSchedulingService actorScheduler;
  private final EventBridgeProperties properties;
  private final InstantSource clock;
  private final IdGenerator idGenerator;
  private final ExecutorService executorService;

  // CopyOnWrite: boot mutates these on the start thread, runtime topic provisioning on the
  // reconciler thread, and stop() iterates them — so all access must be thread-safe.
  private final List<CreatedPartition> createdPartitions = new CopyOnWriteArrayList<>();
  private final List<PartitionLifecycle> lifecycles = new CopyOnWriteArrayList<>();
  private final List<LogRetentionCompactor> retentionCompactors = new CopyOnWriteArrayList<>();
  private final List<CoordinatorPartition> coordinatorPartitions = new CopyOnWriteArrayList<>();

  // Initialized in start(); reused by runtime topic-group provisioning after boot.
  private PartitionFactory factory;
  private DefaultPartitionManagementService managementService;
  private MessagingService brokerMessagingService;
  private MemberId localMemberId;
  private TopicAssignmentGossip.Publisher topicAssignmentPublisher;

  PartitionBootstrapper(
      final AtomixCluster cluster,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final InstantSource clock,
      final IdGenerator idGenerator,
      final ExecutorService executorService) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.clock = clock;
    this.idGenerator = idGenerator;
    this.executorService = executorService;
  }

  void start(
      final Set<PartitionMetadata> distribution,
      final TopologyManagerImpl topologyManager,
      final TopologyManagerImpl coordinatorTopologyManager,
      final MessagingService brokerMessagingService,
      final TopicAssignmentGossip.Publisher topicAssignmentPublisher) {

    final var membershipService = cluster.getMembershipService();
    localMemberId = membershipService.getLocalMember().id();
    this.brokerMessagingService = brokerMessagingService;
    this.topicAssignmentPublisher = topicAssignmentPublisher;

    // Start the partitions assigned to this node (members include the local member), exactly like
    // Zeebe's PartitionManagerImpl derives placement from the cluster configuration.
    final var localPartitions =
        distribution.stream().filter(p -> p.members().contains(localMemberId)).toList();

    LOG.info(
        "Bootstrapping EventBridge partitions {} (local: {})",
        localPartitions.stream().map(p -> p.id().id()).sorted().toList(),
        localMemberId);

    factory = new PartitionFactory(properties, actorScheduler);
    managementService =
        new DefaultPartitionManagementService(membershipService, cluster.getCommunicationService());

    for (final var partition : localPartitions) {
      provisionDataPartition(
          PartitionFactory.GROUP_NAME,
          partition.id().id(),
          Set.copyOf(partition.members()),
          topologyManager);
    }

    bootstrapCoordinator(
        localMemberId,
        factory,
        managementService,
        brokerMessagingService,
        coordinatorTopologyManager);
  }

  /**
   * Bootstraps the dedicated coordinator Raft group, sharded into {@code
   * coordinator.partitionCount} partitions (each replicated across {@code replicationFactor}
   * members). A group is owned by exactly one shard (by {@code groupId} hash). Each shard's leader
   * runs the coordinator over a {@code StreamProcessor}; followers replay the committed offset log
   * so failover is clean.
   */
  private void bootstrapCoordinator(
      final MemberId localMemberId,
      final PartitionFactory factory,
      final DefaultPartitionManagementService managementService,
      final MessagingService brokerMessagingService,
      final TopologyManagerImpl coordinatorTopologyManager) {

    final var clusterSize = properties.cluster().clusterSize();
    final var members =
        IntStream.range(0, clusterSize)
            .mapToObj(i -> MemberId.from("broker-" + i))
            .sorted()
            .toList();
    final var partitionCount = Math.max(1, properties.coordinator().partitionCount());
    final var replicationFactor = Math.min(properties.raft().replicationFactor(), clusterSize);

    // Reuse the same round-robin distribution as the data partitions, but in the coordinator group.
    final var distribution =
        new RoundRobinPartitionDistributor(PartitionFactory.COORDINATOR_GROUP_NAME)
            .distributePartitions(members, partitionCount, replicationFactor);

    distribution.stream()
        .filter(p -> p.members().contains(localMemberId))
        .forEach(
            partition ->
                bootstrapCoordinatorPartition(
                    partition.id().id(),
                    Set.copyOf(partition.members()),
                    localMemberId,
                    factory,
                    managementService,
                    brokerMessagingService,
                    coordinatorTopologyManager));
  }

  private void bootstrapCoordinatorPartition(
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final PartitionFactory factory,
      final DefaultPartitionManagementService managementService,
      final MessagingService brokerMessagingService,
      final TopologyManagerImpl coordinatorTopologyManager) {

    final var created = factory.createCoordinator(partitionId, members, localMemberId);
    createdPartitions.add(created);

    final var runtimeDirectory =
        factory
            .getPartitionDirectory(PartitionFactory.COORDINATOR_GROUP_NAME, partitionId)
            .resolve("runtime");

    final var coordinatorPartition =
        new CoordinatorPartition(
            partitionId,
            properties.broker().partitionCount(),
            properties.cluster().clusterSize(),
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            runtimeDirectory,
            (ConstructableSnapshotStore) created.snapshotStore(),
            coordinatorTopologyManager,
            topicAssignmentPublisher);
    coordinatorPartitions.add(coordinatorPartition);
    actorScheduler.submitActor(coordinatorPartition);

    created
        .raftPartition()
        .addRoleChangeListener((role, term) -> coordinatorPartition.onRoleChange(role, term));

    created
        .raftPartition()
        .bootstrap(managementService, created.snapshotStore())
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.error("Failed to bootstrap coordinator raft partition {}", partitionId, error);
              } else {
                LOG.info("Coordinator raft partition {} bootstrapped", partitionId);
              }
            });
  }

  void stop() {
    LOG.info("Stopping {} EventBridge partition(s)", lifecycles.size());

    for (final var lifecycle : lifecycles) {
      try {
        lifecycle.closeAsync();
      } catch (final Exception e) {
        LOG.warn("Error closing lifecycle for partition {}", lifecycle.getPartitionId(), e);
      }
    }
    lifecycles.clear();

    for (final var compactor : retentionCompactors) {
      try {
        compactor.closeAsync();
      } catch (final Exception e) {
        LOG.warn("Error closing retention compactor", e);
      }
    }
    retentionCompactors.clear();

    for (final var coordinatorPartition : coordinatorPartitions) {
      try {
        coordinatorPartition.closeAsync();
      } catch (final Exception e) {
        LOG.warn("Error closing coordinator partition", e);
      }
    }
    coordinatorPartitions.clear();

    for (final var partition : createdPartitions) {
      try {
        partition.raftPartition().close().get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final Exception e) {
        LOG.warn("Error closing raft partition {}", partition.partitionId(), e);
      }

      try {
        // The FileBasedSnapshotStore is an actor.
        if (partition.snapshotStore() instanceof final Actor actor) {
          actor.closeAsync();
        }
      } catch (final Exception e) {
        LOG.warn("Error closing snapshot store for partition {}", partition.partitionId(), e);
      }
    }
    createdPartitions.clear();
  }

  /**
   * Provisions a data-style partition (event log) for an arbitrary Raft group and bootstraps it.
   * Used for the default data group at boot and for per-topic groups provisioned at runtime — the
   * sequence (create raft components, wire lifecycle + retention + role listener, bootstrap) is
   * identical; only the group name, members, and topology manager differ.
   */
  void provisionDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager) {

    // 1. Create raft-level components
    final var created = factory.createData(groupName, partitionId, members, localMemberId);
    createdPartitions.add(created);

    // 2. Create lifecycle actor
    final var lifecycle =
        new PartitionLifecycle(
            partitionId,
            properties.broker().partitionCount(),
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            idGenerator,
            topologyManager,
            executorService);
    lifecycles.add(lifecycle);
    actorScheduler.submitActor(lifecycle);

    // 2b. Retention compaction runs on every replica (leader and followers), not just the leader:
    // each node trims its own committed log prefix independently. This is safe — Raft guarantees an
    // identical committed prefix everywhere, so replicas differ only in how far back they retain,
    // never in shared content — and it bounds disk on followers without waiting for promotion.
    if (properties.retention().maxRecordsPerPartition() > 0) {
      final var retentionCompactor =
          new LogRetentionCompactor(
              partitionId,
              created.raftPartition(),
              (ConstructableSnapshotStore) created.snapshotStore(),
              properties.retention().maxRecordsPerPartition(),
              Duration.ofMillis(properties.retention().compactionIntervalMs()));
      retentionCompactors.add(retentionCompactor);
      actorScheduler.submitActor(retentionCompactor);
    }

    // 3. Wire raft role changes to lifecycle — before bootstrap so no events are lost
    created.raftPartition().addRoleChangeListener((role, term) -> lifecycle.onRoleChange(role));

    // 4. Bootstrap raft — elections fire via the listener above
    created
        .raftPartition()
        .bootstrap(managementService, created.snapshotStore())
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.error("Failed to bootstrap raft partition {}", partitionId, error);
              } else {
                LOG.info("Raft partition {} bootstrapped", partitionId);
              }
            });
  }
}
