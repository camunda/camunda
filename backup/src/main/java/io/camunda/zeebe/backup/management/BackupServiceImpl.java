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
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Collection;
import java.util.HashSet;
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

    final ActorFuture<Void> snapshotFound = concurrencyControl.createFuture();
    final ActorFuture<Void> snapshotReserved = concurrencyControl.createFuture();
    final ActorFuture<Void> snapshotFilesCollected = concurrencyControl.createFuture();
    final ActorFuture<Void> backupSaved = concurrencyControl.createFuture();

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

    return backupSaved;
  }

  private void saveBackup(
      final InProgressBackup inProgressBackup, final ActorFuture<Void> backupSaved) {
    saveBackup(inProgressBackup)
        .onComplete(
            proceed(
                error -> failBackup(inProgressBackup, backupSaved, error),
                () -> {
                  backupSaved.complete(null);
                  closeInProgressBackup(inProgressBackup);
                }));
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
    backupStore.markFailed(inProgressBackup.id());
    closeInProgressBackup(inProgressBackup);
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

  ActorFuture<BackupStatus> getBackupStatus(
      final BackupIdentifier backupId, final ConcurrencyControl executor) {
    final var future = new CompletableActorFuture<BackupStatus>();
    executor.run(
        () ->
            backupStore
                .getStatus(backupId)
                .whenComplete(
                    (status, error) -> {
                      if (error == null) {
                        future.complete(status);
                      } else {
                        future.completeExceptionally(error);
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
                LOG.debug(
                    "The backup {} initiated by previous leader is still in progress. Marking it as failed.",
                    backupId);
                backupStore
                    .markFailed(backupId)
                    .thenAccept(ignore -> LOG.trace("Marked backup {} as failed.", backupId))
                    .exceptionally(
                        failed -> {
                          LOG.debug("Failed to mark backup {} as failed", backupId, failed);
                          return null;
                        });
              }
            })
        .exceptionally(
            error -> {
              LOG.debug("Failed to retrieve status of backup {}", backupId);
              return null;
            });
  }
}
