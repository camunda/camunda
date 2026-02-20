/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRangeStatus;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.processing.state.CheckpointMetadataValue;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
  private final LogStreamWriter logStreamWriter;
  private final DbBackupRangeState backupRangeState;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private ConcurrencyControl concurrencyControl;

  BackupServiceImpl(
      final BackupStore backupStore,
      final LogStreamWriter logStreamWriter,
      final DbBackupRangeState backupRangeState,
      final DbCheckpointMetadataState checkpointMetadataState) {
    this.backupStore = backupStore;
    this.logStreamWriter = logStreamWriter;
    this.backupRangeState = backupRangeState;
    this.checkpointMetadataState = checkpointMetadataState;
  }

  void close() {
    LOG.atDebug()
        .addKeyValue("inProgress", backupsInProgress.size())
        .setMessage("Closing backup service")
        .log();
    backupsInProgress.forEach(InProgressBackup::close);
  }

  ActorFuture<Void> takeBackup(
      final InProgressBackup inProgressBackup, final ConcurrencyControl concurrencyControl) {
    LOG.atInfo().addKeyValue("backup", inProgressBackup.id()).setMessage("Taking backup").log();

    this.concurrencyControl = concurrencyControl;

    backupsInProgress.add(inProgressBackup);
    LOG.atDebug()
        .addKeyValue("backup", inProgressBackup.id())
        .setMessage("Querying existing backup status")
        .log();

    final var checkCurrentBackup =
        backupStore.list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(),
                Optional.of(inProgressBackup.id().partitionId()),
                CheckpointPattern.of(inProgressBackup.id().checkpointId())));

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
        LOG.atDebug()
            .addKeyValue("backup", inProgressBackup.id())
            .setMessage("Backup is already completed, will not take a new one")
            .log();
        backupSaved.complete(null);
      }
      case FAILED, IN_PROGRESS -> {
        LOG.atWarn()
            .addKeyValue("backup", inProgressBackup.id())
            .addArgument(existingBackupStatus)
            .setMessage("Backup already exists with status {}, will not take a new one")
            .log();
        backupSaved.completeExceptionally(
            new BackupAlreadyExistsException(inProgressBackup.id(), existingBackupStatus));
      }
      case DOES_NOT_EXIST -> {
        LOG.atDebug()
            .addKeyValue("backup", inProgressBackup.id())
            .setMessage("No existing backup found, taking a new backup")
            .log();
        inProgressBackup
            .findValidSnapshot()
            .andThen(ok -> inProgressBackup.reserveSnapshot(), concurrencyControl)
            .andThen(inProgressBackup::findSegmentFiles, concurrencyControl)
            .andThen(ok -> inProgressBackup.findSnapshotFiles(), concurrencyControl)
            .onComplete(
                (result, error) -> {
                  if (error != null) {
                    failBackup(inProgressBackup, backupSaved, error);
                  } else {
                    saveBackup(inProgressBackup, backupSaved);
                  }
                },
                concurrencyControl);
      }
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
    LOG.atDebug().addKeyValue("backup", inProgressBackup.id()).setMessage("Saving backup").log();
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
    LOG.atWarn()
        .addKeyValue("backup", inProgressBackup.id())
        .setCause(error)
        .setMessage("Marking backup as failed")
        .log();
    backupSaved.completeExceptionally(error);
    backupStore.markFailed(inProgressBackup.id(), error.getMessage());
  }

  private void closeInProgressBackup(final InProgressBackup inProgressBackup) {
    backupsInProgress.remove(inProgressBackup);
    inProgressBackup.close();
  }

  private void confirmBackup(final InProgressBackup inProgressBackup) {
    final var checkpointId = inProgressBackup.id().checkpointId();
    LOG.atDebug()
        .addKeyValue("backup", inProgressBackup.id())
        .log("Confirming backup for checkpoint");
    final var checkpointPosition = inProgressBackup.backupDescriptor().checkpointPosition();
    final var checkpointType = inProgressBackup.backupDescriptor().checkpointType();
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
                    .setCheckpointPosition(checkpointPosition)
                    .setCheckpointType(checkpointType)
                    .setFirstLogPosition(inProgressBackup.getFirstLogPosition().orElse(-1L))
                    .setNumberOfPartitions(
                        inProgressBackup.backupDescriptor().numberOfPartitions())));
    switch (confirmationWritten) {
      case Either.Left(final var error) ->
          LOG.atWarn()
              .addKeyValue("backup", inProgressBackup.id())
              .addKeyValue("error", error)
              .setMessage("Could not confirm backup")
              .log();
      case final Either.Right<WriteFailure, Long> position ->
          LOG.atDebug()
              .addKeyValue("backup", inProgressBackup.id())
              .addKeyValue("position", position.value())
              .setMessage("Confirmed backup")
              .log();
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
    final var pattern =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(checkpointId));
    LOG.atDebug().addKeyValue("pattern", pattern).setMessage("Querying backup status").log();
    executor.run(
        () ->
            backupStore
                .list(pattern)
                .whenComplete(
                    (backupStatuses, throwable) -> {
                      if (throwable != null) {
                        LOG.atError()
                            .addKeyValue("pattern", pattern)
                            .setCause(throwable)
                            .setMessage("Failed to query backup status")
                            .log();
                        future.completeExceptionally(throwable);
                      } else {
                        LOG.atTrace()
                            .addKeyValue("pattern", pattern)
                            .addKeyValue("found", backupStatuses.size())
                            .setMessage("Queried backup status")
                            .log();
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
                          CheckpointPattern.of(lastCheckpointId)))
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
    LOG.atDebug()
        .addKeyValue("partitionId", partitionId)
        .addKeyValue("checkpointId", checkpointId)
        .setMessage("Writing DELETE_BACKUP command to log")
        .log();
    final var deleteWritten =
        logStreamWriter.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.CHECKPOINT)
                    .intent(CheckpointIntent.DELETE_BACKUP),
                new CheckpointRecord().setCheckpointId(checkpointId)));
    switch (deleteWritten) {
      case Either.Left(final var error) -> {
        LOG.atWarn()
            .addKeyValue("checkpointId", checkpointId)
            .addKeyValue("error", error)
            .setMessage("Could not write DELETE_BACKUP command")
            .log();
        deleteCompleted.completeExceptionally(
            new RuntimeException("Failed to write DELETE_BACKUP command: " + error));
      }
      case final Either.Right<WriteFailure, Long> position -> {
        LOG.atDebug()
            .addKeyValue("checkpointId", checkpointId)
            .addKeyValue("position", position.value())
            .setMessage("DELETE_BACKUP command written to log")
            .log();
        deleteCompleted.complete(null);
      }
    }
    return deleteCompleted;
  }

  ActorFuture<Collection<BackupStatus>> listBackups(
      final int partitionId, final String pattern, final ConcurrencyControl executor) {
    final ActorFuture<Collection<BackupStatus>> availableBackupsFuture = executor.createFuture();
    executor.run(
        () ->
            backupStore
                .list(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(pattern)))
                .thenAccept(availableBackupsFuture::complete)
                .exceptionally(
                    error -> {
                      availableBackupsFuture.completeExceptionally(error);
                      return null;
                    }));
    return availableBackupsFuture;
  }

  ActorFuture<Collection<BackupRangeStatus>> getBackupRangeStatus(
      final int partitionId, final ConcurrencyControl executor) {
    LOG.atDebug().setMessage("Listing backup ranges").log();
    final ActorFuture<Collection<BackupRangeStatus>> future = executor.createFuture();
    executor.run(
        () -> {
          try {
            final var ranges = backupRangeState.getAllRanges();
            final var result = new ArrayList<BackupRangeStatus>(ranges.size());
            for (final var range : ranges) {
              final var firstMeta = checkpointMetadataState.getCheckpoint(range.start());
              final var lastMeta = checkpointMetadataState.getCheckpoint(range.end());
              if (firstMeta == null || lastMeta == null) {
                LOG.atWarn()
                    .addKeyValue("rangeStart", range.start())
                    .addKeyValue("rangeEnd", range.end())
                    .addKeyValue("firstMetaPresent", firstMeta != null)
                    .addKeyValue("lastMetaPresent", lastMeta != null)
                    .setMessage("Checkpoint metadata missing for range boundary, skipping range")
                    .log();
                continue;
              }
              final var first = toCheckpointInfo(range.start(), firstMeta);
              final var last = toCheckpointInfo(range.end(), lastMeta);
              result.add(new BackupRangeStatus.Complete(first, last));
            }
            future.complete(result);
          } catch (final Exception e) {
            LOG.atError()
                .setMessage("Failed to read backup ranges from column families")
                .setCause(e)
                .log();
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  private static BackupRangeStatus.CheckpointInfo toCheckpointInfo(
      final long checkpointId, final CheckpointMetadataValue meta) {
    return new BackupRangeStatus.CheckpointInfo(
        checkpointId,
        meta.getCheckpointPosition(),
        meta.getCheckpointTimestamp(),
        meta.getCheckpointType(),
        meta.getFirstLogPosition());
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
