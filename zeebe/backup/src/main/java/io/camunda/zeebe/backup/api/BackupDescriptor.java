/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
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

  /**
   * Describes the version of the broker that took the backup. This may be a semver version (for
   * example '8.1.0') or an arbitrary string (for example 'dev').
   *
   * <p>Including the version might be useful for backwards compatability, for example to restore
   * backups of an old broker.
   *
   * @return The version of the broker that took the backup.
   */
  String brokerVersion();

  /**
   * @return the timestamp at which the related checkpoint was created can be null for backups
   *     created before 8.9
   */
  Instant checkpointTimestamp();

  /**
   * @return the type of the checkpoint that triggered the backup
   */
  CheckpointType checkpointType();
}
