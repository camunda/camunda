/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.restore.PartitionRestoreService.BackupValidator;

/** Validates that a backup was taken for the expected number of partitions. */
public final class ValidatePartitionCount implements BackupValidator {
  private final int expectedPartitionCount;

  public ValidatePartitionCount(final int expectedPartitionCount) {
    this.expectedPartitionCount = expectedPartitionCount;
  }

  @Override
  public BackupStatus validateStatus(final BackupStatus status) throws BackupNotValidException {
    final var descriptor =
        status
            .descriptor()
            .orElseThrow(
                () -> new BackupNotValidException(status, "Backup does not have a descriptor"));
    if (descriptor.numberOfPartitions() != expectedPartitionCount) {
      throw new BackupNotValidException(
          status,
          "Expected backup to have %d partitions, but has %d"
              .formatted(expectedPartitionCount, descriptor.numberOfPartitions()));
    }
    return status;
  }
}
