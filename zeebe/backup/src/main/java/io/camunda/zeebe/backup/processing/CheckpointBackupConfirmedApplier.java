/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;

public class CheckpointBackupConfirmedApplier {
  private final CheckpointState checkpointState;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;
  private final boolean trackBackupMetadata;

  public CheckpointBackupConfirmedApplier(
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final boolean trackBackupMetadata) {
    this.checkpointState = checkpointState;
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
    this.trackBackupMetadata = trackBackupMetadata;
  }

  public void apply(final CheckpointRecord checkpointRecord, final long checkpointTimestamp) {
    final var checkpointId = checkpointRecord.getCheckpointId();
    final var firstLogPosition = checkpointRecord.getFirstLogPosition();

    // Read pre-update state for contiguity check
    final var latestBackupId = checkpointState.getLatestBackupId();
    final var latestBackupPosition = checkpointState.getLatestBackupPosition();

    if (trackBackupMetadata) {
      updateRangeState(latestBackupId, firstLogPosition, latestBackupPosition, checkpointId);
    }

    // Update the existing checkpoint state (2-entry DEFAULT CF)
    final var checkpointPosition = checkpointRecord.getCheckpointPosition();
    final var checkpointType = checkpointRecord.getCheckpointType();
    checkpointState.setLatestBackupInfo(
        checkpointId, checkpointPosition, checkpointTimestamp, checkpointType, firstLogPosition);
    if (trackBackupMetadata) {
      checkpointMetadataState.addBackupCheckpoint(
          checkpointId, checkpointPosition, checkpointTimestamp, checkpointType, firstLogPosition);
    }
  }

  private void updateRangeState(
      final long latestBackupId,
      final long firstLogPosition,
      final long latestBackupPosition,
      final long checkpointId) {
    // Update range state: extend existing range or start a new one
    if (latestBackupId != CheckpointState.NO_CHECKPOINT
        && firstLogPosition <= latestBackupPosition + 1) {
      // Contiguous with previous backup — find and extend the range that contains the latest
      // backup
      final var existingRange = backupRangeState.findRangeContaining(latestBackupId);
      if (existingRange.isPresent()) {
        backupRangeState.updateRangeEnd(existingRange.get().start(), checkpointId);
      } else {
        // Range not found — start a new one
        backupRangeState.startNewRange(checkpointId);
      }
    } else {
      backupRangeState.startNewRange(checkpointId);
    }
  }
}
