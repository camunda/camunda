/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.eventbridge.coordinator.MetadataManager;
import io.camunda.eventbridge.coordinator.reconfig.ReconfigurationExecutor;
import io.camunda.eventbridge.coordinator.transport.MetadataRequestHandler;
import io.camunda.eventbridge.transport.RequestHandlerRegistry;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.broker.logstreams.state.DbPositionSupplier;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle actor for the metadata Raft partition (the single shard of the {@code
 * event-bridge-metadata} group that holds the topic registry). It runs a {@link MetadataStream}
 * ({@code StreamProcessor} over the partition log + {@code ZeebeDb} state):
 *
 * <ul>
 *   <li><b>Leader</b>: {@code PROCESSING} mode + {@code MetadataManager} + request handler +
 *       snapshot director; serves topic admin (create/delete/reassign/list), runs the {@code
 *       CREATING -> ACTIVE} transition and the change-coordinator.
 *   <li><b>Follower / passive observer</b>: {@code REPLAY} mode (read-only log, no-op appender) —
 *       continuously applies committed topic events to its own state, so on promotion it only
 *       replays the tail and takes over quickly.
 * </ul>
 *
 * <p>On <em>every</em> role this partition periodically feeds its local replicated registry to a
 * reconcile sink, so each broker provisions its assigned topic Raft groups from its own observed
 * copy of the registry rather than from a leader-side network broadcast.
 *
 * <p>Mirrors {@link CoordinatorPartition}; the two differ only in which stream/manager they run
 * (registry here, consumer offsets + group coordination there).
 */
public final class MetadataPartition extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataPartition.class);
  private static final Duration SNAPSHOT_PERIOD = Duration.ofSeconds(30);
  // Per-broker reconcile cadence: re-asserting the local replicated registry is the anti-entropy
  // that lets a broker that missed an update still converge (replaces the old 2s leader broadcast).
  private static final Duration RECONCILE_INTERVAL = Duration.ofSeconds(1);
  private static final ZeebeLogAppender NOOP_APPENDER = (entry, listener) -> {};

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final ActorSchedulingService actorScheduler;
  private final InstantSource clock;
  private final Path runtimeDirectory;
  private final ConstructableSnapshotStore snapshotStore;
  private final ZeebeDbFactory<EventBridgeColumnFamilies> dbFactory;
  private final RequestHandlerRegistry requestHandlerRegistry;
  private final TopologyManagerImpl metadataTopologyManager;
  private final Consumer<Map<String, TopicMetadata>> registryReconciler;
  private final AtomicReference<BiConsumer<String, List<Integer>>> provisionedSinkRef;
  private final ReconfigurationExecutor reconfigurationExecutor;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private Role currentRole;
  private long currentTerm;

  // Created once and reused across role changes (so follower replay progress survives promotion).
  private StateController stateController;
  private ZeebeDb<EventBridgeColumnFamilies> zeebeDb;

  // Rebuilt on each role change.
  private AtomixLogStorage logStorage;
  private MetadataStream metadataStream;
  private AsyncSnapshotDirector snapshotDirector;
  private MetadataManager metadataManager;

  public MetadataPartition(
      final int partitionId,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final Path runtimeDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final TopologyManagerImpl metadataTopologyManager,
      final Consumer<Map<String, TopicMetadata>> registryReconciler,
      final AtomicReference<BiConsumer<String, List<Integer>>> provisionedSinkRef,
      final ReconfigurationExecutor reconfigurationExecutor) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.actorScheduler = actorScheduler;
    this.clock = clock;
    this.runtimeDirectory = runtimeDirectory;
    this.snapshotStore = snapshotStore;
    this.metadataTopologyManager = metadataTopologyManager;
    this.registryReconciler = registryReconciler;
    this.provisionedSinkRef = provisionedSinkRef;
    this.reconfigurationExecutor = reconfigurationExecutor;
    requestHandlerRegistry = new RequestHandlerRegistry(partitionId, messagingService);
    dbFactory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, partitionId),
            () -> meterRegistry);
  }

  @Override
  public String getName() {
    return "EventBridgeMetadataPartition-" + partitionId;
  }

  @Override
  protected void onActorStarted() {
    scheduleReconcile();
  }

  @Override
  protected void onActorClosing() {
    tearDownStream();
    closeState();
    requestHandlerRegistry.close();
  }

  /**
   * Periodically hands this broker's local replicated registry to the reconcile sink, on every role
   * (leader, follower, passive observer). {@code topicsSnapshot()} reads the stream's thread-safe
   * in-memory cache, so this is safe to call from the partition actor; the sink dispatches the
   * actual provisioning off-actor. No-op until the stream has started.
   */
  private void scheduleReconcile() {
    if (registryReconciler != null && metadataStream != null) {
      try {
        registryReconciler.accept(metadataStream.topicsSnapshot());
      } catch (final Exception e) {
        LOG.warn("Metadata partition {} — registry reconcile failed", partitionId, e);
      }
    }
    actor.schedule(RECONCILE_INTERVAL, this::scheduleReconcile);
  }

  /** Wired from the Raft partition's role-change listener. */
  public void onRoleChange(final Role role, final long term) {
    actor.submit(() -> handleRoleChange(role, term));
  }

  private void handleRoleChange(final Role role, final long term) {
    if (role == currentRole) {
      return;
    }
    LOG.info("Metadata partition {} — role change {} -> {}", partitionId, currentRole, role);
    tearDownStream();
    currentRole = role;
    currentTerm = term;

    switch (role) {
      case LEADER -> start(role, StreamProcessorMode.PROCESSING, true);
      case FOLLOWER, CANDIDATE, PROMOTABLE -> start(role, StreamProcessorMode.REPLAY, false);
      default -> {
        // INACTIVE — stay torn down
      }
    }
  }

  private void start(final Role forRole, final StreamProcessorMode mode, final boolean leader) {
    ensureStateRecovered(
        forRole,
        () -> {
          final var server = raftPartition.getServer();
          // Leaders write (real appender); followers only replay (no-op appender is never invoked).
          final var appender = leader ? server.getAppender().orElse(NOOP_APPENDER) : NOOP_APPENDER;
          logStorage = AtomixLogStorage.ofPartition(server::openReader, appender);
          server.addCommitListener(logStorage);

          metadataStream =
              new MetadataStream(
                  partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
          // A leader must only start topic management once the log has been replayed into the state
          // DB (otherwise it reads an empty registry and loses topics on failover). The stream
          // invokes this once replay completes; followers never trigger it.
          final Runnable onRecovered =
              leader ? () -> actor.run(() -> onLeaderRecovered(forRole)) : null;
          metadataStream
              .start(mode, onRecovered)
              .onComplete(
                  (ok, error) -> {
                    if (forRole != currentRole) {
                      return; // role changed again while starting
                    }
                    if (error != null) {
                      LOG.error("Metadata partition {} — stream start failed", partitionId, error);
                      return;
                    }
                    startSnapshotDirector(forRole, mode, leader, server);
                    if (!leader) {
                      metadataTopologyManager.onBecomingFollower(partitionId, currentTerm);
                      LOG.info("Metadata partition {} — follower replaying", partitionId);
                    }
                  });
        });
  }

  /** Recovers the persistent state DB once; reuses it on subsequent role changes. */
  private void ensureStateRecovered(final Role forRole, final Runnable onReady) {
    if (zeebeDb != null) {
      onReady.run();
      return;
    }
    if (stateController == null) {
      stateController =
          new StateControllerImpl(
              dbFactory,
              snapshotStore,
              runtimeDirectory,
              new AtomixRecordEntrySupplierImpl(raftPartition.getServer()),
              db -> new DbPositionSupplier(db, false),
              actor);
    }
    stateController
        .recover()
        .onComplete(
            (recoveredDb, error) -> {
              if (forRole != currentRole) {
                return;
              }
              if (error != null) {
                LOG.error("Metadata partition {} — state recovery failed", partitionId, error);
                return;
              }
              zeebeDb = castDb(recoveredDb);
              onReady.run();
            });
  }

  /**
   * Invoked on this actor once the stream processor has finished replaying the log into the state
   * DB — the point at which the topic registry is fully restored. Wired as the metadata stream's
   * {@code onRecovered} lifecycle callback, so it only fires for a leader and exactly once per role
   * activation.
   */
  private void onLeaderRecovered(final Role forRole) {
    if (forRole != currentRole) {
      return; // role changed again while replaying
    }
    startMetadataManager();
    metadataTopologyManager.onBecomingLeader(partitionId, currentTerm, null, null);
    LOG.info("Metadata partition {} — leader ready", partitionId);
  }

  private void startMetadataManager() {
    metadataManager =
        new MetadataManager(
            partitionId,
            this::registeredBrokerIds,
            metadataStream,
            provisionedSinkRef,
            reconfigurationExecutor);
    actorScheduler.submitActor(metadataManager);
    requestHandlerRegistry.register(
        MetadataRequestHandler.topicName(partitionId),
        new MetadataRequestHandler(partitionId, metadataManager));
  }

  private void startSnapshotDirector(
      final Role forRole,
      final StreamProcessorMode mode,
      final boolean leader,
      final RaftPartitionServer server) {
    snapshotDirector =
        AsyncSnapshotDirector.of(
            partitionId,
            metadataStream.streamProcessor(),
            stateController,
            mode,
            SNAPSHOT_PERIOD,
            server::flushLog,
            new DbPositionSupplier(zeebeDb, false));

    actorScheduler
        .submitActor(snapshotDirector, SchedulingHints.cpuBound())
        .onComplete(
            (ok, error) -> {
              if (forRole != currentRole || error != null) {
                return;
              }
              if (leader) {
                server.addCommittedEntryListener(snapshotDirector);
                try (final var reader = metadataStream.logStream().newLogStreamReader()) {
                  snapshotDirector.onCommit(reader.seekToEnd());
                }
              }
            });
  }

  @SuppressWarnings("unchecked")
  private static ZeebeDb<EventBridgeColumnFamilies> castDb(final ZeebeDb<?> db) {
    return (ZeebeDb<EventBridgeColumnFamilies>) db;
  }

  /**
   * The broker node ids registered in the metadata Raft group — its live membership (voting members
   * + passive observers). This is the placeable broker set the leader's {@link MetadataManager}
   * uses instead of a static configured cluster size, so a broker is only a placement target once
   * it has joined (registered with) the group.
   */
  private List<Integer> registeredBrokerIds() {
    return raftPartition.members().stream()
        .map(member -> parseNodeId(member.id()))
        .filter(id -> id >= 0)
        .distinct()
        .sorted()
        .toList();
  }

  private static int parseNodeId(final String memberId) {
    try {
      return Integer.parseInt(memberId.replaceAll("[^0-9]", ""));
    } catch (final NumberFormatException e) {
      return -1;
    }
  }

  /** Tears down the per-role stream, leaving the state DB open for reuse on the next role. */
  private void tearDownStream() {
    final var server = raftPartition.getServer();

    if (metadataManager != null) {
      requestHandlerRegistry.unregister(MetadataRequestHandler.topicName(partitionId));
      metadataManager.closeAsync();
      metadataManager = null;
    }
    if (snapshotDirector != null) {
      if (server != null) {
        server.removeCommittedEntryListener(snapshotDirector);
      }
      snapshotDirector.closeAsync();
      snapshotDirector = null;
    }
    if (metadataStream != null) {
      metadataStream.stop();
      metadataStream = null;
    }
    if (logStorage != null) {
      if (server != null) {
        server.removeCommitListener(logStorage);
      }
      logStorage = null;
    }
  }

  private void closeState() {
    if (stateController != null) {
      stateController.closeDb();
      stateController = null;
      zeebeDb = null;
    }
  }
}
