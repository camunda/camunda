/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record PartitionBackupStatus(
    int partitionId,
    BackupStatusCode status,
    Optional<String> failureReason,
    Optional<String> createdAt,
    Optional<String> lastUpdatedAt,
    Optional<String> snapshotId,
    OptionalLong firstLogPosition,
    OptionalLong checkpointPosition,
    OptionalInt brokerId,
    Optional<String> brokerVersion) {

  static PartitionBackupStatus from(final BackupStatusResponse response) {

    final var status = response.getStatus();
    return switch (status) {
      case FAILED -> failedStatus(response);
      case DOES_NOT_EXIST -> notExistingStatus(response.getPartitionId());
      case IN_PROGRESS, COMPLETED -> validStatus(response);
      default -> throw new IllegalArgumentException("Unknown backup status %s".formatted(status));
    };
  }

  private static PartitionBackupStatus validStatus(final BackupStatusResponse response) {
    return new PartitionBackupStatus(
        response.getPartitionId(),
        response.getStatus(),
        Optional.empty(),
        Optional.ofNullable(response.getCreatedAt()),
        Optional.ofNullable(response.getLastUpdated()),
        Optional.ofNullable(response.getSnapshotId()),
        response.hasFirstLogPosition()
            ? OptionalLong.of(response.getFirstLogPosition())
            : OptionalLong.empty(),
        response.hasCheckpointPosition()
            ? OptionalLong.of(response.getCheckpointPosition())
            : OptionalLong.empty(),
        response.hasBrokerId() ? OptionalInt.of(response.getBrokerId()) : OptionalInt.empty(),
        Optional.ofNullable(response.getBrokerVersion()));
  }

  static PartitionBackupStatus notExistingStatus(final int partitionId) {
    return new PartitionBackupStatus(
        partitionId,
        BackupStatusCode.DOES_NOT_EXIST,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalInt.empty(),
        Optional.empty());
  }

  private static PartitionBackupStatus failedStatus(final BackupStatusResponse response) {
    return new PartitionBackupStatus(
        response.getPartitionId(),
        BackupStatusCode.FAILED,
        Optional.of(response.getFailureReason()),
        Optional.ofNullable(response.getCreatedAt()),
        Optional.ofNullable(response.getLastUpdated()),
        Optional.ofNullable(response.getSnapshotId()),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalInt.empty(),
        Optional.empty());
  }
}
