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
import io.camunda.eventbridge.broker.actor.PublishActor;
import io.camunda.eventbridge.broker.logstream.EventBridgeLogStorage;
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
 *       appender, builds a {@link LogStream} on top of it, and calls {@link PublishActor#connect}
 *       with the live writer and reader so that publish and poll requests can proceed.
 *   <li><strong>FOLLOWER / INACTIVE / CANDIDATE</strong>: calls {@link PublishActor#disconnect} and
 *       closes the {@link LogStream}, leaving the actor in a "not-leader" state where requests
 *       return a clear error.
 * </ul>
 *
 * <p>The role-change callback is invoked on RAFT's internal thread. The {@link LogStream} and
 * {@link EventBridgeLogStorage} are created synchronously on that thread; the actor connection is
 * posted to the actor's own thread via {@link PublishActor#connect}/{@link
 * PublishActor#disconnect}.
 */
public final class EventBridgePartition implements RaftRoleChangeListener {

  private static final Logger LOG = LoggerFactory.getLogger(EventBridgePartition.class);

  /** Default maximum fragment size fed to the LogStream sequencer (4 MiB). */
  private static final int DEFAULT_MAX_FRAGMENT_SIZE = 4 * 1024 * 1024;

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final PublishActor publishActor;

  /** Non-null only while this node is leader. */
  private EventBridgeLogStorage logStorage;

  /** Non-null only while this node is leader. */
  private LogStream logStream;

  public EventBridgePartition(
      final int partitionId, final RaftPartition raftPartition, final PublishActor publishActor) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.publishActor = publishActor;
    raftPartition.addRoleChangeListener(this);
  }

  // -------------------------------------------------------------------------
  // RaftRoleChangeListener

  @Override
  public void onNewRole(final Role newRole, final long newTerm) {
    LOG.info("Partition {} role change → {} (term {})", partitionId, newRole, newTerm);
    if (newRole == Role.LEADER) {
      transitionToLeader();
    } else {
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

  private void transitionToLeader() {
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
    publishActor.connect(logStream.newLogStreamWriter(), logStream.newLogStreamReader());

    LOG.info("Partition {} LogStream connected to PublishActor", partitionId);
  }

  private void transitionToNonLeader(final Role newRole) {
    if (logStream == null) {
      // We never were leader in this term; nothing to tear down.
      return;
    }

    // Disconnect the actor first so in-flight publishes fail cleanly.
    publishActor.disconnect();
    closeLogStreamIfPresent();

    LOG.info(
        "Partition {} LogStream disconnected from PublishActor (role={})", partitionId, newRole);
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
