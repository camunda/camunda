/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

import io.camunda.zeebe.protocol.record.value.management.CheckpointType;

public interface CheckpointState {

  long NO_CHECKPOINT = -1L;

  /**
   * Returns the id of the last created checkpoint
   *
   * @return checkpointId
   */
  long getLatestCheckpointId();

  /**
   * Returns the position of the last created checkpoint
   *
   * @return checkpointPosition
   */
  long getLatestCheckpointPosition();

  /** Returns the timestamp of the last checkpoint with a successful backup. */
  long getLatestCheckpointTimestamp();

  /** Returns the type of the last created checkpoint. */
  CheckpointType getLatestCheckpointType();

  /**
   * Set id and position of the last created checkpoint
   *
   * @param checkpointId id of the checkpoint
   * @param checkpointPosition position of the checkpoint
   * @param timestamp timestamp of the checkpoint
   * @param type type of the checkpoint
   */
  void setLatestCheckpointInfo(
      final long checkpointId,
      final long checkpointPosition,
      final long timestamp,
      final CheckpointType type);

  /**
   * Set id and position of the last checkpoint with a successful backup.
   *
   * @param checkpointId id of the checkpoint for this backup.
   * @param checkpointPosition position of the checkpoint for this backup.
   * @param timestamp timestamp of the checkpoint for this backup.
   * @param type type of the checkpoint for this backup.
   */
  void setLatestBackupInfo(
      final long checkpointId,
      final long checkpointPosition,
      final long timestamp,
      final CheckpointType type,
      final long firstLogPosition);

  /** Returns the id of the last checkpoint with a successful backup. */
  long getLatestBackupId();

  /** Returns the position of the last checkpoint with a successful backup. */
  long getLatestBackupPosition();

  /** Returns the timestamp of the last checkpoint with a successful backup. */
  long getLatestBackupTimestamp();

  /** Returns the type of the last created backup. */
  CheckpointType getLatestBackupType();

  /** Returns the first log position of the last checkpoint with a successful backup. */
  long getLatestBackupFirstLogPosition();
}
