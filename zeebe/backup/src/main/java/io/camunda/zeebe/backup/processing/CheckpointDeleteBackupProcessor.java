/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.management.BackupMetadataSyncer;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes DELETE_BACKUP commands. Validates the checkpoint exists, applies state mutations
 * (removes checkpoint from CHECKPOINTS CF, updates BACKUP_RANGES CF), appends a BACKUP_DELETED
 * follow-up event, and schedules async side-effects for backup store deletion and JSON sync.
 */
public final class CheckpointDeleteBackupProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointDeleteBackupProcessor.class);

  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;
  private final CheckpointBackupDeletedApplier backupDeletedApplier;
  private final BackupStore backupStore;
  private final BackupMetadataSyncer syncer;
  private final int partitionId;

  /**
   * @param checkpointMetadataState the checkpoint metadata CF state
   * @param backupRangeState the backup ranges CF state
   * @param checkpointState the legacy 2-entry checkpoint state
   * @param backupStore the backup store for async deletion (nullable)
   * @param syncer the metadata syncer for JSON sync (nullable)
   * @param partitionId the partition ID
   */
  public CheckpointDeleteBackupProcessor(
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final CheckpointState checkpointState,
      final BackupStore backupStore,
      final BackupMetadataSyncer syncer,
      final int partitionId) {
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
    this.backupStore = backupStore;
    this.syncer = syncer;
    this.partitionId = partitionId;
    backupDeletedApplier =
        new CheckpointBackupDeletedApplier(
            checkpointMetadataState, backupRangeState, checkpointState);
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();
    final var checkpointId = checkpointRecord.getCheckpointId();

    // Validate that the checkpoint exists
    final var metadata = checkpointMetadataState.getCheckpoint(checkpointId);
    if (metadata == null) {
      LOG.debug(
          "Rejecting DELETE_BACKUP for checkpoint {} — checkpoint not found in state",
          checkpointId);
      resultBuilder.appendRecord(
          record.getKey(),
          checkpointRecord,
          new RecordMetadata()
              .recordType(RecordType.COMMAND_REJECTION)
              .valueType(ValueType.CHECKPOINT)
              .intent(record.getIntent())
              .rejectionType(RejectionType.NOT_FOUND)
              .rejectionReason(
                  "Expected to delete backup for checkpoint "
                      + checkpointId
                      + ", but no such checkpoint exists"));
      return resultBuilder.build();
    }

    LOG.debug("Deleting backup for checkpoint {}", checkpointId);

    // Apply state mutations (remove checkpoint + update ranges)
    backupDeletedApplier.apply(checkpointRecord);

    // Append BACKUP_DELETED follow-up event
    resultBuilder.appendRecord(
        record.getKey(),
        checkpointRecord,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.BACKUP_DELETED));

    // Schedule async side-effects as post-commit tasks
    if (backupStore != null) {
      resultBuilder.appendPostCommitTask(
          () -> {
            deleteFromBackupStore(checkpointId);
            return true;
          });
    }

    if (syncer != null) {
      resultBuilder.appendPostCommitTask(
          () -> {
            syncer.sync(partitionId, checkpointMetadataState, backupRangeState);
            return true;
          });
    }

    return resultBuilder.build();
  }

  /**
   * Deletes all backup copies for the given checkpoint from the backup store. Lists all backups
   * matching the checkpoint ID (across all nodeIds), marks any in-progress ones as failed first,
   * then deletes them.
   */
  private void deleteFromBackupStore(final long checkpointId) {
    final var pattern =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.of(partitionId), CheckpointPattern.of(checkpointId));

    backupStore
        .list(pattern)
        .thenCompose(
            backups ->
                CompletableFuture.allOf(
                    backups.stream()
                        .map(
                            backup -> {
                              final CompletableFuture<Void> preparationStep;
                              if (backup.statusCode() == BackupStatusCode.IN_PROGRESS) {
                                preparationStep =
                                    backupStore
                                        .markFailed(backup.id(), "The backup is being deleted.")
                                        .thenApply(ignored -> null);
                              } else {
                                preparationStep = CompletableFuture.completedFuture(null);
                              }
                              return preparationStep.thenCompose(
                                  ignored -> backupStore.delete(backup.id()));
                            })
                        .toArray(CompletableFuture[]::new)))
        .exceptionally(
            error -> {
              LOG.warn(
                  "Failed to delete backup for checkpoint {} from backup store",
                  checkpointId,
                  error);
              return null;
            });
  }
}
