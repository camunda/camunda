/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partition;

import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.broker.actor.PollActor;
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.broker.logstream.EventBridgeLogStorage;
import io.camunda.eventbridge.broker.topology.TopologyBroadcaster;
import io.camunda.zeebe.logstreams.log.LogStream;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the full lifecycle of a single Event Bridge partition.
 *
 * <p>Wraps a {@link RaftPartition} and listens for role-change events:
 *
 * <ul>
 *   <li><strong>LEADER</strong>: creates an {@link EventBridgeLogStorage} backed by the RAFT
 *       appender, builds a {@link LogStream} on top of it, calls {@link PublishActor#connect} with
 *       the live writer and reader so that publish requests can proceed, calls {@link
 *       PollActor#connect(LogStream)} so that poll/fetch requests can read from the log, and
 *       notifies the {@link TopologyBroadcaster} so that the SWIM properties are updated for
 *       gateway routing.
 *   <li><strong>FOLLOWER / INACTIVE / CANDIDATE</strong>: calls {@link PublishActor#disconnect} and
 *       {@link PollActor#disconnect()}, closes the {@link LogStream}, and notifies the {@link
 *       TopologyBroadcaster} to remove the partition-leader property from SWIM.
 * </ul>
 *
 * <p>The role-change callback is invoked on RAFT's internal thread. The {@link LogStream} and
 * {@link EventBridgeLogStorage} are created synchronously on that thread; the actor connections are
 * posted to each actor's own thread via {@link PublishActor#connect}/{@link
 * PublishActor#disconnect} and {@link PollActor#connect}/{@link PollActor#disconnect()}.
 */
public final class EventBridgePartition implements RaftRoleChangeListener {

  private static final Logger LOG = LoggerFactory.getLogger(EventBridgePartition.class);

  /** Default maximum fragment size fed to the LogStream sequencer (4 MiB). */
  private static final int DEFAULT_MAX_FRAGMENT_SIZE = 4 * 1024 * 1024;

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final PublishActor publishActor;
  private final PollActor pollActor;
  private final TopologyBroadcaster topologyBroadcaster;

  /** Non-null only while this node is leader. */
  private EventBridgeLogStorage logStorage;

  /** Non-null only while this node is leader. */
  private LogStream logStream;

  public EventBridgePartition(
      final int partitionId,
      final RaftPartition raftPartition,
      final PublishActor publishActor,
      final PollActor pollActor,
      final TopologyBroadcaster topologyBroadcaster) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.publishActor = publishActor;
    this.pollActor = pollActor;
    this.topologyBroadcaster = topologyBroadcaster;
    raftPartition.addRoleChangeListener(this);
  }

  // -------------------------------------------------------------------------
  // RaftRoleChangeListener

  @Override
  public void onNewRole(final Role newRole, final long newTerm) {
    LOG.info("Partition {} role change → {} (term {})", partitionId, newRole, newTerm);
    if (newRole == Role.LEADER) {
      transitionToLeader(newTerm);
    } else {
      // Remove from SWIM *before* disconnecting the actor so the gateway stops routing new
      // requests here immediately; in-flight requests finish before the actor is torn down.
      topologyBroadcaster.onLostLeadership(partitionId, newRole);
      transitionToNonLeader(newRole);
    }
  }

  // -------------------------------------------------------------------------
  // Lifecycle

  /**
   * Closes the underlying RAFT partition and releases all held resources.
   *
   * @return a future that completes when the partition is fully closed
   */
  public CompletableFuture<Void> close() {
    raftPartition.removeRoleChangeListener(this);
    closeLogStreamIfPresent();
    return raftPartition.close();
  }

  /** Returns the underlying {@link RaftPartition}. */
  public RaftPartition getRaftPartition() {
    return raftPartition;
  }

  // -------------------------------------------------------------------------
  // Private helpers

  private void transitionToLeader(final long newTerm) {
    // Always tear down any pre-existing LogStream before creating a new one, regardless of
    // whether the new server/appender are available. Doing this here prevents leaking the
    // old LogStream when the server or appender are unavailable on re-election.
    closeLogStreamIfPresent();

    final var server = raftPartition.getServer();
    if (server == null) {
      LOG.error(
          "Partition {} became LEADER but RaftPartitionServer is null; "
              + "publish requests will fail",
          partitionId);
      return;
    }

    final var appenderOpt = server.getAppender();
    if (appenderOpt.isEmpty()) {
      LOG.error(
          "Partition {} became LEADER but ZeebeLogAppender is unavailable; "
              + "publish requests will fail until the next role transition",
          partitionId);
      return;
    }

    // Build the storage bridge: RAFT committed log → LogStorage interface.
    logStorage = new EventBridgeLogStorage(server::openReader, appenderOpt.get());

    // Register as RaftCommitListener so RAFT commits propagate to LogStream awaiters.
    server.addCommitListener(logStorage);

    // Build the LogStream synchronously; it creates the internal Sequencer.
    logStream =
        LogStream.builder()
            .withLogStorage(logStorage)
            .withLogName("event-bridge-" + partitionId)
            .withPartitionId(partitionId)
            .withMaxFragmentSize(DEFAULT_MAX_FRAGMENT_SIZE)
            .build();

    // Wire writer and reader to the PublishActor (posted to its own actor thread).
    publishActor.connect(logStream.newLogStreamWriter(), logStream.newLogStreamReader(), server);

    // Wire the PollActor to the LogStream so it can read events and register for long-poll
    // notifications. PollActor opens its own dedicated reader.
    pollActor.connect(logStream);

    // Advertise leadership in SWIM *after* the LogStream is fully wired so the gateway
    // does not route requests here before we can serve them.
    topologyBroadcaster.onBecameLeader(partitionId, newTerm);

    LOG.info("Partition {} LogStream connected to PublishActor and PollActor", partitionId);
  }

  private void transitionToNonLeader(final Role newRole) {
    if (logStream == null) {
      // We never were leader in this term; nothing to tear down.
      return;
    }

    // Disconnect both actors first so in-flight requests fail cleanly before the LogStream is
    // closed. PollActor must be disconnected before closeLogStreamIfPresent() because it holds a
    // reference to the LogStream's record-available listener.
    publishActor.disconnect();
    pollActor.disconnect();
    closeLogStreamIfPresent();

    LOG.info(
        "Partition {} LogStream disconnected from PublishActor and PollActor (role={})",
        partitionId,
        newRole);
  }

  private void closeLogStreamIfPresent() {
    if (logStream != null) {
      logStream.close();
      logStream = null;
    }
    if (logStorage != null) {
      final var server = raftPartition.getServer();
      if (server != null) {
        server.removeCommitListener(logStorage);
      }
      logStorage = null;
    }
  }
}
