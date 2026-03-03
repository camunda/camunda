/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes RESET_STATE commands. Applies state mutations (clears all backup runtime state:
 * checkpoint info, backup info, checkpoint metadata, and backup ranges), appends a STATE_RESET
 * follow-up event, and schedules a post-commit task to sync metadata.
 */
public final class CheckpointResetStateProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointResetStateProcessor.class);

  private final CheckpointStateResetApplier stateResetApplier;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;
  private final BackupManager backupManager;

  public CheckpointResetStateProcessor(
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final BackupManager backupManager) {
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
    this.backupManager = backupManager;
    stateResetApplier =
        new CheckpointStateResetApplier(checkpointState, checkpointMetadataState, backupRangeState);
  }

  public ProcessingResult process(
      final TypedRecord<CheckpointRecord> record, final ProcessingResultBuilder resultBuilder) {
    final var checkpointRecord = record.getValue();

    LOG.info("Processing RESET_STATE command");

    // Apply state mutations (clear all backup runtime state)
    stateResetApplier.apply();

    // Append STATE_RESET follow-up event
    resultBuilder.appendRecord(
        record.getKey(),
        checkpointRecord,
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.STATE_RESET));

    // Schedule post-commit task to sync metadata (will write empty metadata to backup store)
    final var checkpoints = checkpointMetadataState.getAllCheckpoints();
    final var ranges = backupRangeState.getAllRanges();
    resultBuilder.appendPostCommitTask(
        () -> {
          backupManager.syncMetadata(checkpoints, ranges);
          return true;
        });

    return resultBuilder.build();
  }
}
