/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
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
  private final CheckpointBackupDeletedApplier backupDeletedApplier;
  private final BackupManager backupManager;

  /**
   * @param checkpointMetadataState the checkpoint metadata CF state
   * @param backupRangeState the backup ranges CF state
   * @param backupManager the backup manager for async deletion (nullable)
   */
  public CheckpointDeleteBackupProcessor(
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final BackupManager backupManager) {
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupManager = backupManager;
    backupDeletedApplier =
        new CheckpointBackupDeletedApplier(checkpointMetadataState, backupRangeState);
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
      return resultBuilder
          .appendRecord(
              record.getKey(),
              checkpointRecord,
              new RecordMetadata()
                  .recordType(RecordType.COMMAND_REJECTION)
                  .rejectionType(RejectionType.NOT_FOUND)
                  .valueType(ValueType.CHECKPOINT)
                  .intent(record.getIntent()))
          .build();
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

    resultBuilder.appendPostCommitTask(
        () -> {
          backupManager.deleteBackup(checkpointId);
          return true;
        });

    return resultBuilder.build();
  }
}
