/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.zeebe.ZeebeLogAppender;
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
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle actor for an event-bridge Raft partition: it drives a {@link ReplicatedStream} (a
 * {@code StreamProcessor} over the partition log plus {@code ZeebeDb} state) through Raft role
 * changes.
 *
 * <ul>
 *   <li><b>Leader</b>: {@code PROCESSING} mode + the partition's manager + request handler +
 *       snapshot director.
 *   <li><b>Follower / passive observer</b>: {@code REPLAY} mode (read-only log, no-op appender) —
 *       continuously applies committed events to its own state, so on promotion it only replays the
 *       tail and takes over quickly.
 * </ul>
 *
 * <p>The {@link StateController}/{@code ZeebeDb} is created once and persists across role changes,
 * so a follower's replayed state is reused on promotion rather than recovered from an old snapshot.
 * The leader starts its manager only once the log has been replayed into the state DB (via the
 * stream's {@code onRecovered} callback), so it never reads empty state on failover.
 *
 * <p>Subclasses supply the partition-specific stream ({@link #createStream}) and manager lifecycle
 * ({@link #onLeaderReady()} / {@link #onManagerTeardown()}).
 *
 * @param <C> the column-family enum of the backing {@link ZeebeDb}
 * @param <S> the concrete replicated stream this partition runs
 */
public abstract class RaftPartitionLifecycle<
        C extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
        S extends ReplicatedStream<C>>
    extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(RaftPartitionLifecycle.class);
  private static final Duration SNAPSHOT_PERIOD = Duration.ofSeconds(30);
  private static final ZeebeLogAppender NOOP_APPENDER = (entry, listener) -> {};

  protected final int partitionId;
  protected final RaftPartition raftPartition;
  protected final ActorSchedulingService actorScheduler;
  protected final InstantSource clock;
  protected final RequestHandlerRegistry requestHandlerRegistry;
  protected final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final Path runtimeDirectory;
  private final ConstructableSnapshotStore snapshotStore;
  private final TopologyManagerImpl topologyManager;
  private final ZeebeDbFactory<C> dbFactory;

  protected Role currentRole;
  protected long currentTerm;

  // Created once and reused across role changes (so follower replay progress survives promotion).
  private StateController stateController;
  protected ZeebeDb<C> zeebeDb;

  // Rebuilt on each role change.
  private AtomixLogStorage logStorage;
  protected S stream;
  private AsyncSnapshotDirector snapshotDirector;

  protected RaftPartitionLifecycle(
      final int partitionId,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final Path runtimeDirectory,
      final ConstructableSnapshotStore snapshotStore,
      final TopologyManagerImpl topologyManager) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.actorScheduler = actorScheduler;
    this.clock = clock;
    this.runtimeDirectory = runtimeDirectory;
    this.snapshotStore = snapshotStore;
    this.topologyManager = topologyManager;
    requestHandlerRegistry = new RequestHandlerRegistry(partitionId, messagingService);
    dbFactory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, partitionId),
            () -> meterRegistry);
  }

  /** A short label for this partition kind, e.g. {@code "Coordinator"} or {@code "Metadata"}. */
  protected abstract String label();

  /** Creates the partition-specific replicated stream for the recovered state DB. */
  protected abstract S createStream(
      int partitionId,
      AtomixLogStorage logStorage,
      ActorSchedulingService actorScheduler,
      ZeebeDb<C> zeebeDb,
      InstantSource clock,
      MeterRegistry meterRegistry);

  /**
   * Invoked on this actor once a leader has fully replayed the log into the state DB: start the
   * partition's manager and register its request handler. The replicated stream is available via
   * {@link #stream}.
   */
  protected abstract void onLeaderReady();

  /**
   * Tears down whatever {@link #onLeaderReady()} started; called on every role change. Null-safe.
   */
  protected abstract void onManagerTeardown();

  @Override
  public String getName() {
    return "EventBridge" + label() + "Partition-" + partitionId;
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
    LOG.info("{} partition {} — role change {} -> {}", label(), partitionId, currentRole, role);
    tearDownStream();
    currentRole = role;
    currentTerm = term;

    switch (role) {
      case LEADER -> start(role, StreamProcessorMode.PROCESSING, true);
      // PASSIVE is a non-voting observer that still receives committed entries from the leader, so
      // it
      // must replay too: a passive metadata observer (any broker beyond the metadata replication
      // factor) only provisions its assigned topic Raft groups once it has replayed the registry
      // into
      // its state DB. Without this it never reconciles, leaving "ghost" replicas no node hosts.
      case FOLLOWER, CANDIDATE, PROMOTABLE, PASSIVE ->
          start(role, StreamProcessorMode.REPLAY, false);
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

          stream =
              createStream(partitionId, logStorage, actorScheduler, zeebeDb, clock, meterRegistry);
          // A leader must only start its manager once the log has been replayed into the state DB
          // (otherwise it reads empty state and loses data on failover). The stream invokes this
          // once replay completes; followers never trigger it.
          final Runnable onRecovered =
              leader ? () -> actor.run(() -> onLeaderRecovered(forRole)) : null;
          stream
              .start(mode, onRecovered)
              .onComplete(
                  (ok, error) -> {
                    if (forRole != currentRole) {
                      return; // role changed again while starting
                    }
                    if (error != null) {
                      LOG.error(
                          "{} partition {} — stream start failed", label(), partitionId, error);
                      return;
                    }
                    startSnapshotDirector(forRole, mode, leader, server);
                    if (!leader) {
                      topologyManager.onBecomingFollower(partitionId, currentTerm);
                      LOG.info("{} partition {} — follower replaying", label(), partitionId);
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
                LOG.error("{} partition {} — state recovery failed", label(), partitionId, error);
                return;
              }
              zeebeDb = castDb(recoveredDb);
              onReady.run();
            });
  }

  /**
   * Invoked on this actor once a leader's stream has finished replaying the log into the state DB.
   * Wired as the stream's {@code onRecovered} lifecycle callback, so it only fires for a leader and
   * exactly once per role activation.
   */
  private void onLeaderRecovered(final Role forRole) {
    if (forRole != currentRole) {
      return; // role changed again while replaying
    }
    onLeaderReady();
    topologyManager.onBecomingLeader(partitionId, currentTerm, null, null);
    LOG.info("{} partition {} — leader ready", label(), partitionId);
  }

  private void startSnapshotDirector(
      final Role forRole,
      final StreamProcessorMode mode,
      final boolean leader,
      final RaftPartitionServer server) {
    snapshotDirector =
        AsyncSnapshotDirector.of(
            partitionId,
            stream.streamProcessor(),
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
                try (final var reader = stream.logStream().newLogStreamReader()) {
                  snapshotDirector.onCommit(reader.seekToEnd());
                }
              }
            });
  }

  @SuppressWarnings("unchecked")
  private static <C extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDb<C> castDb(final ZeebeDb<?> db) {
    return (ZeebeDb<C>) db;
  }

  /** Tears down the per-role stream, leaving the state DB open for reuse on the next role. */
  private void tearDownStream() {
    final var server = raftPartition.getServer();

    onManagerTeardown();
    if (snapshotDirector != null) {
      if (server != null) {
        server.removeCommittedEntryListener(snapshotDirector);
      }
      snapshotDirector.closeAsync();
      snapshotDirector = null;
    }
    if (stream != null) {
      stream.stop();
      stream = null;
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
