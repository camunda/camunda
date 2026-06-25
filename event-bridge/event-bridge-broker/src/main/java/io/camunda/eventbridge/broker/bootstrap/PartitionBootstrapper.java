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
import io.camunda.eventbridge.broker.partitioning.PartitionLeaderReporter;
import io.camunda.eventbridge.broker.partitioning.PartitionLifecycle;
import io.camunda.eventbridge.broker.partitioning.RoundRobinPartitionDistributor;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.stream.MetadataPartition;
import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
import io.camunda.eventbridge.consumergroups.stream.CoordinatorPartition;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
  private final PartitionLeaderReporter leaderReporter;

  // Set by stop() so in-flight metadata passive-join retries bail instead of scheduling more work.
  private volatile boolean closing;

  // CopyOnWrite: boot mutates these on the start thread, runtime topic provisioning on the
  // reconciler thread, and stop() iterates them — so all access must be thread-safe.
  private final List<CreatedPartition> createdPartitions = new CopyOnWriteArrayList<>();
  private final List<PartitionLifecycle> lifecycles = new CopyOnWriteArrayList<>();
  private final List<LogRetentionCompactor> retentionCompactors = new CopyOnWriteArrayList<>();
  private final List<CoordinatorPartition> coordinatorPartitions = new CopyOnWriteArrayList<>();
  private final List<MetadataPartition> metadataPartitions = new CopyOnWriteArrayList<>();

  // Per-(group, partition) lookup for runtime join/leave and the reconciler's "already running?"
  // check. The lists above remain the stop() inventory; this map is the addressable index.
  private final java.util.Map<String, Provisioned> dataPartitions =
      new java.util.concurrent.ConcurrentHashMap<>();

  private record Provisioned(
      CreatedPartition created, PartitionLifecycle lifecycle, LogRetentionCompactor compactor) {}

  private static String key(final String groupName, final int partitionId) {
    return groupName + "#" + partitionId;
  }

  /**
   * Numeric node id from a {@code broker-<n>} member id (the leader node id in leadership reports).
   */
  private static int parseNodeId(final String memberId) {
    try {
      return Integer.parseInt(memberId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return -1;
    }
  }

  // Initialized in start(); reused by runtime topic-group provisioning after boot.
  private PartitionFactory factory;
  private DefaultPartitionManagementService managementService;
  private MessagingService brokerMessagingService;
  private MemberId localMemberId;
  private TopicRegistry topicRegistry;
  private Consumer<Map<String, TopicMetadata>> registryReconciler;
  private io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor
      reconfigurationExecutor;

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
      final io.camunda.eventbridge.clustermetadata.reconfig.ReconfigurationExecutor
          reconfigurationExecutor) {

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
            topicRegistry,
            created.raftPartition(),
            actorScheduler,
            brokerMessagingService,
            clock,
            runtimeDirectory,
            (ConstructableSnapshotStore) created.snapshotStore(),
            coordinatorTopologyManager);
    coordinatorPartitions.add(coordinatorPartition);
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
    final var members =
        IntStream.range(0, clusterSize)
            .mapToObj(i -> MemberId.from("broker-" + i))
            .sorted()
            .toList();
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
    createdPartitions.add(created);

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
    metadataPartitions.add(metadataPartition);
    actorScheduler.submitActor(metadataPartition);

    // Voting members bootstrap the group; everyone else joins as a non-voting passive observer.
    // In both cases register the role-change listener only AFTER the Raft partition is started
    // (replays the current role) — see the note in bootstrapCoordinatorPartition: registering
    // before would pin the StreamProcessor's committed reader to a segment that startup resets.
    if (observe) {
      joinMetadataAsPassiveWithRetry(partitionId, created, metadataPartition, managementService, 0);
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

  /**
   * Joins the metadata Raft group as a passive observer, <b>retrying until it succeeds</b>. A
   * passive join commits a configuration change, which needs the group to already have a leader; at
   * startup it races the voting members' election (every member answers {@code NO_LEADER} until one
   * wins) and Atomix's {@code joinWithRetry} gives up after a single pass over the members ("Sent
   * join request to all known members, but all failed"). We recover the same way the broker's other
   * startup interactions with the metadata leader do (see {@code BrokerRegistrar}'s
   * register-until-leader loop, and Zeebe's {@code ClusterConfigurationManager} operation retry):
   * keep retrying until the leader is reachable.
   *
   * <p>Each retry first {@link io.atomix.raft.partition.RaftPartition#close() closes} the
   * half-started server so its Raft message subjects are unregistered, letting the next attempt's
   * fresh server re-register them; only the Raft server is rebuilt, while the {@link
   * MetadataPartition} actor and its state DB (created once above) are reused. The role-change
   * listener is wired only on the attempt that succeeds.
   */
  private void joinMetadataAsPassiveWithRetry(
      final int partitionId,
      final CreatedPartition created,
      final MetadataPartition metadataPartition,
      final DefaultPartitionManagementService managementService,
      final int attempt) {
    if (closing) {
      return;
    }
    created
        .raftPartition()
        .joinAsPassive(managementService, created.snapshotStore())
        .whenComplete(
            (rp, error) -> {
              if (error == null) {
                LOG.info("Metadata raft partition {} observed (passive)", partitionId);
                created
                    .raftPartition()
                    .addRoleChangeListener(
                        (role, term) -> metadataPartition.onRoleChange(role, term));
                return;
              }
              if (closing) {
                return;
              }
              final var delay = passiveJoinRetryDelay(attempt);
              LOG.warn(
                  "Failed to observe (passive join) metadata raft partition {} (attempt {}); the "
                      + "group may have no leader yet — retrying in {}",
                  partitionId,
                  attempt + 1,
                  delay,
                  error);
              // Unregister the half-started server's Raft subjects before the next attempt
              // re-creates
              // them; otherwise the rebuilt server collides with the previous registration.
              created
                  .raftPartition()
                  .close()
                  .whenComplete(
                      (ignored, closeError) -> {
                        if (closeError != null) {
                          LOG.warn(
                              "Error closing metadata raft partition {} before passive-join retry",
                              partitionId,
                              closeError);
                        }
                        schedulePassiveJoinRetry(
                            partitionId,
                            created,
                            metadataPartition,
                            managementService,
                            attempt + 1,
                            delay);
                      });
            });
  }

  private void schedulePassiveJoinRetry(
      final int partitionId,
      final CreatedPartition created,
      final MetadataPartition metadataPartition,
      final DefaultPartitionManagementService managementService,
      final int attempt,
      final Duration delay) {
    if (closing || executorService.isShutdown()) {
      return;
    }
    try {
      CompletableFuture.runAsync(
          () ->
              joinMetadataAsPassiveWithRetry(
                  partitionId, created, metadataPartition, managementService, attempt),
          CompletableFuture.delayedExecutor(
              delay.toMillis(), TimeUnit.MILLISECONDS, executorService));
    } catch (final RejectedExecutionException e) {
      LOG.debug("Not scheduling metadata passive-join retry — executor is shutting down");
    }
  }

  /** Capped exponential backoff for passive-join retries: 1s, 2s, 4s, then 8s thereafter. */
  private static Duration passiveJoinRetryDelay(final int attempt) {
    return Duration.ofSeconds(1L << Math.min(attempt, 3));
  }

  void stop() {
    closing = true;
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

    for (final var metadataPartition : metadataPartitions) {
      try {
        metadataPartition.closeAsync();
      } catch (final Exception e) {
        LOG.warn("Error closing metadata partition", e);
      }
    }
    metadataPartitions.clear();

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
    startDataPartition(groupName, partitionId, members, topologyManager, false);
  }

  /**
   * Joins an already-running Raft group as a new replica at runtime (reassignment add). Same setup
   * as {@link #provisionDataPartition} but the Raft server joins the existing cluster (catching up
   * via the leader) instead of bootstrapping a new one.
   */
  java.util.concurrent.CompletableFuture<Void> joinDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager) {
    return startDataPartition(groupName, partitionId, members, topologyManager, true);
  }

  /** Whether this broker currently runs a replica of {@code (groupName, partitionId)}. */
  boolean isRunning(final String groupName, final int partitionId) {
    return dataPartitions.containsKey(key(groupName, partitionId));
  }

  /**
   * Whether this broker has on-disk data for {@code (groupName, partitionId)} (restart recovery).
   */
  boolean hasData(final String groupName, final int partitionId) {
    final var dir = factory.getPartitionDirectory(groupName, partitionId);
    try (final var entries = java.nio.file.Files.list(dir)) {
      return entries.findAny().isPresent();
    } catch (final java.io.IOException e) {
      return false;
    }
  }

  /** Leaves and tears down a runtime replica (reassignment remove). Idempotent. */
  java.util.concurrent.CompletableFuture<Void> leaveDataPartition(
      final String groupName, final int partitionId) {
    final var provisioned = dataPartitions.remove(key(groupName, partitionId));
    if (provisioned == null) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
    lifecycles.remove(provisioned.lifecycle());
    createdPartitions.remove(provisioned.created());
    try {
      provisioned.lifecycle().closeAsync();
    } catch (final Exception e) {
      LOG.warn("Error closing lifecycle for {}/{}", groupName, partitionId, e);
    }
    if (provisioned.compactor() != null) {
      retentionCompactors.remove(provisioned.compactor());
      provisioned.compactor().closeAsync();
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

  private java.util.concurrent.CompletableFuture<Void> startDataPartition(
      final String groupName,
      final int partitionId,
      final Set<MemberId> members,
      final TopologyManagerImpl topologyManager,
      final boolean join) {

    if (isRunning(groupName, partitionId)) {
      return java.util.concurrent.CompletableFuture.completedFuture(null); // idempotent
    }

    // 1. Create raft-level components
    final var created = factory.createData(groupName, partitionId, members, localMemberId);
    createdPartitions.add(created);

    // The gateway routing group: data partitions use the BrokerClient's default group, topic groups
    // route under their own Raft group name (so handler subjects don't collide across groups).
    final var routingGroup =
        PartitionFactory.GROUP_NAME.equals(groupName)
            ? io.camunda.zeebe.protocol.Protocol.DEFAULT_PARTITION_GROUP_NAME
            : groupName;

    // 2. Create lifecycle actor. For a topic-registry group, on becoming leader it reports its
    // leadership to the metadata group (so topic readiness is derived); the default data group does
    // not (topic == null → no-op reporter).
    final var topic = io.camunda.eventbridge.core.topic.TopicGroups.topicFrom(groupName);
    final var lifecycle =
        new PartitionLifecycle(
            partitionId,
            properties.broker().partitionCount(),
            routingGroup,
            topic,
            parseNodeId(localMemberId.id()),
            leaderReporter,
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
    LogRetentionCompactor retentionCompactor = null;
    if (properties.retention().maxRecordsPerPartition() > 0) {
      retentionCompactor =
          new LogRetentionCompactor(
              partitionId,
              created.raftPartition(),
              (ConstructableSnapshotStore) created.snapshotStore(),
              properties.retention().maxRecordsPerPartition(),
              Duration.ofMillis(properties.retention().compactionIntervalMs()));
      retentionCompactors.add(retentionCompactor);
      actorScheduler.submitActor(retentionCompactor);
    }

    dataPartitions.put(
        key(groupName, partitionId), new Provisioned(created, lifecycle, retentionCompactor));

    // 3. Wire raft role changes to lifecycle — before start so no events are lost. The term is the
    // leader-epoch the lifecycle reports to the metadata group on becoming leader.
    created.raftPartition().addRoleChangeListener(lifecycle::onRoleChange);

    // 4. Start raft — bootstrap a new group, or join an existing one for a reassignment add.
    final var started =
        join
            ? created.raftPartition().join(managementService, created.snapshotStore())
            : created.raftPartition().bootstrap(managementService, created.snapshotStore());
    final var lifecycleRef = lifecycle;
    final var compactorRef = retentionCompactor;
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
                // Roll back tracking so a retry genuinely re-attempts and committed state is not
                // advanced on a failed join (it would otherwise look "running" and no-op the
                // retry).
                dataPartitions.remove(key(groupName, partitionId));
                createdPartitions.remove(created);
                lifecycles.remove(lifecycleRef);
                lifecycleRef.closeAsync();
                if (compactorRef != null) {
                  retentionCompactors.remove(compactorRef);
                  compactorRef.closeAsync();
                }
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
