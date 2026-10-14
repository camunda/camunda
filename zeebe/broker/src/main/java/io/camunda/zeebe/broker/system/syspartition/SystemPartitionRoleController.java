/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Reacts to the role transitions of this broker's system partition replica, like {@code
 * ZeebePartition} does for data partitions: it is the only Raft role listener of the system
 * partition. On becoming leader it installs a {@link SystemPartitionWriter} and, once this broker
 * has applied every entry in its log, hands it to the {@link SystemPartitionListener listeners}; on
 * any other transition it revokes it.
 */
final class SystemPartitionRoleController extends Actor {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final RaftPartition raftPartition;
  private final SystemPartitionStateMachine stateMachine;
  private final List<SystemPartitionListener> listeners;
  private final RaftRoleChangeListener roleChangeListener =
      (role, term) -> actor.run(() -> onRoleChange(role, term));

  private SystemPartitionWriter writer;
  // whether the writer was handed out, which waits until this broker has caught up
  private boolean writerHandedOut;
  private long term;

  SystemPartitionRoleController(
      final RaftPartition raftPartition,
      final SystemPartitionStateMachine stateMachine,
      final Collection<SystemPartitionListener> listeners) {
    super("SystemPartitionRoleController", SystemPartition.PARTITION_ID);
    this.raftPartition = raftPartition;
    this.stateMachine = stateMachine;
    this.listeners = new ArrayList<>(listeners);
  }

  /** Adds the listener; it is notified right away if this broker is currently the leader. */
  void addListener(final SystemPartitionListener listener) {
    actor.run(
        () -> {
          listeners.add(listener);
          if (writerHandedOut) {
            notify(listener, l -> l.onBecomingLeader(term, writer));
          }
        });
  }

  void removeListener(final SystemPartitionListener listener) {
    actor.run(() -> listeners.remove(listener));
  }

  @Override
  protected void onActorStarting() {
    // notified right away of the current role
    raftPartition.addRoleChangeListener(roleChangeListener);
  }

  @Override
  protected void onActorClosing() {
    raftPartition.removeRoleChangeListener(roleChangeListener);
    revokeWriter();
  }

  private void onRoleChange(final Role role, final long newTerm) {
    revokeWriter();
    term = newTerm;
    switch (role) {
      case LEADER -> becomeLeader(newTerm);
      case FOLLOWER -> listeners.forEach(l -> notify(l, it -> it.onBecomingFollower(newTerm)));
      case INACTIVE -> listeners.forEach(l -> notify(l, it -> it.onBecomingInactive(newTerm)));
      default -> {
        // candidates and promotables can neither write nor have settled on a leader yet
      }
    }
  }

  private void becomeLeader(final long newTerm) {
    final var server = raftPartition.getServer();
    final var appender = server.getAppender();
    if (appender.isEmpty() || server.getTerm() != newTerm) {
      // already lost leadership again; the next role change follows
      return;
    }
    final var leaderWriter = new SystemPartitionWriter(raftPartition, appender.get(), actor);
    writer = leaderWriter;
    // Hand out the writer only once every entry in the log is applied, so that listeners read
    // everything committed before this leadership, as a data partition replays before processing.
    actor.runOnCompletion(
        stateMachine.awaitApplied(leaderWriter.initialLastPosition()),
        (ok, error) -> {
          if (writer != leaderWriter) {
            return; // the leadership ended while catching up
          }
          if (error != null) {
            LOG.warn("Failed to catch up as system partition leader in term {}", newTerm, error);
            return;
          }
          writerHandedOut = true;
          listeners.forEach(l -> notify(l, it -> it.onBecomingLeader(newTerm, leaderWriter)));
        });
  }

  private void revokeWriter() {
    if (writer != null) {
      writer.close();
      writer = null;
      writerHandedOut = false;
    }
  }

  private void notify(
      final SystemPartitionListener listener, final Consumer<SystemPartitionListener> call) {
    try {
      call.accept(listener);
    } catch (final RuntimeException e) {
      // one failing listener must not keep the others from learning about the transition
      LOG.warn("System partition listener {} failed to handle a role transition", listener, e);
    }
  }
}
