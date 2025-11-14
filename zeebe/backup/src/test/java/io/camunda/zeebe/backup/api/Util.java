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
import org.jspecify.annotations.NonNull;

final class Util {
  static BackupIdentifier id(final long checkpointId) {
    return new TestBackupIdentifier(checkpointId);
  }

  static BackupIdentifier id(final Integer checkpointId) {
    if (checkpointId == null) {
      return null;
    }
    return new TestBackupIdentifier(checkpointId);
  }

  static BackupDescriptor descriptor(final BackupIdentifier left, final BackupIdentifier right) {
    return new TestBackupDescriptor(left, right);
  }

  record TestBackupIdentifier(long checkpointId) implements BackupIdentifier {
    @Override
    public int nodeId() {
      return 1;
    }

    @Override
    public int partitionId() {
      return 1;
    }

    @Override
    public long checkpointId() {
      return checkpointId;
    }

    @Override
    public @NonNull String toString() {
      return Long.toString(checkpointId);
    }
  }

  record TestBackupDescriptor(BackupIdentifier previousBackup, BackupIdentifier nextBackup)
      implements BackupDescriptor {
    @Override
    public Optional<String> snapshotId() {
      return Optional.empty();
    }

    @Override
    public long checkpointPosition() {
      return 1;
    }

    @Override
    public int numberOfPartitions() {
      return 1;
    }

    @Override
    public String brokerVersion() {
      return "";
    }

    @Override
    public Instant checkpointTimestamp() {
      return Instant.MIN;
    }

    @Override
    public CheckpointType checkpointType() {
      return CheckpointType.MANUAL_BACKUP;
    }

    @Override
    public @NonNull String toString() {
      return "[%s, %s]".formatted(previousBackup, nextBackup);
    }
  }
}
