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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies STATE_CLEARED events during both processing and replay. Clears all backup runtime state:
 * latest checkpoint info, latest backup info, all checkpoint metadata entries, and all backup range
 * entries.
 */
public final class CheckpointStateClearedApplier {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointStateClearedApplier.class);

  private final CheckpointState checkpointState;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;

  public CheckpointStateClearedApplier(
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState) {
    this.checkpointState = checkpointState;
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
  }

  /** Applies the state clear by clearing all backup runtime state. */
  public void apply() {
    LOG.info(
        "Clearing all backup runtime state. "
            + "Current state: latestCheckpointId={}, latestCheckpointPosition={}, "
            + "latestBackupId={}, latestBackupPosition={}",
        checkpointState.getLatestCheckpointId(),
        checkpointState.getLatestCheckpointPosition(),
        checkpointState.getLatestBackupId(),
        checkpointState.getLatestBackupPosition());

    // Clear latest checkpoint and backup info from the DEFAULT column family
    checkpointState.clearLatestCheckpointInfo();
    checkpointState.clearLatestBackupInfo();

    // Clear all checkpoint metadata entries from the CHECKPOINTS column family
    checkpointMetadataState.clearAll();

    // Clear all backup range entries from the BACKUP_RANGES column family
    backupRangeState.clearAll();

    LOG.info("Backup runtime state has been cleared");
  }
}
