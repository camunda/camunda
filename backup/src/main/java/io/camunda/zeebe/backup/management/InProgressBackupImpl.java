/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

final class InProgressBackupImpl implements InProgressBackup {
  private static final String ERROR_MSG_NO_VALID_SNAPSHOT =
      "Cannot find a snapshot that can be included in the backup %d. All available snapshots (%s) have processedPosition or exportedPosition > checkpointPosition %d";

  private final PersistedSnapshotStore snapshotStore;
  private final BackupIdentifier backupId;
  private final long checkpointPosition;
  private final int numberOfPartitions;
  private final ConcurrencyControl concurrencyControl;

  // Snapshot related data
  private boolean hasSnapshot = true;
  private Set<PersistedSnapshot> availableValidSnapshots;
  private SnapshotReservation snapshotReservation;
  private PersistedSnapshot reservedSnapshot;

  InProgressBackupImpl(
      final PersistedSnapshotStore snapshotStore,
      final BackupIdentifier backupId,
      final long checkpointPosition,
      final int numberOfPartitions,
      final ConcurrencyControl concurrencyControl) {
    this.snapshotStore = snapshotStore;
    this.backupId = backupId;
    this.checkpointPosition = checkpointPosition;
    this.numberOfPartitions = numberOfPartitions;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public long checkpointId() {
    return backupId.checkpointId();
  }

  @Override
  public long checkpointPosition() {
    return checkpointPosition;
  }

  @Override
  public ActorFuture<Void> findValidSnapshot() {
    final ActorFuture<Void> result = concurrencyControl.createFuture();
    snapshotStore
        .getAvailableSnapshots()
        .onComplete(
            (snapshots, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else if (snapshots.isEmpty()) {
                // no snapshot is taken until now, so return successfully
                hasSnapshot = false;
                result.complete(null);
              } else {
                final var eitherSnapshots = findValidSnapshot(snapshots);
                if (eitherSnapshots.isLeft()) {
                  result.completeExceptionally(
                      new SnapshotNotFoundException(eitherSnapshots.getLeft()));
                } else {
                  availableValidSnapshots = eitherSnapshots.get();
                  result.complete(null);
                }
              }
            });

    return result;
  }

  @Override
  public ActorFuture<Void> reserveSnapshot() {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    if (hasSnapshot) {
      // Try reserve snapshot in the order - latest snapshot first
      final var snapshotIterator =
          availableValidSnapshots.stream()
              .sorted(Comparator.comparingLong(PersistedSnapshot::getCompactionBound).reversed())
              .iterator();

      tryReserveAnySnapshot(snapshotIterator, future);
    } else {
      // No snapshot to reserve
      future.complete(null);
    }

    return future;
  }

  @Override
  public ActorFuture<Void> findSnapshotFiles() {
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public ActorFuture<Void> findSegmentFiles() {
    return concurrencyControl.createCompletedFuture();
  }

  @Override
  public Backup createBackup() {
    final String snapshotId;

    if (hasSnapshot) {
      snapshotId = reservedSnapshot.getId();
    } else {
      // To do : change snapshotId in BackupDescriptor to Optional
      snapshotId = null;
    }

    final var backupDescriptor =
        new BackupDescriptorImpl(snapshotId, checkpointPosition, numberOfPartitions);
    return new BackupImpl(backupId, backupDescriptor, null, null);
  }

  @Override
  public void fail(final Throwable error) {
    // To be implemented
  }

  @Override
  public void close() {
    if (snapshotReservation != null) {
      snapshotReservation.release();
    }
  }

  private Either<String, Set<PersistedSnapshot>> findValidSnapshot(
      final Set<PersistedSnapshot> snapshots) {
    final var validSnapshots =
        snapshots.stream()
            .filter(s -> s.getMetadata().processedPosition() < checkpointPosition) // &&
            .filter(s -> s.getMetadata().lastFollowupEventPosition() < checkpointPosition)
            .collect(Collectors.toSet());

    if (validSnapshots.isEmpty()) {
      return Either.left(
          String.format(
              ERROR_MSG_NO_VALID_SNAPSHOT, checkpointId(), snapshots, checkpointPosition));
    } else {
      return Either.right(validSnapshots);
    }
  }

  private void tryReserveAnySnapshot(
      final Iterator<PersistedSnapshot> snapshotIterator, final ActorFuture<Void> future) {
    final var snapshot = snapshotIterator.next();
    final ActorFuture<SnapshotReservation> reservationFuture = snapshot.reserve();
    reservationFuture.onComplete(
        (reservation, error) -> {
          if (error != null) {
            if (snapshotIterator.hasNext()) {
              tryReserveAnySnapshot(snapshotIterator, future);
            } else {
              // fail future.
              future.completeExceptionally("No snapshot could be reserved", error);
            }
          } else {
            // complete
            snapshotReservation = reservation;
            reservedSnapshot = snapshot;
            future.complete(null);
          }
        });
  }
}
