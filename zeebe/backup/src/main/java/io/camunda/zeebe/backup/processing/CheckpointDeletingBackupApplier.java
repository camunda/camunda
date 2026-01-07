/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the DELETING_BACKUP event during replay. This marks that a backup deletion has been
 * initiated and clears the checkpoint/backup state if the deleted backup matches the latest stored
 * checkpoint or backup.
 */
public class CheckpointDeletingBackupApplier {

  private static final Logger LOG = LoggerFactory.getLogger(CheckpointDeletingBackupApplier.class);

  private final CheckpointState checkpointState;

  public CheckpointDeletingBackupApplier(final CheckpointState checkpointState) {
    this.checkpointState = checkpointState;
  }

  public void apply(final CheckpointRecord checkpointRecord) {
    final long backupId = checkpointRecord.getCheckpointId();

    // Clear checkpoint state if the deleted backup matches the latest checkpoint
    if (checkpointState.getLatestCheckpointId() == backupId) {
      LOG.debug("Clearing latest checkpoint info as backup {} is being deleted", backupId);
      checkpointState.clearLatestCheckpointInfo();
    }

    // Clear backup state if the deleted backup matches the latest backup
    if (checkpointState.getLatestBackupId() == backupId) {
      LOG.debug("Clearing latest backup info as backup {} is being deleted", backupId);
      checkpointState.clearLatestBackupInfo();
    }
  }
}
