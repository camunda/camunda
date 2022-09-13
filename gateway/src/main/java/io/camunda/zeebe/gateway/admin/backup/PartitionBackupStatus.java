/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.util.Optional;

record PartitionBackupStatus(
    int partitionId,
    BackupStatusCode status,
    Optional<PartitionBackupDescriptor> description,
    Optional<String> failureReason,
    Optional<String> createdAt,
    Optional<String> lastUpdatedAt) {

  static PartitionBackupStatus from(final BackupStatusResponse response) {

    final var status = response.getStatus();
    return switch (status) {
      case FAILED -> failedStatus(response);
      case DOES_NOT_EXIST -> notExistingStatus(response);
      case IN_PROGRESS, COMPLETED -> validStatus(response);
      default -> throw new IllegalArgumentException("Unknown backup status %s".formatted(status));
    };
  }

  private static PartitionBackupStatus validStatus(final BackupStatusResponse response) {
    final var descriptor =
        new PartitionBackupDescriptor(
            response.getSnapshotId(),
            response.getCheckpointPosition(),
            response.getBrokerId(),
            response.getBrokerVersion());
    return new PartitionBackupStatus(
        response.getPartitionId(),
        response.getStatus(),
        Optional.of(descriptor),
        Optional.empty(),
        Optional.ofNullable(response.getCreatedAt()),
        Optional.ofNullable(response.getLastUpdated()));
  }

  private static PartitionBackupStatus notExistingStatus(final BackupStatusResponse response) {
    return new PartitionBackupStatus(
        response.getPartitionId(),
        BackupStatusCode.DOES_NOT_EXIST,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static PartitionBackupStatus failedStatus(final BackupStatusResponse response) {
    return new PartitionBackupStatus(
        response.getPartitionId(),
        BackupStatusCode.FAILED,
        Optional.empty(),
        Optional.of(response.getFailureReason()),
        Optional.ofNullable(response.getCreatedAt()),
        Optional.ofNullable(response.getLastUpdated()));
  }
}
