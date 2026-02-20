/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import java.util.Set;

public final class CheckpointCreatedEventApplier {

  private final CheckpointState checkpointState;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private final Set<CheckpointListener> checkpointListeners;

  public CheckpointCreatedEventApplier(
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final Set<CheckpointListener> checkpointListeners) {
    this.checkpointState = checkpointState;
    this.checkpointMetadataState = checkpointMetadataState;
    this.checkpointListeners = checkpointListeners;
  }

  public void apply(final CheckpointRecord checkpointRecord, final long checkpointTimestamp) {
    checkpointState.setLatestCheckpointInfo(
        checkpointRecord.getCheckpointId(),
        checkpointRecord.getCheckpointPosition(),
        checkpointTimestamp,
        checkpointRecord.getCheckpointType());

    checkpointMetadataState.addCheckpoint(
        checkpointRecord.getCheckpointId(),
        checkpointRecord.getCheckpointPosition(),
        checkpointTimestamp,
        checkpointRecord.getCheckpointType());

    checkpointListeners.forEach(
        listener ->
            listener.onNewCheckpointCreated(
                checkpointState.getLatestCheckpointId(), checkpointRecord.getCheckpointType()));
  }
}
