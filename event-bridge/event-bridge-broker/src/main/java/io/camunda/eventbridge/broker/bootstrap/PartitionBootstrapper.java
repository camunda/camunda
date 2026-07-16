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
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.eventbridge.broker.BrokerMembers;
import io.camunda.eventbridge.broker.compaction.CompactionConfig;
import io.camunda.eventbridge.broker.compaction.CompactionPartitionWiring;
import io.camunda.eventbridge.broker.compaction.CompactionPartitionWiring.CompactionRuntime;
import io.camunda.eventbridge.broker.compaction.SnapshotManifestStore;
import io.camunda.eventbridge.broker.logstreams.LogRetentionCompactor;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory;
import io.camunda.eventbridge.broker.partitioning.PartitionFactory.CreatedPartition;
import io.camunda.eventbridge.broker.partitioning.PartitionLifecycle;
import io.camunda.eventbridge.broker.partitioning.RoundRobinPartitionDistributor;
import io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.stream.MetadataPartition;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorPartition;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.partition.PartitionLeaderReporter;
import io.camunda.eventbridge.core.topic.TopicGroups;
import io.camunda.eventbridge.messaging.stream.EventStreamReader;
import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.agrona.concurrent.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the auxiliary Raft groups (coordinator + metadata) this broker hosts and owns runtime
 * provisioning of per-topic data partitions. There is no default data group: every data partition
 * belongs to a per-topic Raft group, started by the {@link TopicReconciler} from the replicated
 * topic registry rather than from a boot-time cluster configuration.
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

    // No default data group is started here: every data partition belongs to a per-topic Raft
    // group,
    // provisioned by the TopicReconciler from the replicated registry (config-declared topics are
    // auto-created by the metadata leader). Only the coordinator and metadata groups bootstrap
    // here.
    LOG.info("Bootstrapping EventBridge coordinator + metadata groups (local: {})", localMemberId);

    factory = new PartitionFactory(properties, actorScheduler);
    managementService =
        new DefaultPartitionManagementService(membershipService, cluster.getCommunicationService());

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

    final var partitionCount = Math.max(1, properties.coordinator().partitionCount());
    // Reuse the same round-robin distribution as the data partitions, but in the coordinator group.
    final var distribution =
        groupDistribution(PartitionFactory.COORDINATOR_GROUP_NAME, partitionCount);

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

    bootstrapAndWireRole(
        "Coordinator", partitionId, created, managementService, coordinatorPartition::onRoleChange);
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

    // Single partition: the topic registry is a cluster-wide namespace, not sharded.
    final var distribution = groupDistribution(PartitionFactory.METADATA_GROUP_NAME, 1);

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
            reconfigurationExecutor,
            properties.resolvedTopics(),
            properties.rebalance().enabled(),
            Duration.ofMillis(properties.rebalance().intervalMs()),
            properties.rebalance().minImbalance());
    registry.addAuxiliary(created, metadataPartition);
    actorScheduler.submitActor(metadataPartition);

    // Voting members bootstrap the group; everyone else joins as a non-voting passive observer.
    // In both cases register the role-change listener only AFTER the Raft partition is started
    // (replays the current role) — see the note in bootstrapAndWireRole: registering before would
    // pin the StreamProcessor's committed reader to a segment that startup resets.
    if (observe) {
      new MetadataPassiveJoiner(
              partitionId,
              created.raftPartition(),
              created.snapshotStore(),
              managementService,
              executorService,
              () -> closing,
              () -> created.raftPartition().addRoleChangeListener(metadataPartition::onRoleChange))
          .start();
    } else {
      bootstrapAndWireRole(
          "Metadata", partitionId, created, managementService, metadataPartition::onRoleChange);
    }
  }

  /**
   * The round-robin partition distribution for an auxiliary Raft group (coordinator or metadata)
   * over the configured cluster, replicated at the cluster-capped replication factor. The same
   * shape the data partitions use, just in the named group.
   */
  private Set<PartitionMetadata> groupDistribution(
      final String groupName, final int partitionCount) {
    final var clusterSize = properties.cluster().clusterSize();
    final var members = BrokerMembers.all(clusterSize);
    final var replicationFactor = Math.min(properties.raft().replicationFactor(), clusterSize);
    return new RoundRobinPartitionDistributor(groupName)
        .distributePartitions(members, partitionCount, replicationFactor);
  }

  /**
   * Bootstraps a voting member's Raft partition FIRST, then registers the role-change listener —
   * mirroring Zeebe's partition startup (RaftBootstrapStep before ZeebePartitionStep). The listener
   * replays the current role on registration, so we don't miss the initial transition. If we
   * registered before bootstrap, the StreamProcessor (and its committed log reader) would be
   * created while the journal is still being initialized/reset, pinning the reader to a segment
   * that bootstrap then closes — so followers would never replay (their reader stays
   * SEGMENT-NOT-OPEN).
   */
  private void bootstrapAndWireRole(
      final String groupLabel,
      final int partitionId,
      final CreatedPartition created,
      final DefaultPartitionManagementService managementService,
      final RaftRoleChangeListener roleListener) {
    created
        .raftPartition()
        .bootstrap(managementService, created.snapshotStore())
        .whenComplete(
            (rp, error) -> {
              if (error != null) {
                LOG.error(
                    "Failed to bootstrap {} raft partition {}", groupLabel, partitionId, error);
              } else {
                LOG.info("{} raft partition {} bootstrapped", groupLabel, partitionId);
                created.raftPartition().addRoleChangeListener(roleListener);
              }
            });
  }

  /**
   * Logs the outcome of a runtime Raft reconfiguration ({@code action} on {@code
   * groupName/partitionId}) and adapts it to {@code CompletableFuture<Void>}. Failures are warned
   * (the change-coordinator retries the step); successes are logged at info.
   */
  private static <T> CompletableFuture<Void> logCompletion(
      final CompletableFuture<T> future,
      final String action,
      final String groupName,
      final int partitionId) {
    return future
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                LOG.warn(
                    "Failed {} for raft partition {}/{}", action, groupName, partitionId, error);
              } else {
                LOG.info("Completed {} for raft partition {}/{}", action, groupName, partitionId);
              }
            })
        .thenApply(result -> null);
  }

  void stop() {
    closing = true;
    registry.closeAll();
  }

  /**
   * Provisions a data-style partition (event log) for a per-topic Raft group and bootstraps it.
   * Driven by the {@link TopicReconciler} from the replicated registry: create raft components,
   * wire lifecycle + retention/compaction + role listener, then bootstrap.
   */
  void provisionDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager,
      final CleanupPolicy cleanupPolicy) {
    startDataPartition(
        groupName, partitionId, members, topologyManager, cleanupPolicy, false, false);
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
      final TopologyManagerImpl topologyManager,
      final CleanupPolicy cleanupPolicy) {
    return startDataPartition(
        groupName, partitionId, members, topologyManager, cleanupPolicy, true, false);
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
      final TopologyManagerImpl topologyManager,
      final CleanupPolicy cleanupPolicy) {
    return startDataPartition(
        groupName, partitionId, members, topologyManager, cleanupPolicy, true, true);
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
    return logCompletion(
        provisioned.created().raftPartition().promoteMember(BrokerMembers.memberId(memberNodeId)),
        "promote of member " + memberNodeId,
        groupName,
        partitionId);
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
    return logCompletion(
        provisioned.created().raftPartition().leave(), "leave", groupName, partitionId);
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
    return logCompletion(
        provisioned.created().raftPartition().removeMember(BrokerMembers.memberId(memberNodeId)),
        "removal of member " + memberNodeId,
        groupName,
        partitionId);
  }

  private CompletableFuture<Void> startDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager,
      final CleanupPolicy cleanupPolicy,
      final boolean join,
      final boolean passive) {

    if (isRunning(groupName, partitionId)) {
      return CompletableFuture.completedFuture(null); // idempotent
    }

    // 1. Create raft-level components
    final var created = factory.createData(groupName, partitionId, members, localMemberId);

    // The gateway routing group is the topic's own Raft group name, so the per-group handler
    // subjects
    // don't collide across topics and the gateway resolves each topic's partition leaders
    // separately.
    final var routingGroup = groupName;

    // 2a. A COMPACT partition's manifest store + reader-lease registry are built before the
    // lifecycle so they can be stamped into the immutable PartitionContext once (see
    // PartitionContext's javadoc on why the policy check is cache-free). Null for DELETE.
    final CompactionRuntime compactionRuntime =
        cleanupPolicy == CleanupPolicy.COMPACT
            ? buildCompactionRuntime(groupName, partitionId, created)
            : null;

    // 2b. Create lifecycle actor. On becoming leader it reports its leadership to the metadata
    // group
    // so topic readiness (CREATING → ACTIVE) is derived from the partition leaders.
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
            executorService,
            cleanupPolicy,
            compactionRuntime != null ? compactionRuntime.manifestStore() : null,
            compactionRuntime != null ? compactionRuntime.leaseRegistry() : null);
    actorScheduler.submitActor(lifecycle);

    // 2c. Retention compaction (DELETE) or log compaction (COMPACT) runs on every replica (leader
    // and followers), not just the leader: each node trims/cleans its own committed log prefix
    // independently. This is safe — Raft guarantees an identical committed prefix everywhere, so
    // replicas differ only in how far back they retain, never in shared content — and it bounds
    // disk on followers without waiting for promotion. The two policies are mutually exclusive.
    Actor compactor = null;
    if (cleanupPolicy == CleanupPolicy.COMPACT) {
      compactor = compactionRuntime.cleaner();
      actorScheduler.submitActor(compactor);
    } else if (properties.retention().maxRecordsPerPartition() > 0) {
      compactor =
          new LogRetentionCompactor(
              partitionId,
              created.raftPartition(),
              (ConstructableSnapshotStore) created.snapshotStore(),
              properties.retention().maxRecordsPerPartition(),
              Duration.ofMillis(properties.retention().compactionIntervalMs()));
      actorScheduler.submitActor(compactor);
    }

    registry.addData(groupName, partitionId, created, lifecycle, compactor);

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

    final var result = new CompletableFuture<Void>();
    started.whenComplete(
        (rp, error) -> {
          if (error == null) {
            LOG.info(
                "Raft partition {}/{} {}",
                groupName,
                partitionId,
                join ? "joined" : "bootstrapped");
            result.complete(null);
            return;
          }
          // A join/bootstrap can fail transiently — most often the group has no leader yet (an
          // election in progress when the change-coordinator's step or the reconcile arrives). The
          // change-coordinator (and the 1s reconcile) retry, but a retry must start clean: roll
          // back
          // tracking AND close the half-started Raft server so its Raft message subjects are
          // unregistered, otherwise the retry's fresh server for the same (group, partition)
          // collides
          // on those subjects and can never come up. Mirrors MetadataPassiveJoiner's close-before-
          // retry; we close here (rather than loop internally) so we don't race the coordinator's
          // own
          // retry. The failure is propagated only after the close completes.
          LOG.warn(
              "Failed to {} raft partition {}/{}; closing the half-started server so a retry starts"
                  + " clean",
              join ? "join" : "bootstrap",
              groupName,
              partitionId,
              error);
          registry.removeData(groupName, partitionId);
          closeFailedRaft(created, groupName, partitionId)
              .whenComplete((ignored, closeError) -> result.completeExceptionally(error));
        });
    return result;
  }

  /**
   * Closes a half-started Raft server (and its snapshot store) after a failed bootstrap/join, so
   * its Raft message subjects are unregistered before a retry recreates them. Best-effort: close
   * errors are logged, never propagated.
   */
  private CompletableFuture<Void> closeFailedRaft(
      final CreatedPartition created, final String groupName, final int partitionId) {
    return created
        .raftPartition()
        .close()
        .handle(
            (ignored, error) -> {
              if (error != null) {
                LOG.warn(
                    "Error closing half-started raft partition {}/{} after a failed start",
                    groupName,
                    partitionId,
                    error);
              }
              if (created.snapshotStore() instanceof final Actor snapshotActor) {
                snapshotActor.closeAsync();
              }
              return null;
            });
  }

  // A read-only appender: the cleaner's dirty-log reader never writes, so no entry ever reaches it
  // (mirrors RaftPartitionLifecycle's follower-role appender for the same reason).
  private static final ZeebeLogAppender NOOP_APPENDER = (entry, listener) -> {};

  /**
   * Builds the compaction runtime (manifest store + reader-lease registry + cleaner) for one
   * replica of a {@code COMPACT} partition. Runs unconditionally regardless of raft role — every
   * replica cleans its own committed log prefix independently (ADR 0001, decision 6), persists its
   * own snapshots, and receives lagging-follower catch-up through the existing InstallSnapshot
   * machinery (decision 10) — nothing extra is wired for that here, see {@link
   * SnapshotManifestStore}'s javadoc.
   */
  private CompactionRuntime buildCompactionRuntime(
      final String groupName, final int partitionId, final CreatedPartition created) {
    final var compactionDirectory =
        factory.getPartitionDirectory(groupName, partitionId).resolve("compaction");
    try {
      FileUtil.ensureDirectoryExists(compactionDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to create compaction directory for partition " + partitionId, e);
    }

    final var raftPartition = created.raftPartition();
    // Fetched fresh on every call, exactly like dirtyLogReaderSupplier/lastCommittedPosition below:
    // AtomixRecordEntrySupplierImpl binds to a specific RaftPartitionServer instance at
    // construction, and the server does not exist yet at this point (it is created during raft
    // bootstrap, which runs after this method returns) — constructing it eagerly here would bake
    // in a permanent null.
    final AtomixRecordEntrySupplier entrySupplier =
        position -> {
          final var server = raftPartition.getServer();
          return server == null
              ? Optional.empty()
              : new AtomixRecordEntrySupplierImpl(server).getPreviousIndexedEntry(position);
        };
    final var manifestStore =
        new SnapshotManifestStore(
            partitionId,
            compactionDirectory,
            (ConstructableSnapshotStore) created.snapshotStore(),
            entrySupplier);

    final var cfg = properties.compaction();
    final var compactionConfig =
        new CompactionConfig(
            cfg.minLagRecords(),
            cfg.maxSegmentBytes(),
            Duration.ofMillis(cfg.graceWindowMs()),
            cfg.keyMapCapacity(),
            Duration.ofMillis(cfg.passIntervalMs()));

    final Supplier<EventStreamReader> dirtyLogReaderSupplier =
        () -> {
          // Fetched fresh on every pass: the server is created during raft bootstrap, after this
          // supplier is built (mirrors LogRetentionCompactor.compact()'s own fresh fetch).
          final var server = raftPartition.getServer();
          if (server == null) {
            throw new IllegalStateException(
                "Partition " + partitionId + " — raft server not yet available for compaction");
          }
          return new EventStreamReader(
              AtomixLogStorage.ofPartition(server::openReader, NOOP_APPENDER).newReader());
        };
    final LongSupplier lastCommittedPosition =
        () -> lastCommittedPosition(raftPartition, partitionId);

    return CompactionPartitionWiring.build(
        partitionId,
        compactionDirectory,
        manifestStore,
        dirtyLogReaderSupplier,
        lastCommittedPosition,
        compactionConfig);
  }

  /**
   * Reads the highest committed record position from this replica's own Raft log, or {@code -1} if
   * the log is empty or not yet readable. Mirrors {@code LogRetentionCompactor}'s identically-named
   * private helper; duplicated rather than shared to avoid touching that already-battle-tested
   * class for this change (DELETE-policy partitions must be byte-for-byte unaffected).
   */
  private static long lastCommittedPosition(
      final RaftPartition raftPartition, final int partitionId) {
    final var server = raftPartition.getServer();
    if (server == null) {
      return -1L;
    }
    try (final var reader = server.openReader()) {
      reader.seekToLast();
      if (!reader.hasNext()) {
        return -1L;
      }
      final var entry = reader.next();
      return entry.isApplicationEntry() ? entry.getApplicationEntry().highestPosition() : -1L;
    } catch (final Exception e) {
      LOG.debug(
          "Partition {} — could not read last committed position for compaction", partitionId, e);
      return -1L;
    }
  }
}
