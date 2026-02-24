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
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState.BackupRange;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies BACKUP_DELETED events during both processing and replay. Performs the state mutations:
 * removes the checkpoint from the CHECKPOINTS CF and maintains the BACKUP_RANGES CF.
 */
public final class CheckpointBackupDeletedApplier {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointBackupDeletedApplier.class);

  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;
  private final CheckpointState checkpointState;

  public CheckpointBackupDeletedApplier(
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final CheckpointState checkpointState) {
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
    this.checkpointState = checkpointState;
  }

  /**
   * Applies the deletion of a backup checkpoint. Removes the checkpoint entry from the CHECKPOINTS
   * CF and updates the BACKUP_RANGES CF according to the deletion scenario.
   *
   * @param checkpointRecord the record containing the checkpoint ID to delete
   */
  public void apply(final CheckpointRecord checkpointRecord) {
    final var checkpointId = checkpointRecord.getCheckpointId();
    // Remove the checkpoint from the CHECKPOINTS CF
    checkpointMetadataState.removeCheckpoint(checkpointId);

    // Update range state based on deletion scenario
    final var range = backupRangeState.findRangeContaining(checkpointId);
    if (range.isPresent()) {
      updateRangeOnDeletion(checkpointId, range.get());
      removeUncoveredMarkers();
    } else {
      LOG.debug("Checkpoint {} is not in any range, skipping range maintenance", checkpointId);
    }

    // Remove the checkpoint from latest checkpoint info
    updateLatestCheckpointInfo(checkpointId);
  }

  private void removeUncoveredMarkers() {
    final var firstRange = backupRangeState.getFirstRange();
    if (firstRange.isEmpty()) {
      // No ranges exist, keep any existing checkpoints because they might get included in the next
      // backup.
      return;
    }
    final var firstLogPosition =
        checkpointMetadataState
            .getCheckpoint(firstRange.orElseThrow().start())
            .getFirstLogPosition();
    checkpointMetadataState.removeCheckpointsUntil(firstLogPosition);
  }

  /**
   * Updates the DbCheckpointState when the deleted checkpoint is the latest backup. Rolls back to
   * the predecessor backup if one exists or clears the latest backup info entirely.
   */
  private void updateLatestCheckpointInfo(final long checkpointId) {
    if (checkpointState.getLatestBackupId() != checkpointId) {
      return;
    }

    final var predecessor = checkpointMetadataState.findPredecessorBackupCheckpoint(checkpointId);
    if (predecessor.isPresent()) {
      final var predecessorMetadata = checkpointMetadataState.getCheckpoint(predecessor.get());
      if (predecessorMetadata != null) {
        checkpointState.setLatestBackupInfo(
            predecessor.get(),
            predecessorMetadata.getCheckpointPosition(),
            predecessorMetadata.getCheckpointTimestamp(),
            predecessorMetadata.getCheckpointType(),
            predecessorMetadata.getFirstLogPosition());
        LOG.debug(
            "Rolled back latest backup from {} to predecessor {}", checkpointId, predecessor.get());
      } else {
        LOG.warn(
            "Predecessor checkpoint {} found but metadata missing, clearing latest backup info",
            predecessor.get());
        checkpointState.clearLatestBackupInfo();
      }
    } else {
      LOG.debug(
          "No predecessor backup found for deleted checkpoint {}, clearing latest backup info",
          checkpointId);
      checkpointState.clearLatestBackupInfo();
    }
  }

  private void updateRangeOnDeletion(final long checkpointId, final BackupRange range) {
    final var isStart = range.start() == checkpointId;
    final var isEnd = range.end() == checkpointId;

    if (isStart && isEnd) {
      // Only checkpoint in the range — delete the entire range
      backupRangeState.deleteRange(range.start());
      LOG.debug("Deleted single-entry range [{}, {}]", range.start(), range.end());
    } else if (isStart) {
      updateRangeStart(checkpointId, range);
    } else if (isEnd) {
      updateRangeEnd(checkpointId, range);
    } else {
      splitRange(checkpointId, range);
    }
  }

  private void updateRangeStart(final long checkpointId, final BackupRange range) {
    // Deleting from the start — advance start to successor
    final var successor = checkpointMetadataState.findSuccessorBackupCheckpoint(checkpointId);
    if (successor.isPresent()) {
      backupRangeState.updateRangeStart(range.start(), successor.get());
      LOG.debug(
          "Advanced range start from {} to {} (range end: {})",
          range.start(),
          successor.get(),
          range.end());
    } else {
      // No successor found — should not happen for a range with start != end
      LOG.warn(
          "No successor found for start checkpoint {} in range [{}, {}], deleting range",
          checkpointId,
          range.start(),
          range.end());
      backupRangeState.deleteRange(range.start());
    }
  }

  private void updateRangeEnd(final long checkpointId, final BackupRange range) {
    // Deleting from the end — shrink end to predecessor
    final var predecessor = checkpointMetadataState.findPredecessorBackupCheckpoint(checkpointId);
    if (predecessor.isPresent()) {
      backupRangeState.updateRangeEnd(range.start(), predecessor.get());
      LOG.debug(
          "Shrunk range end from {} to {} (range start: {})",
          range.end(),
          predecessor.get(),
          range.start());
    } else {
      // No predecessor found — should not happen for a range with start != end
      LOG.warn(
          "No predecessor found for end checkpoint {} in range [{}, {}], deleting range",
          checkpointId,
          range.start(),
          range.end());
      backupRangeState.deleteRange(range.start());
    }
  }

  private void splitRange(final long checkpointId, final BackupRange range) {
    // Deleting from the middle — split the range
    final var predecessor = checkpointMetadataState.findPredecessorBackupCheckpoint(checkpointId);
    final var successor = checkpointMetadataState.findSuccessorBackupCheckpoint(checkpointId);
    if (predecessor.isPresent() && successor.isPresent()) {
      backupRangeState.splitRange(range.start(), range.end(), predecessor.get(), successor.get());
      LOG.debug(
          "Split range [{}, {}] into [{}, {}] and [{}, {}]",
          range.start(),
          range.end(),
          range.start(),
          predecessor.get(),
          successor.get(),
          range.end());
    } else {
      LOG.warn(
          "Could not find predecessor/successor for interior checkpoint {} in range [{}, {}]",
          checkpointId,
          range.start(),
          range.end());
    }
  }
}
