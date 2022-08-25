/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.api;

import java.util.Optional;

/**
 * Additional information about the backup that might be required for restoring or querying the
 * status
 */
public interface BackupDescriptor {
  /**
   * @return id of the snapshot included in the backup
   */
  Optional<String> snapshotId();

  /**
   * @return the checkpoint position of the checkpoint included in the backup
   */
  long checkpointPosition();

  /**
   * The number of partitions configured in the system at the time the backup is taken. This is
   * useful when the system supports dynamic configuration and the system restores from a backup at
   * a time when the number of partitions was different.
   *
   * @return number of partitions at the time backup is taken.
   */
  int numberOfPartitions();
}
