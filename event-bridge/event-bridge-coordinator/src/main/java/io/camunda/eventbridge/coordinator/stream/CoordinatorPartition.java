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
import io.camunda.eventbridge.coordinator.CoordinationManager;
import io.camunda.eventbridge.coordinator.transport.CoordinationRequestHandler;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle actor for a coordinator Raft partition (one shard of the coordinator group). It runs a
 * {@link CoordinatorStream} ({@code StreamProcessor} over the partition log + {@code ZeebeDb}
 * state):
 *
 * <ul>
 *   <li><b>Leader</b>: {@code PROCESSING} mode + {@code CoordinationManager} + request handler +
 *       snapshot director; serves join/heartbeat/leave/commit and replicates offsets + group
 *       metadata.
 *   <li><b>Follower</b>: {@code REPLAY} mode (read-only log, no-op appender) — continuously applies
 *       committed offset/metadata events to its own state, so on promotion it only replays the tail
 *       and takes over quickly.
 * </ul>
 *
 * The {@link StateController}/{@code ZeebeDb} is created once and persists across role changes, so
 * a follower's replayed state is reused on promotion rather than recovered from an old snapshot.
 */
public final class CoordinatorPartition extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(CoordinatorPartition.class);
  private static final Duration SNAPSHOT_PERIOD = Duration.ofSeconds(30);
  private static final ZeebeLogAppender NOOP_APPENDER = (entry, listener) -> {};

  private final int partitionId;
  private final int partitionCount;
  private final int clusterSize;
  private final RaftPartition raftPartition;
  private final ActorSchedulingService actorScheduler;
  private final InstantSource clock;
  private final Path runtimeDirectory;
  private final ConstructableSnapshotStore snapshotStore;
  private final ZeebeDbFactory<EventBridgeColumnFamilies> dbFactory;
  private final RequestHandlerRegistry requestHandlerRegistry;
  private final TopologyManagerImpl coordinatorTopologyManager;
  private final TopicAssignmentGossip.Publisher topicAssignmentPublisher;
  private final java.util.concurrent.atomic.AtomicReference<
          java.util.function.BiConsumer<String, java.util.List<Integer>>>
      provisionedSinkRef;
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private Role currentRole;
  private long currentTerm;

  // Created once and reused across role changes (so follower replay progress survives promotion).
  private StateController stateController;
  private ZeebeDb<EventBridgeColumnFamilies> zeebeDb;

  // Rebuilt on each role change.
  private AtomixLogStorage logStorage;
  private CoordinatorStream coordinatorStream;
  private AsyncSnapshotDirector snapshotDirector;
  private CoordinationManager coordinationManager;

  public CoordinatorPartition(
      final int partitionId,
      final int partitionCount,
      final int clusterSize,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final Path runtimeDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final TopologyManagerImpl coordinatorTopologyManager,
      final TopicAssignmentGossip.Publisher topicAssignmentPublisher,
      final java.util.concurrent.atomic.AtomicReference<
              java.util.function.BiConsumer<String, java.util.List<Integer>>>
          provisionedSinkRef) {
    this.partitionId = partitionId;
    this.partitionCount = partitionCount;
    this.clusterSize = clusterSize;
    this.raftPartition = raftPartition;
    this.actorScheduler = actorScheduler;
    this.clock = clock;
    this.runtimeDirectory = runtimeDirectory;
    this.snapshotStore = snapshotStore;
    this.coordinatorTopologyManager = coordinatorTopologyManager;
    this.topicAssignmentPublisher = topicAssignmentPublisher;
    this.provisionedSinkRef = provisionedSinkRef;
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
    return "EventBridgeCoordinatorPartition-" + partitionId;
  }

  @Override
  protected void onActorClosing() {
    tearDownStream();
    closeState();
    requestHandlerRegistry.close();
  }

  /** Wired from the Raft partition's role-change listener. */
  public void onRoleChange(final Role role, final long term) {
    actor.submit(() -> handleRoleChange(role, term));
  }

  private void handleRoleChange(final Role role, final long term) {
    if (role == currentRole) {
      return;
    }
    LOG.info("Coordinator partition {} — role change {} -> {}", partitionId, currentRole, role);
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

          coordinatorStream =
              new CoordinatorStream(
                  partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
          // A leader must only start coordination once the log has been replayed into the state DB
          // (otherwise restoreGroups() reads empty state and loses the membership on failover). The
          // stream invokes this once replay completes; followers never trigger it.
          final Runnable onRecovered =
              leader ? () -> actor.run(() -> onLeaderRecovered(forRole)) : null;
          coordinatorStream
              .start(mode, onRecovered)
              .onComplete(
                  (ok, error) -> {
                    if (forRole != currentRole) {
                      return; // role changed again while starting
                    }
                    if (error != null) {
                      LOG.error(
                          "Coordinator partition {} — stream start failed", partitionId, error);
                      return;
                    }
                    startSnapshotDirector(forRole, mode, leader, server);
                    if (!leader) {
                      coordinatorTopologyManager.onBecomingFollower(partitionId, currentTerm);
                      LOG.info("Coordinator partition {} — follower replaying", partitionId);
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
                LOG.error("Coordinator partition {} — state recovery failed", partitionId, error);
                return;
              }
              zeebeDb = castDb(recoveredDb);
              onReady.run();
            });
  }

  /**
   * Invoked on this actor once the stream processor has finished replaying the log into the state
   * DB — the point at which {@code restoreGroups()} can see the replicated consumer-group metadata.
   * Wired as the coordinator stream's {@code onRecovered} lifecycle callback, so it only fires for
   * a leader and exactly once per role activation.
   */
  private void onLeaderRecovered(final Role forRole) {
    if (forRole != currentRole) {
      return; // role changed again while replaying
    }
    startCoordination();
    coordinatorTopologyManager.onBecomingLeader(partitionId, currentTerm, null, null);
    LOG.info("Coordinator partition {} — leader ready", partitionId);
  }

  private void startCoordination() {
    coordinationManager =
        new CoordinationManager(
            partitionId,
            partitionCount,
            clusterSize,
            clock,
            coordinatorStream,
            topicAssignmentPublisher,
            provisionedSinkRef);
    actorScheduler.submitActor(coordinationManager);
    requestHandlerRegistry.register(
        CoordinationRequestHandler.topicName(partitionId),
        new CoordinationRequestHandler(partitionId, coordinationManager));
  }

  private void startSnapshotDirector(
      final Role forRole,
      final StreamProcessorMode mode,
      final boolean leader,
      final RaftPartitionServer server) {
    snapshotDirector =
        AsyncSnapshotDirector.of(
            partitionId,
            coordinatorStream.streamProcessor(),
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
                try (final var reader = coordinatorStream.logStream().newLogStreamReader()) {
                  snapshotDirector.onCommit(reader.seekToEnd());
                }
              }
            });
  }

  @SuppressWarnings("unchecked")
  private static ZeebeDb<EventBridgeColumnFamilies> castDb(final ZeebeDb<?> db) {
    return (ZeebeDb<EventBridgeColumnFamilies>) db;
  }

  /** Tears down the per-role stream, leaving the state DB open for reuse on the next role. */
  private void tearDownStream() {
    final var server = raftPartition.getServer();

    if (coordinationManager != null) {
      requestHandlerRegistry.unregister(CoordinationRequestHandler.topicName(partitionId));
      coordinationManager.closeAsync();
      coordinationManager = null;
    }
    if (snapshotDirector != null) {
      if (server != null) {
        server.removeCommittedEntryListener(snapshotDirector);
      }
      snapshotDirector.closeAsync();
      snapshotDirector = null;
    }
    if (coordinatorStream != null) {
      coordinatorStream.stop();
      coordinatorStream = null;
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
