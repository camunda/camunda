/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BackupServiceImpl {
  private static final Logger LOG = LoggerFactory.getLogger(BackupServiceImpl.class);
  private final Set<InProgressBackup> backupsInProgress = new HashSet<>();
  private final BackupStore backupStore;
  private final LogStreamWriter logStreamWriter;
  private ConcurrencyControl concurrencyControl;

  BackupServiceImpl(final BackupStore backupStore, final LogStreamWriter logStreamWriter) {
    this.backupStore = backupStore;
    this.logStreamWriter = logStreamWriter;
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
    backupSaved.onSuccess(ignore -> confirmBackup(inProgressBackup));

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
            : Collections.max(availableBackups, BackupStatusCode.BY_STATUS).statusCode();
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
      case DOES_NOT_EXIST ->
          inProgressBackup
              .findValidSnapshot()
              .andThen(inProgressBackup::findSegmentFiles, concurrencyControl)
              .andThen(ok -> inProgressBackup.reserveSnapshot(), concurrencyControl)
              .andThen(ok -> inProgressBackup.findSnapshotFiles(), concurrencyControl)
              .onComplete(
                  (result, error) -> {
                    if (error != null) {
                      failBackup(inProgressBackup, backupSaved, error);
                    } else {
                      saveBackup(inProgressBackup, backupSaved);
                    }
                  });
      default -> LOG.warn("Invalid case on BackupStatus {}", existingBackupStatus);
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

  private void confirmBackup(final InProgressBackup inProgressBackup) {
    final var checkpointId = inProgressBackup.checkpointId();
    final var checkpointPosition = inProgressBackup.checkpointPosition();
    final var confirmationWritten =
        logStreamWriter.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.CHECKPOINT)
                    .intent(CheckpointIntent.CONFIRM_BACKUP),
                new CheckpointRecord()
                    .setCheckpointId(checkpointId)
                    .setCheckpointPosition(checkpointPosition)));
    switch (confirmationWritten) {
      case Either.Left(final var error) ->
          LOG.warn(
              "Could not confirm backup {} at position {}: {}",
              checkpointId,
              checkpointPosition,
              error);
      case final Either.Right<WriteFailure, Long> ignored ->
          LOG.debug("Confirmed backup {} at position {}", checkpointId, ignored);
    }
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
    final ActorFuture<Optional<BackupStatus>> future = executor.createFuture();
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
      final int partitionId, final long lastCheckpointId, final ConcurrencyControl executor) {
    if (lastCheckpointId != CheckpointState.NO_CHECKPOINT) {
      executor.run(
          () ->
              backupStore
                  .list(
                      new BackupIdentifierWildcardImpl(
                          Optional.empty(),
                          Optional.of(partitionId),
                          Optional.of(lastCheckpointId)))
                  .thenAccept(backups -> backups.forEach(this::failInProgressBackup))
                  .exceptionally(
                      failure -> {
                        LOG.warn("Failed to list backups that should be marked as failed", failure);
                        return null;
                      }));
    }
  }

  private void failInProgressBackup(final BackupStatus backupStatus) {
    if (backupStatus.statusCode() != BackupStatusCode.IN_PROGRESS) {
      return;
    }

    LOG.info(
        "The backup {} initiated by previous leader is still in progress. Marking it as failed.",
        backupStatus.id());

    backupStore
        .markFailed(backupStatus.id(), "Backup is cancelled due to leader change.")
        .thenAccept(ignore -> LOG.trace("Marked backup {} as failed.", backupStatus.id()))
        .exceptionally(
            failed -> {
              LOG.warn("Failed to mark backup {} as failed", backupStatus.id(), failed);
              return null;
            });
  }

  ActorFuture<Void> deleteBackup(
      final int partitionId, final long checkpointId, final ConcurrencyControl executor) {
    final ActorFuture<Void> deleteCompleted = executor.createFuture();
    executor.run(
        () ->
            backupStore
                .list(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.of(partitionId), Optional.of(checkpointId)))
                .thenCompose(
                    backups ->
                        CompletableFuture.allOf(
                            backups.stream()
                                .map(this::deleteBackupIfExists)
                                .toArray(CompletableFuture[]::new)))
                .thenAccept(ignore -> deleteCompleted.complete(null))
                .exceptionally(
                    error -> {
                      deleteCompleted.completeExceptionally(error);
                      return null;
                    }));
    return deleteCompleted;
  }

  private CompletableFuture<Void> deleteBackupIfExists(final BackupStatus backupStatus) {
    LOG.debug("Deleting backup {}", backupStatus.id());
    if (backupStatus.statusCode() == BackupStatusCode.IN_PROGRESS) {
      // In progress backups cannot be deleted. So first mark it as failed
      return backupStore
          .markFailed(backupStatus.id(), "The backup is going to be deleted.")
          .thenCompose(ignore -> backupStore.delete(backupStatus.id()));
    } else {
      return backupStore.delete(backupStatus.id());
    }
  }

  ActorFuture<Collection<BackupStatus>> listBackups(
      final int partitionId, final String pattern, final ConcurrencyControl executor) {
    final ActorFuture<Collection<BackupStatus>> availableBackupsFuture = executor.createFuture();
    executor.run(
        () ->
            backupStore
                .list(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.of(partitionId), Optional.empty()))
                .thenAccept(
                    statuses ->
                        availableBackupsFuture.complete(
                            statuses.stream()
                                .filter(status -> matchPattern(status, pattern))
                                .toList()))
                .exceptionally(
                    error -> {
                      availableBackupsFuture.completeExceptionally(error);
                      return null;
                    }));
    return availableBackupsFuture;
  }

  private boolean matchPattern(final BackupStatus status, final String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      return true;
    }
    if (pattern.endsWith("*")) {
      final var prefix = pattern.substring(0, pattern.length() - 1);
      return Long.toString(status.id().checkpointId()).startsWith(prefix);
    } else {
      return Long.toString(status.id().checkpointId()).equals(pattern);
    }
  }

  void createFailedBackup(
      final BackupIdentifier backupId,
      final long checkpointPosition,
      final String failureReason,
      final ConcurrencyControl executor) {
    executor.run(
        () -> {
          LOG.debug(
              "Creating failed backup {} at position {} due to: {}",
              backupId,
              checkpointPosition,
              failureReason);

          // Directly mark the backup as failed - this will create the backup entry with failed
          // status
          backupStore
              .markFailed(backupId, failureReason)
              .thenAccept(ignore -> LOG.trace("Successfully created failed backup {}", backupId))
              .exceptionally(
                  error -> {
                    LOG.debug("Failed to create failed backup {}", backupId, error);
                    return null;
                  });
        });
  }
}
