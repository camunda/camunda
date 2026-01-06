/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing;

import io.camunda.zeebe.backup.api.CheckpointListener;
import io.camunda.zeebe.backup.metrics.CheckpointMetrics;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import java.util.Set;

public final class CheckpointCreatedEventApplier {

  private final CheckpointState checkpointState;
  private final Set<CheckpointListener> checkpointListeners;

  private final CheckpointMetrics metrics;

  public CheckpointCreatedEventApplier(
      final CheckpointState checkpointState,
      final Set<CheckpointListener> checkpointListeners,
      final CheckpointMetrics metrics) {
    this.checkpointState = checkpointState;
    this.checkpointListeners = checkpointListeners;
    this.metrics = metrics;
  }

  public void apply(final CheckpointRecord checkpointRecord) {
    checkpointState.setLatestCheckpointInfo(
        checkpointRecord.getCheckpointId(), checkpointRecord.getCheckpointPosition());
    checkpointListeners.forEach(
        listener -> listener.onNewCheckpointCreated(checkpointState.getLatestCheckpointId()));
  }
}
