/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.time.Instant;
import java.util.Optional;

/** Represents the status of a backup. */
public interface BackupStatus extends Comparable<BackupStatus> {
  BackupIdentifier id();

  Optional<BackupDescriptor> descriptor();

  BackupStatusCode statusCode();

  default boolean isCompleted() {
    return statusCode() == BackupStatusCode.COMPLETED;
  }

  Optional<String> failureReason();

  Optional<Instant> created();

  default Instant createdOrThrow() {
    return created()
        .orElseThrow(
            () -> new IllegalStateException("Backup %s missing created field".formatted(this)));
  }

  Optional<Instant> lastModified();

  @Override
  default int compareTo(final BackupStatus backupStatus) {
    return Long.compare(id().checkpointId(), backupStatus.id().checkpointId());
  }
}
