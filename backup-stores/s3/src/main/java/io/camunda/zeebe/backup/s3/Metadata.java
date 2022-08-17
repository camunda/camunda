/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.api.Backup;
import java.util.Set;

/**
 * Holds all metadata information about a backup, for example the {@link
 * io.camunda.zeebe.backup.api.BackupIdentifier}, the {@link
 * io.camunda.zeebe.backup.api.BackupDescriptor} and file names of segments and snapshot.
 *
 * <p>Used for JSON serialization.
 */
record Metadata(
    long checkpointId,
    int partitionId,
    int nodeId,
    long checkpointPosition,
    int numberOfPartitions,
    String snapshotId,
    Set<String> snapshotFileNames,
    Set<String> segmentFileNames) {

  static final String OBJECT_KEY = "metadata";

  static Metadata of(Backup backup) {
    return new Metadata(
        backup.id().checkpointId(),
        backup.id().partitionId(),
        backup.id().nodeId(),
        backup.descriptor().checkpointPosition(),
        backup.descriptor().numberOfPartitions(),
        backup.descriptor().snapshotId(),
        backup.snapshot().names(),
        backup.segments().names());
  }
}
