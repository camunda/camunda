/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BackupServiceImpl {
  private static final Logger LOG = LoggerFactory.getLogger(BackupServiceImpl.class);
  private final Set<InProgressBackup> backupsInProgress = new HashSet<>();
  private final BackupStore backupStore;
  private ConcurrencyControl concurrencyControl;

  BackupServiceImpl(final BackupStore backupStore) {
    this.backupStore = backupStore;
  }

  void close() {
    backupsInProgress.forEach(InProgressBackup::close);
  }

  ActorFuture<Void> takeBackup(
      final InProgressBackup inProgressBackup, final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = concurrencyControl;

    backupsInProgress.add(inProgressBackup);

    final var checkCurrentBackup =
        backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(),
                Optional.of(inProgressBackup.id().partitionId()),
                Optional.of(inProgressBackup.checkpointId())));

    final ActorFuture<Void> backupSaved = concurrencyControl.createFuture();

    checkCurrentBackup.whenCompleteAsync(
        (availableBackups, error) -> {
          if (error != null) {
            backupSaved.completeExceptionally(error);
          } else {
            takeBackupIfDoesNotExist(
                availableBackups, inProgressBackup, concurrencyControl, backupSaved);
          }
        },
        concurrencyControl::run);

    backupSaved.onComplete((ignore, error) -> closeInProgressBackup(inProgressBackup));
    return backupSaved;
  }

  private void takeBackupIfDoesNotExist(
      final Collection<BackupStatus> availableBackups,
      final InProgressBackup inProgressBackup,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<Void> backupSaved) {

    final BackupStatusCode existingBackupStatus =
        availableBackups.isEmpty()
            ? BackupStatusCode.DOES_NOT_EXIST
            : Collections.max(availableBackups, Comparator.comparing(BackupStatus::statusCode))
                .statusCode();
    switch (existingBackupStatus) {
      case COMPLETED -> {
        LOG.debug("Backup {} is already completed, will not take a new one", inProgressBackup.id());
        backupSaved.complete(null);
      }
      case FAILED, IN_PROGRESS -> {
        LOG.error(
            "Backup {} already exists with status {}, will not take a new one",
            inProgressBackup.id(),
            existingBackupStatus);
        backupSaved.completeExceptionally(
            new BackupAlreadyExistsException(inProgressBackup.id(), existingBackupStatus));
      }
      default -> {
        final ActorFuture<Void> snapshotFound = concurrencyControl.createFuture();
        final ActorFuture<Void> snapshotReserved = concurrencyControl.createFuture();
        final ActorFuture<Void> snapshotFilesCollected = concurrencyControl.createFuture();
        final ActorFuture<Void> segmentFilesCollected = inProgressBackup.findSegmentFiles();

        segmentFilesCollected.onComplete(
            proceed(
                snapshotFound::completeExceptionally,
                () -> inProgressBackup.findValidSnapshot().onComplete(snapshotFound)));

        snapshotFound.onComplete(
            proceed(
                snapshotReserved::completeExceptionally,
                () -> inProgressBackup.reserveSnapshot().onComplete(snapshotReserved)));

        snapshotReserved.onComplete(
            proceed(
                snapshotFilesCollected::completeExceptionally,
                () -> inProgressBackup.findSnapshotFiles().onComplete(snapshotFilesCollected)));

        snapshotFilesCollected.onComplete(
            proceed(
                error -> failBackup(inProgressBackup, backupSaved, error),
                () -> saveBackup(inProgressBackup, backupSaved)));
      }
    }
  }

  private void saveBackup(
      final InProgressBackup inProgressBackup, final ActorFuture<Void> backupSaved) {
    saveBackup(inProgressBackup)
        .onComplete(
            proceed(
                error -> failBackup(inProgressBackup, backupSaved, error),
                () -> backupSaved.complete(null)));
  }

  private ActorFuture<Void> saveBackup(final InProgressBackup inProgressBackup) {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    final var backup = inProgressBackup.createBackup();
    backupStore
        .save(backup)
        .whenComplete(
            (ignore, error) -> {
              if (error == null) {
                future.complete(null);
              } else {
                future.completeExceptionally("Failed to save backup", error);
              }
            });
    return future;
  }

  private void failBackup(
      final InProgressBackup inProgressBackup,
      final ActorFuture<Void> backupSaved,
      final Throwable error) {
    backupSaved.completeExceptionally(error);
    backupStore.markFailed(inProgressBackup.id(), error.getMessage());
  }

  private void closeInProgressBackup(final InProgressBackup inProgressBackup) {
    backupsInProgress.remove(inProgressBackup);
    inProgressBackup.close();
  }

  private BiConsumer<Void, Throwable> proceed(
      final Consumer<Throwable> onError, final Runnable nextStep) {
    return (ignore, error) -> {
      if (error != null) {
        onError.accept(error);
      } else {
        nextStep.run();
      }
    };
  }

  ActorFuture<Optional<BackupStatus>> getBackupStatus(
      final int partitionId, final long checkpointId, final ConcurrencyControl executor) {
    final var future = new CompletableActorFuture<Optional<BackupStatus>>();
    executor.run(
        () ->
            backupStore
                .list(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.of(partitionId), Optional.of(checkpointId)))
                .whenComplete(
                    (backupStatuses, throwable) -> {
                      if (throwable != null) {
                        future.completeExceptionally(throwable);
                      } else {
                        future.complete(backupStatuses.stream().max(BackupStatusCode.BY_STATUS));
                      }
                    }));
    return future;
  }

  void failInProgressBackups(
      final int partitionId,
      final long lastCheckpointId,
      final Collection<Integer> brokers,
      final ConcurrencyControl executor) {
    if (lastCheckpointId != CheckpointState.NO_CHECKPOINT) {
      executor.run(
          () -> {
            final var backupIds =
                brokers.stream()
                    .map(b -> new BackupIdentifierImpl(b, partitionId, lastCheckpointId))
                    .toList();
            // Fail backups initiated by previous leaders
            backupIds.forEach(this::failInProgressBackup);
          });
    }
  }

  private void failInProgressBackup(final BackupIdentifier backupId) {
    backupStore
        .getStatus(backupId)
        .thenAccept(
            status -> {
              if (status.statusCode() == BackupStatusCode.IN_PROGRESS) {
                LOG.info(
                    "The backup {} initiated by previous leader is still in progress. Marking it as failed.",
                    backupId);
                backupStore
                    .markFailed(backupId, "Backup is cancelled due to leader change.")
                    .thenAccept(ignore -> LOG.trace("Marked backup {} as failed.", backupId))
                    .exceptionally(
                        failed -> {
                          LOG.warn("Failed to mark backup {} as failed", backupId, failed);
                          return null;
                        });
              }
            })
        .exceptionally(
            error -> {
              LOG.warn("Failed to retrieve status of backup {}", backupId);
              return null;
            });
  }
}
