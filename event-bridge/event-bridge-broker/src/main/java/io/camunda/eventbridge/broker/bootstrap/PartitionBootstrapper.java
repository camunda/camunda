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
import io.camunda.eventbridge.broker.BrokerMembers;
import io.camunda.eventbridge.broker.logstreams.LogRetentionCompactor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.broker.partitioning.PartitionLeaderReporter;
import io.camunda.eventbridge.broker.partitioning.PartitionLifecycle;
import io.camunda.eventbridge.broker.partitioning.RoundRobinPartitionDistributor;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.stream.MetadataPartition;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorPartition;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.topic.TopicGroups;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
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

  private final AtomixCluster cluster;
  private final ActorSchedulingService actorScheduler;
  private final EventBridgeProperties properties;
  private final InstantSource clock;
  private final IdGenerator idGenerator;
  private final ExecutorService executorService;
  private final PartitionLeaderReporter leaderReporter;

  // Set by stop() so in-flight metadata passive-join retries bail instead of scheduling more work.
  private volatile boolean closing;

  // The inventory of every partition replica this broker hosts (data, coordinator, metadata) and
  // the owner of their teardown; data replicas are addressable here for runtime reconfiguration.
  private final PartitionRegistry registry = new PartitionRegistry();

  // Initialized in start(); reused by runtime topic-group provisioning after boot.
  private PartitionFactory factory;
  private DefaultPartitionManagementService managementService;
  private MessagingService brokerMessagingService;
  private MemberId localMemberId;
  private TopicRegistry topicRegistry;
  private Consumer<Map<String, TopicMetadata>> registryReconciler;
  private ReconfigurationExecutor reconfigurationExecutor;

  PartitionBootstrapper(
      final AtomixCluster cluster,
      final ActorSchedulingService actorScheduler,
      final EventBridgeProperties properties,
      final InstantSource clock,
      final IdGenerator idGenerator,
      final ExecutorService executorService,
      final PartitionLeaderReporter leaderReporter) {
    this.cluster = cluster;
    this.actorScheduler = actorScheduler;
    this.properties = properties;
    this.clock = clock;
    this.idGenerator = idGenerator;
    this.executorService = executorService;
    this.leaderReporter = leaderReporter;
  }

  void start(
      final Set<PartitionMetadata> distribution,
      final TopologyManagerImpl topologyManager,
      final TopologyManagerImpl coordinatorTopologyManager,
      final TopologyManagerImpl metadataTopologyManager,
      final MessagingService brokerMessagingService,
      final TopicRegistry topicRegistry,
      final Consumer<Map<String, TopicMetadata>> registryReconciler,
      final ReconfigurationExecutor reconfigurationExecutor) {

    final var membershipService = cluster.getMembershipService();
    localMemberId = membershipService.getLocalMember().id();
    this.brokerMessagingService = brokerMessagingService;
    this.topicRegistry = topicRegistry;
    this.registryReconciler = registryReconciler;
    this.reconfigurationExecutor = reconfigurationExecutor;

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

    bootstrapMetadata(
        localMemberId, factory, managementService, brokerMessagingService, metadataTopologyManager);
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
    final var members = BrokerMembers.all(clusterSize);
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

    final var runtimeDirectory =
        factory
            .getPartitionDirectory(PartitionFactory.COORDINATOR_GROUP_NAME, partitionId)
            .resolve("runtime");

    final var coordinatorPartition =
        new CoordinatorPartition(
            partitionId,
            topicRegistry,
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            runtimeDirectory,
            (ConstructableSnapshotStore) created.snapshotStore(),
            coordinatorTopologyManager);
    registry.addAuxiliary(created, coordinatorPartition);
    actorScheduler.submitActor(coordinatorPartition);

    // Bootstrap the Raft partition FIRST, then register the role-change listener — mirroring
    // Zeebe's
    // partition startup (RaftBootstrapStep before ZeebePartitionStep). The listener replays the
    // current role on registration, so we don't miss the initial transition. If we registered
    // before bootstrap, the StreamProcessor (and its committed log reader) would be created while
    // the journal is still being initialized/reset, pinning the reader to a segment that bootstrap
    // then closes — so followers would never replay (their reader stays SEGMENT-NOT-OPEN).
    created
        .raftPartition()
        .bootstrap(managementService, created.snapshotStore())
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.error("Failed to bootstrap coordinator raft partition {}", partitionId, error);
              } else {
                LOG.info("Coordinator raft partition {} bootstrapped", partitionId);
                created
                    .raftPartition()
                    .addRoleChangeListener(
                        (role, term) -> coordinatorPartition.onRoleChange(role, term));
              }
            });
  }

  /**
   * Bootstraps the dedicated metadata Raft group — a single partition (the topic registry is a
   * cluster-wide namespace, not sharded) replicated across {@code replicationFactor} members. Its
   * leader runs the {@code MetadataManager} over a {@code MetadataStream}; followers replay the
   * committed topic log so failover is clean. Mirrors {@link #bootstrapCoordinator} but with a
   * fixed partition count of 1.
   */
  private void bootstrapMetadata(
      final MemberId localMemberId,
      final PartitionFactory factory,
      final DefaultPartitionManagementService managementService,
      final MessagingService brokerMessagingService,
      final TopologyManagerImpl metadataTopologyManager) {

    final var clusterSize = properties.cluster().clusterSize();
    final var members = BrokerMembers.all(clusterSize);
    final var replicationFactor = Math.min(properties.raft().replicationFactor(), clusterSize);

    // Single partition: the topic registry is a cluster-wide namespace, not sharded.
    final var distribution =
        new RoundRobinPartitionDistributor(PartitionFactory.METADATA_GROUP_NAME)
            .distributePartitions(members, 1, replicationFactor);

    // Every broker observes the metadata group so it can reconcile its topic groups from the
    // group's replicated registry: the RF chosen brokers are voting members (bootstrap), all others
    // join as non-voting PASSIVE observers (they replicate the registry without affecting quorum).
    distribution.forEach(
        partition -> {
          final var votingMembers = Set.copyOf(partition.members());
          final var observe = !votingMembers.contains(localMemberId);
          bootstrapMetadataPartition(
              partition.id().id(),
              votingMembers,
              localMemberId,
              observe,
              factory,
              managementService,
              brokerMessagingService,
              metadataTopologyManager);
        });
  }

  private void bootstrapMetadataPartition(
      final int partitionId,
      final Set<MemberId> members,
      final MemberId localMemberId,
      final boolean observe,
      final PartitionFactory factory,
      final DefaultPartitionManagementService managementService,
      final MessagingService brokerMessagingService,
      final TopologyManagerImpl metadataTopologyManager) {

    final var created = factory.createMetadata(partitionId, members, localMemberId);

    final var runtimeDirectory =
        factory
            .getPartitionDirectory(PartitionFactory.METADATA_GROUP_NAME, partitionId)
            .resolve("runtime");

    final var metadataPartition =
        new MetadataPartition(
            partitionId,
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            runtimeDirectory,
            (ConstructableSnapshotStore) created.snapshotStore(),
            metadataTopologyManager,
            registryReconciler,
            reconfigurationExecutor);
    registry.addAuxiliary(created, metadataPartition);
    actorScheduler.submitActor(metadataPartition);

    // Voting members bootstrap the group; everyone else joins as a non-voting passive observer.
    // In both cases register the role-change listener only AFTER the Raft partition is started
    // (replays the current role) — see the note in bootstrapCoordinatorPartition: registering
    // before would pin the StreamProcessor's committed reader to a segment that startup resets.
    if (observe) {
      new MetadataPassiveJoiner(
              partitionId,
              created.raftPartition(),
              created.snapshotStore(),
              managementService,
              executorService,
              () -> closing,
              () ->
                  created
                      .raftPartition()
                      .addRoleChangeListener(
                          (role, term) -> metadataPartition.onRoleChange(role, term)))
          .start();
    } else {
      created
          .raftPartition()
          .bootstrap(managementService, created.snapshotStore())
          .whenComplete(
              (rp, error) -> {
                if (error != null) {
                  LOG.error("Failed to bootstrap metadata raft partition {}", partitionId, error);
                } else {
                  LOG.info("Metadata raft partition {} bootstrapped", partitionId);
                  created
                      .raftPartition()
                      .addRoleChangeListener(
                          (role, term) -> metadataPartition.onRoleChange(role, term));
                }
              });
    }
  }

  void stop() {
    closing = true;
    registry.closeAll();
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
    startDataPartition(groupName, partitionId, members, topologyManager, false, false);
  }

  /**
   * Joins an already-running Raft group as a new voting replica at runtime (reassignment add). Same
   * setup as {@link #provisionDataPartition} but the Raft server joins the existing cluster
   * (catching up via the leader) instead of bootstrapping a new one.
   */
  CompletableFuture<Void> joinDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager) {
    return startDataPartition(groupName, partitionId, members, topologyManager, true, false);
  }

  /**
   * Joins an already-running Raft group as a non-voting PASSIVE observer (grow-first heal step 1):
   * it replicates and catches up without affecting quorum, to be promoted to voting later by the
   * partition leader.
   */
  CompletableFuture<Void> joinDataPartitionAsPassive(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager) {
    return startDataPartition(groupName, partitionId, members, topologyManager, true, true);
  }

  /**
   * Promotes a (caught-up) passive member of a data partition's Raft group to a voting replica,
   * driven by this broker as the partition leader (grow-first heal step 2). The promotion is
   * catch-up gated inside Atomix.
   */
  CompletableFuture<Void> promoteMemberInDataPartition(
      final String groupName, final int partitionId, final int memberNodeId) {
    final var provisioned = registry.data(groupName, partitionId);
    if (provisioned == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Cannot promote member "
                  + memberNodeId
                  + " in "
                  + groupName
                  + "/"
                  + partitionId
                  + ": partition not hosted on this broker"));
    }
    return provisioned
        .created()
        .raftPartition()
        .promoteMember(BrokerMembers.memberId(memberNodeId))
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.warn(
                    "Error promoting member {} in raft partition {}/{}",
                    memberNodeId,
                    groupName,
                    partitionId,
                    error);
              } else {
                LOG.info(
                    "Promoted member {} in raft partition {}/{}",
                    memberNodeId,
                    groupName,
                    partitionId);
              }
            })
        .thenApply(rp -> null);
  }

  /** Whether this broker currently runs a replica of {@code (groupName, partitionId)}. */
  boolean isRunning(final String groupName, final int partitionId) {
    return registry.containsData(groupName, partitionId);
  }

  /**
   * Whether this broker has on-disk data for {@code (groupName, partitionId)} (restart recovery).
   */
  boolean hasData(final String groupName, final int partitionId) {
    final var dir = factory.getPartitionDirectory(groupName, partitionId);
    try (final var entries = Files.list(dir)) {
      return entries.findAny().isPresent();
    } catch (final IOException e) {
      return false;
    }
  }

  /** Leaves and tears down a runtime replica (reassignment remove). Idempotent. */
  CompletableFuture<Void> leaveDataPartition(final String groupName, final int partitionId) {
    final var provisioned = registry.removeData(groupName, partitionId);
    if (provisioned == null) {
      return CompletableFuture.completedFuture(null);
    }
    return provisioned
        .created()
        .raftPartition()
        .leave()
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.warn("Error leaving raft partition {}/{}", groupName, partitionId, error);
              } else {
                LOG.info("Left raft partition {}/{}", groupName, partitionId);
              }
            })
        .thenApply(rp -> null);
  }

  /**
   * Removes another member from a data partition's Raft group, driven by this (surviving) replica —
   * used to evict a dead/fenced member that cannot leave on its own. This broker keeps its own
   * replica running; only the named member is removed from the group's configuration.
   */
  CompletableFuture<Void> removeMemberFromDataPartition(
      final String groupName, final int partitionId, final int memberNodeId) {
    final var provisioned = registry.data(groupName, partitionId);
    if (provisioned == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Cannot remove member "
                  + memberNodeId
                  + " from "
                  + groupName
                  + "/"
                  + partitionId
                  + ": partition not hosted on this broker"));
    }
    return provisioned
        .created()
        .raftPartition()
        .removeMember(BrokerMembers.memberId(memberNodeId))
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.warn(
                    "Error removing member {} from raft partition {}/{}",
                    memberNodeId,
                    groupName,
                    partitionId,
                    error);
              } else {
                LOG.info(
                    "Removed member {} from raft partition {}/{}",
                    memberNodeId,
                    groupName,
                    partitionId);
              }
            })
        .thenApply(rp -> null);
  }

  private CompletableFuture<Void> startDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager,
      final boolean join,
      final boolean passive) {

    if (isRunning(groupName, partitionId)) {
      return CompletableFuture.completedFuture(null); // idempotent
    }

    // 1. Create raft-level components
    final var created = factory.createData(groupName, partitionId, members, localMemberId);

    // The gateway routing group: data partitions use the BrokerClient's default group, topic groups
    // route under their own Raft group name (so handler subjects don't collide across groups).
    final var routingGroup =
        PartitionFactory.GROUP_NAME.equals(groupName)
            ? Protocol.DEFAULT_PARTITION_GROUP_NAME
            : groupName;

    // 2. Create lifecycle actor. For a topic-registry group, on becoming leader it reports its
    // leadership to the metadata group (so topic readiness is derived); the default data group does
    // not (topic == null → no-op reporter).
    final var topic = TopicGroups.topicFrom(groupName);
    final var lifecycle =
        new PartitionLifecycle(
            partitionId,
            properties.broker().partitionCount(),
            routingGroup,
            topic,
            BrokerMembers.nodeId(localMemberId),
            leaderReporter,
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            idGenerator,
            topologyManager,
            executorService);
    actorScheduler.submitActor(lifecycle);

    // 2b. Retention compaction runs on every replica (leader and followers), not just the leader:
    // each node trims its own committed log prefix independently. This is safe — Raft guarantees an
    // identical committed prefix everywhere, so replicas differ only in how far back they retain,
    // never in shared content — and it bounds disk on followers without waiting for promotion.
    LogRetentionCompactor retentionCompactor = null;
    if (properties.retention().maxRecordsPerPartition() > 0) {
      retentionCompactor =
          new LogRetentionCompactor(
              partitionId,
              created.raftPartition(),
              (ConstructableSnapshotStore) created.snapshotStore(),
              properties.retention().maxRecordsPerPartition(),
              Duration.ofMillis(properties.retention().compactionIntervalMs()));
      actorScheduler.submitActor(retentionCompactor);
    }

    registry.addData(groupName, partitionId, created, lifecycle, retentionCompactor);

    // 3. Wire raft role changes to lifecycle — before start so no events are lost. The term is the
    // leader-epoch the lifecycle reports to the metadata group on becoming leader.
    created.raftPartition().addRoleChangeListener(lifecycle::onRoleChange);

    // 4. Start raft — bootstrap a new group, or join an existing one for a reassignment add. A
    // grow-first heal joins as a non-voting PASSIVE observer first (it catches up without affecting
    // quorum) and is promoted to voting later by the partition leader.
    final var started =
        join
            ? (passive
                ? created.raftPartition().joinAsPassive(managementService, created.snapshotStore())
                : created.raftPartition().join(managementService, created.snapshotStore()))
            : created.raftPartition().bootstrap(managementService, created.snapshotStore());
    return started
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.error(
                    "Failed to {} raft partition {}/{}",
                    join ? "join" : "bootstrap",
                    groupName,
                    partitionId,
                    error);
                // Roll back tracking (and close the lifecycle + compactor) so a retry genuinely
                // re-attempts and committed state is not advanced on a failed join (it would
                // otherwise look "running" and no-op the retry).
                registry.removeData(groupName, partitionId);
              } else {
                LOG.info(
                    "Raft partition {}/{} {}",
                    groupName,
                    partitionId,
                    join ? "joined" : "bootstrapped");
              }
            })
        .thenApply(rp -> null);
  }
}
