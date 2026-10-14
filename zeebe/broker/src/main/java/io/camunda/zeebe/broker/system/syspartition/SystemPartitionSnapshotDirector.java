/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * The system partition's counterpart of a data partition's {@code AsyncSnapshotDirector}: at the
 * configured snapshot period it takes a transient snapshot of the state at the last applied
 * position, flushes the journal, and persists the snapshot. Persisting a snapshot lets Raft compact
 * the log up to it.
 *
 * <p>Applied entries are already committed, so unlike for data partitions there is no need to wait
 * for the snapshot position to be committed. A partition without any snapshot gets a forced one on
 * start, as data partitions get when they are bootstrapped.
 */
final class SystemPartitionSnapshotDirector extends Actor {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemPartitionStateController stateController;
  private final SystemPartitionStateMachine stateMachine;
  private final RaftPartition raftPartition;
  private final Duration snapshotRate;
  private boolean snapshotInProgress;

  SystemPartitionSnapshotDirector(
      final SystemPartitionStateController stateController,
      final SystemPartitionStateMachine stateMachine,
      final RaftPartition raftPartition,
      final Duration snapshotRate) {
    super("SystemPartitionSnapshotDirector", SystemPartition.PARTITION_ID);
    this.stateController = stateController;
    this.stateMachine = stateMachine;
    this.raftPartition = raftPartition;
    this.snapshotRate = snapshotRate;
  }

  @Override
  protected void onActorStarted() {
    if (stateController.latestSnapshotPosition() < 0) {
      trySnapshot(true);
    }
    actor.runAtFixedRate(snapshotRate, () -> trySnapshot(false));
  }

  private void trySnapshot(final boolean forceSnapshot) {
    if (snapshotInProgress) {
      return;
    }
    snapshotInProgress = true;
    actor.runOnCompletion(
        stateMachine.getLastAppliedPosition(),
        (lastAppliedPosition, error) -> {
          if (error != null) {
            onSnapshotFailed(error);
          } else if (!forceSnapshot
              && lastAppliedPosition <= stateController.latestSnapshotPosition()) {
            LOG.trace("No new entries applied since the latest snapshot, skipping snapshot");
            snapshotInProgress = false;
          } else {
            snapshot(lastAppliedPosition, forceSnapshot);
          }
        });
  }

  private void snapshot(final long lastAppliedPosition, final boolean forceSnapshot) {
    actor.runOnCompletion(
        stateController.takeTransientSnapshot(lastAppliedPosition, forceSnapshot, actor),
        (transientSnapshot, takeError) -> {
          if (takeError != null) {
            onSnapshotFailed(takeError);
            return;
          }
          actor.runOnCompletion(
              flushJournal(),
              (flushed, flushError) -> {
                if (flushError != null) {
                  onSnapshotFailed(flushError);
                } else {
                  persistSnapshot(transientSnapshot);
                }
              });
        });
  }

  private ActorFuture<Void> flushJournal() {
    final var future = new CompletableActorFuture<Void>();
    raftPartition
        .getServer()
        .flushLog()
        .whenCompleteAsync(
            (ok, error) -> {
              if (error != null) {
                future.completeExceptionally(error);
              } else {
                future.complete(null);
              }
            },
            actor);
    return future;
  }

  private void persistSnapshot(final TransientSnapshot transientSnapshot) {
    actor.runOnCompletion(
        transientSnapshot.persist(),
        (PersistedSnapshot persisted, Throwable error) -> {
          if (error != null) {
            onSnapshotFailed(error);
          } else {
            snapshotInProgress = false;
            LOG.info("Persisted system partition snapshot {}", persisted.getId());
          }
        });
  }

  private void onSnapshotFailed(final Throwable error) {
    snapshotInProgress = false;
    LOG.warn("Failed to take system partition snapshot", error);
  }
}
