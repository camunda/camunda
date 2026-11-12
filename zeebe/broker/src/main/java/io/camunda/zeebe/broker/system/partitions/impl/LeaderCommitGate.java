/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.atomix.raft.RaftApplicationEntryCommittedPositionListener;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.db.cache.CommitGate;
import io.camunda.zeebe.logstreams.log.LogStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The {@link CommitGate} of one term in which this replica leads the partition. Each database the
 * partition opens gets a new gate for the term it opens in.
 *
 * <p>Raft tells the gate everything it needs, on Raft's thread:
 *
 * <ul>
 *   <li>Once Raft reports that it leads in the gate's term, the gate reads where the committed log
 *       ends. Raft only reports a new leader once everything before its term is committed.
 *   <li>From then on, Raft reports each record of this leader that the log commits.
 *   <li>Once Raft reports any other role or term, the gate stops for good. A later leader may write
 *       other records at the positions of records this leader never committed.
 * </ul>
 *
 * <p>Until Raft reports that it leads in the gate's term, the committed position doesn't move.
 */
@NullMarked
public final class LeaderCommitGate
    implements CommitGate,
        RaftRoleChangeListener,
        RaftApplicationEntryCommittedPositionListener,
        AutoCloseable {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final long term;
  private final RaftPartitionServer raft;
  private final LogStream logStream;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private volatile Phase phase = Phase.WAITING;
  private volatile long committedPosition = -1;

  /**
   * Creates the gate and starts listening to {@code raft}.
   *
   * @param term the term the database opens in
   * @param raft the Raft server of the partition
   * @param logStream the log stream the partition writes to in {@code term}
   */
  public LeaderCommitGate(
      final long term, final RaftPartitionServer raft, final LogStream logStream) {
    this.term = term;
    this.raft = Objects.requireNonNull(raft, "must specify a Raft server");
    this.logStream = Objects.requireNonNull(logStream, "must specify a log stream");
    raft.addCommittedEntryListener(this);
    raft.addRoleChangeListener(this);
  }

  @Override
  public long lastWrittenPosition() {
    return logStream.getLastWrittenPosition();
  }

  @Override
  public long committedPosition() {
    return committedPosition;
  }

  @Override
  public boolean stopped() {
    return phase == Phase.STOPPED;
  }

  @Override
  public void addCommitListener(final Runnable listener) {
    listeners.add(listener);
  }

  @Override
  public void removeCommitListener(final Runnable listener) {
    listeners.remove(listener);
  }

  @Override
  public synchronized void onNewRole(final Role newRole, final long newTerm) {
    if (phase == Phase.STOPPED) {
      return;
    }
    if (newRole != Role.LEADER || newTerm != term) {
      stop();
    } else if (phase == Phase.WAITING) {
      phase = Phase.LEADING;
      readCommittedEndOfLog();
    }
  }

  @Override
  public synchronized void onCommit(final long committedPosition) {
    if (phase == Phase.LEADING) {
      moveTo(committedPosition);
    }
  }

  /** Stops the gate and stops listening to Raft. Calling it again does nothing. */
  @Override
  public synchronized void close() {
    if (phase != Phase.STOPPED) {
      stop();
    }
  }

  private void readCommittedEndOfLog() {
    // Raft calls all role listeners in one try block: throwing here would hide the new role from
    // the listeners after this one
    try (final var reader = logStream.newLogStreamReader()) {
      moveTo(Math.max(0, reader.seekToEnd()));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to read where the committed log of partition {} ends in term {}; its state waits"
              + " for the next record the log commits",
          logStream.getPartitionId(),
          term,
          e);
    }
  }

  private void moveTo(final long position) {
    if (position > committedPosition) {
      committedPosition = position;
      notifyListeners();
    }
  }

  private void stop() {
    phase = Phase.STOPPED;
    raft.removeRoleChangeListener(this);
    raft.removeCommittedEntryListener(this);
    notifyListeners();
  }

  private void notifyListeners() {
    for (final var listener : listeners) {
      try {
        listener.run();
      } catch (final RuntimeException e) {
        LOG.warn(
            "Failed to tell the state of partition {} that its log moved on",
            logStream.getPartitionId(),
            e);
      }
    }
  }

  private enum Phase {
    WAITING,
    LEADING,
    STOPPED
  }
}
