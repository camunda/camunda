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

public class CheckpointBackupConfirmedApplier {
  private final CheckpointState checkpointState;

  public CheckpointBackupConfirmedApplier(final CheckpointState checkpointState) {
    this.checkpointState = checkpointState;
  }

  public void apply(final CheckpointRecord checkpointRecord, final long checkpointTimestamp) {
    checkpointState.setLatestBackupInfo(
        checkpointRecord.getCheckpointId(),
        checkpointRecord.getCheckpointPosition(),
        checkpointTimestamp,
        checkpointRecord.getCheckpointType(),
        checkpointRecord.getFirstLogPosition());
  }
}
