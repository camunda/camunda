/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.protocol.record.value.management.CheckpointType;

public record BackupRangeStatus(CheckpointInfo first, CheckpointInfo last) {

  /**
   * Checkpoint metadata for a range boundary. Contains the information needed to describe a
   * checkpoint within a backup range without requiring a full {@link BackupStatus} lookup from the
   * backup store.
   */
  public record CheckpointInfo(
      long checkpointId,
      long checkpointPosition,
      long checkpointTimestamp,
      CheckpointType checkpointType,
      long firstLogPosition) {}
}
