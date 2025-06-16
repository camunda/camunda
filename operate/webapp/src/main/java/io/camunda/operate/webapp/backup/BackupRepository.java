/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import io.camunda.operate.util.Either;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface BackupRepository {
  Logger LOGGER = LoggerFactory.getLogger(BackupRepository.class);
  // Match all numbers, optionally ending with a *
  Pattern BACKUPID_PATTERN = Pattern.compile("^(\\d*)\\*?$");

  // Maximum length of a length when converted to a string
  int LONG_MAX_LENGTH_AS_STRING = 19;

  void deleteSnapshot(String repositoryName, String snapshotName);

  void validateRepositoryExists(String repositoryName);

  void validateNoDuplicateBackupId(String repositoryName, Long backupId);

  GetBackupStateResponseDto getBackupState(
      String repositoryName, Long backupId, final Predicate<Long> isBackupInProgressPredicate);

  List<GetBackupStateResponseDto> getBackups(
      String repositoryName,
      boolean verbose,
      final String pattern,
      Predicate<Long> isBackupInProgressPredicate);

  void executeSnapshotting(
      BackupService.SnapshotRequest snapshotRequest, Runnable onSuccess, Runnable onFailure);

  static Either<Throwable, String> validPattern(final String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      return Either.right("*");
    } else if (pattern.length() <= (LONG_MAX_LENGTH_AS_STRING + 1)
        && BACKUPID_PATTERN.matcher(pattern).matches()) {
      return Either.right(pattern);
    } else {
      return Either.left(new IllegalArgumentException("Invalid pattern: " + pattern));
    }
  }

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
