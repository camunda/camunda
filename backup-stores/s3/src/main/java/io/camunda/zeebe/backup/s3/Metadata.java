/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.s3;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import java.util.Optional;
import java.util.Set;

/**
 * Holds all metadata information about a backup, for example the {@link
 * io.camunda.zeebe.backup.api.BackupIdentifier}, the {@link
 * io.camunda.zeebe.backup.api.BackupDescriptor} and file names of segments and snapshot.
 *
 * <p>Used for JSON serialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record Metadata(
    long checkpointId,
    int partitionId,
    int nodeId,
    long checkpointPosition,
    int numberOfPartitions,
    Optional<String> snapshotId,
    String brokerVersion,
    Set<String> snapshotFileNames,
    Set<String> segmentFileNames) {

  static final String OBJECT_KEY = "metadata.json";

  static Metadata of(final Backup backup) {
    return new Metadata(
        backup.id().checkpointId(),
        backup.id().partitionId(),
        backup.id().nodeId(),
        backup.descriptor().checkpointPosition(),
        backup.descriptor().numberOfPartitions(),
        backup.descriptor().snapshotId(),
        backup.descriptor().brokerVersion(),
        backup.snapshot().names(),
        backup.segments().names());
  }

  BackupIdentifier id() {
    return new BackupIdentifierImpl(nodeId, partitionId, checkpointId);
  }

  BackupDescriptor descriptor() {
    return new BackupDescriptorImpl(
        snapshotId, checkpointPosition, numberOfPartitions, brokerVersion);
  }
}
