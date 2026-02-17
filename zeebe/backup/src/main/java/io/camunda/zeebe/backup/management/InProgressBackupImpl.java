/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InProgressBackupImpl implements InProgressBackup {

  private static final Logger LOG = LoggerFactory.getLogger(InProgressBackupImpl.class);

  private static final String ERROR_MSG_NO_VALID_SNAPSHOT =
      "Cannot find a snapshot that can be included in the backup %d. All available snapshots (%s) have processedPosition or lastFollowupEventPosition > checkpointPosition %d";

  private final PersistedSnapshotStore snapshotStore;
  private final BackupIdentifier backupId;
  private final BackupDescriptor backupDescriptor;
  private final ConcurrencyControl concurrencyControl;

  private final Path segmentsDirectory;

  private final JournalInfoProvider journalInfoProvider;

  // Snapshot related data
  private boolean hasSnapshot = true;
  private Set<PersistedSnapshot> availableValidSnapshots;
  private SnapshotReservation snapshotReservation;
  private PersistedSnapshot reservedSnapshot;
  private NamedFileSet snapshotFileSet;
  private NamedFileSet segmentsFileSet;
  private OptionalLong firstLogPosition = OptionalLong.empty();

  InProgressBackupImpl(
      final PersistedSnapshotStore snapshotStore,
      final BackupIdentifier backupId,
      final BackupDescriptor backupDescriptor,
      final ConcurrencyControl concurrencyControl,
      final Path segmentsDirectory,
      final JournalInfoProvider journalInfoProvider) {
    this.snapshotStore = snapshotStore;
    this.backupId = backupId;
    this.backupDescriptor = backupDescriptor;
    this.concurrencyControl = concurrencyControl;
    this.segmentsDirectory = segmentsDirectory;
    this.journalInfoProvider = journalInfoProvider;
  }

  @Override
  public OptionalLong getFirstLogPosition() {
    return firstLogPosition;
  }

  @Override
  public BackupDescriptor backupDescriptor() {
    return backupDescriptor;
  }

  @Override
  public BackupIdentifier id() {
    return backupId;
  }

  @Override
  public ActorFuture<Set<PersistedSnapshot>> findValidSnapshot() {
    final ActorFuture<Set<PersistedSnapshot>> result = concurrencyControl.createFuture();
    snapshotStore
        .getAvailableSnapshots()
        .onComplete(
            (snapshots, error) -> {
              if (error != null) {
                LOG.atError()
                    .addKeyValue("backup", backupId)
                    .setCause(error)
                    .setMessage("Failed to retrieve available snapshots")
                    .log();
                result.completeExceptionally(error);
              } else if (snapshots.isEmpty()) {
                LOG.atTrace()
                    .addKeyValue("backup", backupId)
                    .setMessage("Found no snapshots for backup")
                    .log();
                // no snapshot is taken until now, so return successfully
                hasSnapshot = false;
                availableValidSnapshots = Collections.emptySet();
                result.complete(availableValidSnapshots);
              } else {
                LOG.atTrace()
                    .addKeyValue("backup", backupId)
                    .addKeyValue("snapshots", snapshots::size)
                    .setMessage("Found snapshots for backup")
                    .log();
                final var eitherSnapshots = findValidSnapshot(snapshots);
                if (eitherSnapshots.isLeft()) {
                  result.completeExceptionally(
                      new SnapshotNotFoundException(eitherSnapshots.getLeft()));
                } else {
                  availableValidSnapshots = eitherSnapshots.get();
                  result.complete(availableValidSnapshots);
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
    if (!hasSnapshot) {
      snapshotFileSet = new NamedFileSetImpl(Map.of());
      return concurrencyControl.createCompletedFuture();
    }

    final ActorFuture<Void> filesCollected = concurrencyControl.createFuture();

    final Path snapshotRoot = reservedSnapshot.getPath();
    try (final var stream = Files.list(snapshotRoot)) {
      final var snapshotFiles = stream.collect(Collectors.toSet());
      final var checksumFile = reservedSnapshot.getChecksumPath();

      final Map<String, Path> fileSet = new HashMap<>();
      snapshotFiles.forEach(
          path -> {
            final var name = snapshotRoot.relativize(path);
            fileSet.put(name.toString(), path);
          });

      fileSet.put(checksumFile.getFileName().toString(), checksumFile);

      snapshotFileSet = new NamedFileSetImpl(fileSet);
      LOG.atTrace()
          .addKeyValue("backup", backupId)
          .addKeyValue("snapshot", reservedSnapshot.getId())
          .addKeyValue("files", snapshotFileSet.files()::size)
          .setMessage("Collected snapshot files for backup")
          .log();

      filesCollected.complete(null);

    } catch (final IOException e) {
      LOG.atError()
          .addKeyValue("backup", backupId)
          .addKeyValue("snapshot", reservedSnapshot.getId())
          .setCause(e)
          .setMessage("Failed to collect snapshot files for backup")
          .log();
      filesCollected.completeExceptionally(e);
    }

    return filesCollected;
  }

  @Override
  public ActorFuture<Void> findSegmentFiles() {
    final ActorFuture<Void> filesCollected = concurrencyControl.createFuture();
    try {
      long maxIndex = 0L;
      if (reservedSnapshot != null) {
        maxIndex = reservedSnapshot.getIndex();
      }
      journalInfoProvider
          .getTailSegments(maxIndex)
          .whenComplete(
              (tailSegments, throwable) -> {
                if (throwable != null) {
                  LOG.atError()
                      .addKeyValue("backup", backupId)
                      .setCause(throwable)
                      .setMessage("Failed to retrieve journal segments for backup")
                      .log();
                  filesCollected.completeExceptionally(throwable);
                } else if (tailSegments.segmentPaths().isEmpty()) {
                  LOG.atError()
                      .addKeyValue("backup", backupId)
                      .setMessage("No journal segments found for backup")
                      .log();
                  filesCollected.completeExceptionally(
                      new IllegalStateException("Segments must not be empty"));
                } else {
                  LOG.atTrace()
                      .addKeyValue("backup", backupId)
                      .addKeyValue("segments", tailSegments.segmentPaths()::size)
                      .setMessage("Collected journal segments for backup")
                      .log();
                  firstLogPosition = tailSegments.firstAsqn();

                  final Map<String, Path> map =
                      tailSegments.segmentPaths().stream()
                          .collect(
                              Collectors.toMap(
                                  path -> segmentsDirectory.relativize(path).toString(),
                                  Function.identity()));
                  segmentsFileSet = new NamedFileSetImpl(map);
                  filesCollected.complete(null);
                }
              });
    } catch (final Exception e) {
      LOG.atError()
          .addKeyValue("backup", backupId)
          .setCause(e)
          .setMessage("Failed to retrieve journal segments for backup")
          .log();
      filesCollected.completeExceptionally(e);
    }
    return filesCollected;
  }

  @Override
  public Backup createBackup() {
    final Optional<String> snapshotId;

    if (hasSnapshot) {
      snapshotId = Optional.of(reservedSnapshot.getId());
    } else {
      snapshotId = Optional.empty();
    }

    final var backupDescriptor =
        new BackupDescriptorImpl(
            snapshotId,
            firstLogPosition,
            backupDescriptor().checkpointPosition(),
            backupDescriptor().numberOfPartitions(),
            VersionUtil.getVersion(),
            backupDescriptor().checkpointTimestamp(),
            backupDescriptor().checkpointType());
    return new BackupImpl(backupId, backupDescriptor, snapshotFileSet, segmentsFileSet);
  }

  @Override
  public void close() {
    if (snapshotReservation != null) {
      snapshotReservation.release();
      LOG.atTrace()
          .addKeyValue("backup", backupId)
          .addKeyValue("snapshot", reservedSnapshot.getId())
          .setMessage("Released snapshot reservation")
          .log();
    }
  }

  private Either<String, Set<PersistedSnapshot>> findValidSnapshot(
      final Set<PersistedSnapshot> snapshots) {
    final var validSnapshots =
        snapshots.stream()
            .filter(
                s ->
                    s.getMetadata().processedPosition()
                        < backupDescriptor().checkpointPosition()) // &&
            .filter(
                s ->
                    s.getMetadata().lastFollowupEventPosition()
                        < backupDescriptor().checkpointPosition())
            .filter(
                s ->
                    s.getMetadata().maxExportedPosition() < backupDescriptor().checkpointPosition())
            .collect(Collectors.toSet());

    if (validSnapshots.isEmpty()) {
      LOG.atError()
          .addKeyValue("invalidSnapshots", snapshots::size)
          .setMessage("No valid snapshots found for backup")
          .log();
      return Either.left(
          String.format(
              ERROR_MSG_NO_VALID_SNAPSHOT,
              id().checkpointId(),
              snapshots,
              backupDescriptor().checkpointPosition()));
    } else {
      LOG.atTrace()
          .addKeyValue("backup", backupId)
          .addKeyValue("validSnapshots", validSnapshots::size)
          .setMessage("Found valid snapshots for backup")
          .log();
      return Either.right(validSnapshots);
    }
  }

  private void tryReserveAnySnapshot(
      final Iterator<PersistedSnapshot> snapshotIterator, final ActorFuture<Void> future) {
    final var snapshot = snapshotIterator.next();

    LOG.atTrace()
        .addKeyValue("backup", backupId)
        .addKeyValue("snapshot", snapshot.getId())
        .setMessage("Attempting to reserve snapshot")
        .log();
    final ActorFuture<SnapshotReservation> reservationFuture = snapshot.reserve();
    reservationFuture.onComplete(
        (reservation, error) -> {
          if (error != null) {
            if (snapshotIterator.hasNext()) {
              LOG.atDebug()
                  .addKeyValue("backup", backupId)
                  .addKeyValue("snapshot", snapshot.getId())
                  .setCause(error)
                  .setMessage("Failed to reserve snapshot, trying next available snapshot")
                  .log();
              tryReserveAnySnapshot(snapshotIterator, future);
            } else {
              LOG.atError()
                  .addKeyValue("backup", backupId)
                  .addKeyValue("snapshot", snapshot.getId())
                  .setCause(error)
                  .setMessage("Failed to reserve last available snapshot")
                  .log();
              // fail future.
              future.completeExceptionally(
                  String.format(
                      "Attempted to reserve snapshots %s, but no snapshot could be reserved",
                      availableValidSnapshots),
                  error);
            }
          } else {
            // complete
            snapshotReservation = reservation;
            reservedSnapshot = snapshot;
            LOG.atTrace()
                .addKeyValue("backup", backupId)
                .addKeyValue("snapshot", snapshot.getId())
                .setMessage("Reserved snapshot")
                .log();
            future.complete(null);
          }
        });
  }
}
