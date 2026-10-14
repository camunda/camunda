/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.atomix.raft.RaftCommitListener;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Applies the committed system partition log to the {@link SystemKeyValueStore} on every broker,
 * leader and followers alike, like a data partition's stream processor replays its log. It starts
 * after the entry at the position {@link SystemPartitionStateController#recover() recovered} from
 * the latest snapshot.
 */
final class SystemPartitionStateMachine extends Actor {
  private final RaftPartition raftPartition;
  private final SystemKeyValueStore keyValueStore;
  private final RaftCommitListener commitListener = index -> actor.run(this::applyCommitted);

  // callers waiting for an entry to be applied, by its position
  private final TreeMap<Long, List<CompletableActorFuture<Void>>> appliedWaiters = new TreeMap<>();

  private RaftLogReader reader;
  private long appliedPosition;

  SystemPartitionStateMachine(
      final RaftPartition raftPartition,
      final SystemKeyValueStore keyValueStore,
      final long recoveredPosition) {
    super("SystemPartitionStateMachine", SystemPartition.PARTITION_ID);
    this.raftPartition = raftPartition;
    this.keyValueStore = keyValueStore;
    appliedPosition = recoveredPosition;
  }

  /** Returns the state as of the latest applied entry. Thread-safe. */
  SystemPartitionState state() {
    return new SystemPartitionState(SystemPartitionState.CURRENT_VERSION, keyValueStore.entries());
  }

  /** Returns the position of the latest applied entry, or 0 if none. */
  ActorFuture<Long> getLastAppliedPosition() {
    return actor.call(() -> appliedPosition);
  }

  /**
   * Returns a future completed once the entry at the given position, or a later one, is applied.
   */
  ActorFuture<Void> awaitApplied(final long position) {
    final var applied = new CompletableActorFuture<Void>();
    actor.run(
        () -> {
          if (appliedPosition >= position) {
            applied.complete(null);
          } else {
            appliedWaiters.computeIfAbsent(position, ignored -> new ArrayList<>()).add(applied);
          }
        });
    return applied;
  }

  @Override
  protected void onActorStarting() {
    // reads only committed entries, so it works the same on the leader and on followers
    reader = raftPartition.getServer().openReader();
    raftPartition.getServer().addCommitListener(commitListener);
  }

  @Override
  protected void onActorStarted() {
    // The partition may have committed before this actor started, with no further commits to
    // notify about.
    applyCommitted();
  }

  @Override
  protected void onActorClosing() {
    raftPartition.getServer().removeCommitListener(commitListener);
    reader.close();
    final var closed = new IllegalStateException("The system partition state machine was closed");
    appliedWaiters
        .values()
        .forEach(waiters -> waiters.forEach(w -> w.completeExceptionally(closed)));
    appliedWaiters.clear();
  }

  private void applyCommitted() {
    if (!reader.hasNext()) {
      // A reader opened on a follower's empty log does not see entries replicated afterwards;
      // position it explicitly at the latest applied entry, which is skipped below.
      reader.seekToAsqn(appliedPosition);
    }
    while (reader.hasNext()) {
      final var indexed = reader.next();
      if (!indexed.isApplicationEntry()) {
        continue;
      }
      // application entries read from the log are always serialized
      final var entry = (SerializedApplicationEntry) indexed.getApplicationEntry();
      if (entry.highestPosition() <= appliedPosition) {
        continue;
      }
      keyValueStore.apply(entry.data());
      appliedPosition = entry.highestPosition();
    }
    final var reached = appliedWaiters.headMap(appliedPosition, true);
    reached.values().forEach(waiters -> waiters.forEach(w -> w.complete(null)));
    reached.clear();
  }
}
