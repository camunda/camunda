/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reserves a snapshot for a checkpoint before its record is written, so that the backup of the
 * checkpoint finds a snapshot which cannot be compacted away in the meantime.
 *
 * <p>It forces a fresh snapshot and reserves it. If no fresh snapshot is taken, it falls back to
 * the latest snapshot, and if there is none, the checkpoint is written without a snapshot.
 */
final class CheckpointSnapshotReserver {
  private static final Logger LOG = LoggerFactory.getLogger(CheckpointSnapshotReserver.class);

  private final PersistedSnapshotStore snapshotStore;
  private final SnapshotTrigger snapshotTrigger;
  private final CheckpointState checkpointState;
  private final ConcurrencyControl concurrencyControl;

  CheckpointSnapshotReserver(
      final PersistedSnapshotStore snapshotStore,
      final SnapshotTrigger snapshotTrigger,
      final CheckpointState checkpointState,
      final ConcurrencyControl concurrencyControl) {
    this.snapshotStore = snapshotStore;
    this.snapshotTrigger = snapshotTrigger;
    this.checkpointState = checkpointState;
    this.concurrencyControl = concurrencyControl;
  }

  /**
   * @return future completed with the id of the snapshot reserved for the checkpoint, or empty if
   *     the checkpoint needs none or no snapshot could be reserved
   */
  ActorFuture<Optional<String>> reserveFor(final long checkpointId) {
    // The processor ignores a checkpoint that is not newer than the latest one, e.g. a retried
    // request, and releases its reservation. This check only saves forcing a snapshot for it: a
    // stale read costs a reservation that the processor releases again.
    if (checkpointId <= checkpointState.getLatestCheckpointId()) {
      return CompletableActorFuture.completed(Optional.empty());
    }

    return snapshotTrigger
        .forceSnapshot()
        .andThen(snapshot -> reserveFreshSnapshot(checkpointId, snapshot), concurrencyControl)
        .andThen(
            (snapshotId, error) ->
                error == null
                    ? CompletableActorFuture.completed(Optional.of(snapshotId))
                    : reserveLatestSnapshot(checkpointId, error),
            concurrencyControl);
  }

  /** Releases a reservation made by {@link #reserveFor}, e.g. if the checkpoint was not written. */
  void release(final long checkpointId, final Optional<String> snapshotId) {
    snapshotId.ifPresent(id -> snapshotStore.releaseReservation(checkpointId, id));
  }

  private ActorFuture<String> reserveFreshSnapshot(
      final long checkpointId, final PersistedSnapshot snapshot) {
    if (snapshot == null) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException("Snapshot was skipped, e.g. because one is already taken"));
    }
    final var snapshotId = snapshot.getId();
    return snapshotStore
        .reserveSnapshot(checkpointId, snapshotId)
        .thenApply(ignored -> snapshotId, concurrencyControl);
  }

  private ActorFuture<Optional<String>> reserveLatestSnapshot(
      final long checkpointId, final Throwable cause) {
    LOG.debug("Failed to reserve a fresh snapshot for the checkpoint, reserving the latest", cause);
    return snapshotStore
        .reserveLatestSnapshot(checkpointId)
        .andThen(
            (snapshotId, error) -> {
              if (error != null) {
                LOG.warn("Failed to reserve a snapshot for the checkpoint", error);
                return CompletableActorFuture.completed(Optional.empty());
              }
              return CompletableActorFuture.completed(snapshotId);
            },
            concurrencyControl);
  }
}
