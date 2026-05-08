/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.RaftRoleChangeListener;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor that owns the system partition's in-memory {@link ClusterConfiguration} and mediates all
 * reads/writes against it.
 *
 * <p>Lifecycle: {@link #onActorStarted()} subscribes a {@link RaftCommitListener} and {@link
 * RaftRoleChangeListener} on the underlying {@link RaftPartition}. On every Raft commit, the
 * listener drains newly committed entries from a single {@link RaftLogReader} and applies them in
 * order. On role change away from leader, all pending writer futures are failed with {@link
 * SystemPartition.NotLeaderException} so the caller can retry against the new leader.
 */
public final class SystemPartitionStateMachine extends Actor implements SystemPartition {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionStateMachine.class);

  private final RaftPartition raftPartition;
  private final String actorName;

  private RaftLogReader reader;
  private long lastAppliedIndex = 0;
  private ClusterConfiguration current = ClusterConfiguration.uninitialized();

  // index -> caller waiting for that entry to commit-and-apply on this replica
  private final Map<Long, CompletableActorFuture<ClusterConfiguration>> pendingAcks =
      new HashMap<>();

  private final CopyOnWriteArrayList<Consumer<ClusterConfiguration>> commitListeners =
      new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Consumer<Boolean>> leaderListeners =
      new CopyOnWriteArrayList<>();

  private final RaftCommitListener commitListener = this::onCommitFromRaft;
  private final RaftRoleChangeListener roleListener = this::onRoleChange;

  private boolean started = false;

  public SystemPartitionStateMachine(final RaftPartition raftPartition) {
    this.raftPartition = raftPartition;
    actorName = "SystemPartitionStateMachine-" + raftPartition.id().id();
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarted() {
    started = true;
    raftPartition.addRoleChangeListener(roleListener);
    final RaftPartitionServer server = raftPartition.getServer();
    if (server != null) {
      server.addCommitListener(commitListener);
      // Drain anything already committed on disk before our listener was registered. This handles
      // restarts: on every boot we replay the entire committed log to rebuild current state.
      // The committed reader naturally stops at the current commit index, so passing
      // Long.MAX_VALUE drains exactly the committed prefix. Snapshot-based recovery is
      // intentionally out of scope for now — log replay is sufficient because the state is a
      // single ClusterConfiguration record.
      drainUpTo(Long.MAX_VALUE);
    } else {
      LOG.warn(
          "System partition {} has no local server (this broker is not a member); state machine will not apply entries",
          raftPartition.id());
    }
  }

  @Override
  protected void onActorClosing() {
    final RaftPartitionServer server = raftPartition.getServer();
    if (server != null) {
      server.removeCommitListener(commitListener);
    }
    raftPartition.removeRoleChangeListener(roleListener);
    if (reader != null) {
      reader.close();
      reader = null;
    }
    pendingAcks
        .values()
        .forEach(
            f ->
                f.completeExceptionally(
                    new SystemPartition.NotLeaderException(
                        "System partition state machine is closing")));
    pendingAcks.clear();
  }

  @Override
  public ActorFuture<ClusterConfiguration> update(final ClusterConfiguration newConfiguration) {
    final CompletableActorFuture<ClusterConfiguration> future = new CompletableActorFuture<>();
    actor.run(
        () -> {
          if (!started) {
            future.completeExceptionally(
                new IllegalStateException("System partition state machine not started"));
            return;
          }
          final RaftPartitionServer server = raftPartition.getServer();
          if (server == null || server.getRole() != Role.LEADER) {
            future.completeExceptionally(
                new SystemPartition.NotLeaderException(
                    "Local replica is not the leader of the system partition"));
            return;
          }
          final var maybeAppender = server.getAppender();
          if (maybeAppender.isEmpty()) {
            future.completeExceptionally(
                new SystemPartition.NotLeaderException(
                    "No leader appender available on system partition"));
            return;
          }
          final ZeebeLogAppender appender = maybeAppender.get();
          final SystemPartitionRecord record =
              new SystemPartitionRecord(current.version(), newConfiguration);
          appender.appendEntry(
              0L,
              0L,
              record.encode(),
              new AppendListener() {
                @Override
                public void onWrite(final IndexedRaftLogEntry indexed) {
                  actor.run(() -> pendingAcks.put(indexed.index(), future));
                }

                @Override
                public void onWriteError(final Throwable error) {
                  actor.run(() -> future.completeExceptionally(error));
                }

                @Override
                public void onCommitError(final long index, final Throwable error) {
                  actor.run(
                      () -> {
                        final var pending = pendingAcks.remove(index);
                        if (pending != null) {
                          pending.completeExceptionally(error);
                        }
                      });
                }
              });
        });
    return future;
  }

  @Override
  public ActorFuture<ClusterConfiguration> query() {
    final CompletableActorFuture<ClusterConfiguration> future = new CompletableActorFuture<>();
    actor.run(() -> future.complete(current));
    return future;
  }

  @Override
  public boolean isLeader() {
    final RaftPartitionServer server = raftPartition.getServer();
    return server != null && server.getRole() == Role.LEADER;
  }

  @Override
  public void addCommitListener(final Consumer<ClusterConfiguration> listener) {
    commitListeners.add(listener);
  }

  @Override
  public void removeCommitListener(final Consumer<ClusterConfiguration> listener) {
    commitListeners.remove(listener);
  }

  @Override
  public void addLeaderListener(final Consumer<Boolean> listener) {
    leaderListeners.add(listener);
  }

  /** Called by Raft when a new batch of entries has been committed. May skip indices. */
  private void onCommitFromRaft(final long index) {
    actor.run(() -> drainUpTo(index));
  }

  private void drainUpTo(final long upToIndex) {
    if (reader == null) {
      final RaftPartitionServer server = raftPartition.getServer();
      if (server == null) {
        return;
      }
      reader = server.openReader();
      if (lastAppliedIndex == 0) {
        reader.reset();
      } else {
        reader.seek(lastAppliedIndex + 1);
      }
    }
    while (reader.hasNext()) {
      final IndexedRaftLogEntry entry = reader.next();
      if (entry.index() > upToIndex) {
        break;
      }
      if (!entry.isApplicationEntry()) {
        // configuration entries etc — skip but advance
        lastAppliedIndex = entry.index();
        continue;
      }
      try {
        applyEntry(entry);
      } catch (final Exception e) {
        LOG.error(
            "Failed to apply system partition entry at index {}; skipping. State may diverge from leader.",
            entry.index(),
            e);
      }
      lastAppliedIndex = entry.index();
    }
  }

  private void applyEntry(final IndexedRaftLogEntry entry) {
    final BufferWriter writer = entry.getApplicationEntry().dataWriter();
    final byte[] bytes = new byte[writer.getLength()];
    final MutableDirectBuffer scratch = new UnsafeBuffer(bytes);
    writer.write(scratch, 0);
    final SystemPartitionRecord record = SystemPartitionRecord.decode(bytes);

    if (record.expectedPreviousVersion() != current.version()) {
      LOG.info(
          "Discarding system partition entry @ index {}: CAS mismatch (expected previous version {}, current {}).",
          entry.index(),
          record.expectedPreviousVersion(),
          current.version());
      final var pending = pendingAcks.remove(entry.index());
      if (pending != null) {
        pending.completeExceptionally(
            new SystemPartition.ConcurrentModificationException(
                "Concurrent update committed first; expected previous version "
                    + record.expectedPreviousVersion()
                    + " but local current version is "
                    + current.version()));
      }
      return;
    }

    current = record.newConfiguration();
    LOG.debug(
        "System partition applied entry @ index {}; new ClusterConfiguration version={}",
        entry.index(),
        current.version());

    final var pending = pendingAcks.remove(entry.index());
    if (pending != null) {
      pending.complete(current);
    }
    for (final var listener : commitListeners) {
      try {
        listener.accept(current);
      } catch (final Exception e) {
        LOG.warn("System partition commit listener {} threw", listener, e);
      }
    }
  }

  private void onRoleChange(final Role newRole, final long newTerm) {
    actor.run(
        () -> {
          LOG.debug(
              "System partition {} role change: {} (term {})",
              raftPartition.id(),
              newRole,
              newTerm);
          final boolean isLeader = newRole == Role.LEADER;
          if (!isLeader && !pendingAcks.isEmpty()) {
            final var failure =
                new SystemPartition.NotLeaderException(
                    "Lost leadership before commit (new role: " + newRole + ")");
            pendingAcks.values().forEach(f -> f.completeExceptionally(failure));
            pendingAcks.clear();
          }
          for (final var listener : leaderListeners) {
            try {
              listener.accept(isLeader);
            } catch (final Exception e) {
              LOG.warn("System partition leader listener {} threw", listener, e);
            }
          }
        });
  }
}
