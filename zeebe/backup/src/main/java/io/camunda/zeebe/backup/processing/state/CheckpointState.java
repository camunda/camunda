/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.processing.state;

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

  /**
   * Set id and position of the last created checkpoint
   *
   * @param checkpointId id of the checkpoint
   * @param checkpointPosition position of the checkpoint
   */
  void setLatestCheckpointInfo(final long checkpointId, final long checkpointPosition);

  /**
   * Set id and position of the last checkpoint with a successful backup.
   *
   * @param checkpointId id of the checkpoint for this backup.
   * @param checkpointPosition position of the checkpoint for this backup.
   */
  void setLatestBackupInfo(long checkpointId, long checkpointPosition);

  /** Returns the id of the last checkpoint with a successful backup. */
  long getLatestBackupId();

  /** Returns the position of the last checkpoint with a successful backup. */
  long getLatestBackupPosition();
}
