/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.api;

/** Can be used by InterPartitionCommand Sender/Receiver to get the latest checkpointId. */
public interface CheckpointListener {

  /**
   * Called when ever a new checkpoint is created.
   *
   * <p>Will be called immediately after CHECKPOINT:CREATE record is processed if it results in a
   * new checkpoint.
   */
  void onNewCheckpointCreated(long checkpointId);
}
