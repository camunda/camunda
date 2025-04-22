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
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface BackupRepository {
  // Match all numbers, optionally ending with a *
  Pattern BACKUPID_PATTERN = Pattern.compile("^(\\d*)\\*?$");
  // Maximum length of a length when converted to a string
  int LONG_MAX_LENGTH_AS_STRING = 19;
  Logger LOGGER = LoggerFactory.getLogger(BackupRepository.class);

  SnapshotNameProvider snapshotNameProvider();

  void deleteSnapshot(String repositoryName, String snapshotName);

  void validateRepositoryExists(String repositoryName);

  void validateNoDuplicateBackupId(String repositoryName, Long backupId);

  GetBackupStateResponseDto getBackupState(String repositoryName, Long backupId);

  Optional<Metadata> getMetadata(String repositoryName, Long backupId);

  Set<String> checkAllIndicesExist(List<String> indices);

  List<GetBackupStateResponseDto> getBackups(
      String repositoryName, final boolean verbose, final String pattern);

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

  static String validPattern(final String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      return "*";
    } else if (pattern.length() <= (LONG_MAX_LENGTH_AS_STRING + 1)
        && BACKUPID_PATTERN.matcher(pattern).matches()) {
      return pattern;
    } else {
      throw new IllegalArgumentException("Invalid pattern: " + pattern);
    }
  }
}
