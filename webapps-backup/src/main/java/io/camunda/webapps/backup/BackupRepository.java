/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface BackupRepository {

  Logger LOGGER = LoggerFactory.getLogger(BackupRepository.class);

  SnapshotNameProvider snapshotNameProvider();

  void deleteSnapshot(String repositoryName, String snapshotName);

  void validateRepositoryExists(String repositoryName);

  void validateNoDuplicateBackupId(String repositoryName, Long backupId);

  GetBackupStateResponseDto getBackupState(String repositoryName, Long backupId);

  Optional<Metadata> getMetadata(String repositoryName, Long backupId);

  Set<String> checkAllIndicesExist(List<String> indices);

  List<GetBackupStateResponseDto> getBackups(String repositoryName, final boolean verbose);

  void executeSnapshotting(SnapshotRequest snapshotRequest, Runnable onSuccess, Runnable onFailure);

  default boolean isIncompleteCheckTimedOut(
      final long incompleteCheckTimeoutInSeconds, final long lastSnapshotFinishedTime) {
    final var incompleteCheckTimeoutInMilliseconds = incompleteCheckTimeoutInSeconds * 1000;
    try {
      final var now = Instant.now().toEpochMilli();
      return (now - lastSnapshotFinishedTime) > (incompleteCheckTimeoutInMilliseconds);
    } catch (final Exception e) {
      LOGGER.warn(
          "Couldn't check incomplete timeout for backup. Return incomplete check is timed out", e);
      return true;
    }
  }
}
