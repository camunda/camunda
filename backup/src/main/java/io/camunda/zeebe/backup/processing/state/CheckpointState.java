/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing.state;

public interface CheckpointState {

  long NO_CHECKPOINT = -1L;

  /**
   * Returns the id of the last created checkpoint
   *
   * @return checkpointId
   */
  long getCheckpointId();

  /**
   * Returns the position of the last created checkpoint
   *
   * @return checkpointPosition
   */
  long getCheckpointPosition();

  /**
   * Set checkpointId and checkpointPosition
   *
   * @param checkpointId id of the checkpoint
   * @param checkpointPosition position of the checkpoint
   */
  void setCheckpointInfo(final long checkpointId, final long checkpointPosition);
}
