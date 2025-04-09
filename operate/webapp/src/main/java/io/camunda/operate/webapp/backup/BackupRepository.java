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
import java.util.List;
import java.util.regex.Pattern;

public interface BackupRepository {
  // Match all numbers, optionally ending with a *
  Pattern BACKUPID_PATTERN = Pattern.compile("^(\\d*)\\*?$");

  void deleteSnapshot(String repositoryName, String snapshotName);

  void validateRepositoryExists(String repositoryName);

  void validateNoDuplicateBackupId(String repositoryName, Long backupId);

  GetBackupStateResponseDto getBackupState(String repositoryName, Long backupId);

  List<GetBackupStateResponseDto> getBackups(
      String repositoryName, boolean verbose, final String pattern);

  default List<GetBackupStateResponseDto> getBackups(final String repositoryName) {
    return getBackups(repositoryName, true, null);
  }

  void executeSnapshotting(
      BackupService.SnapshotRequest snapshotRequest, Runnable onSuccess, Runnable onFailure);

  static Either<Throwable, String> validPattern(final String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      return Either.right("*");
    } else if (pattern.length() <= 20 && BACKUPID_PATTERN.matcher(pattern).matches()) {
      return Either.right(pattern);
    } else {
      return Either.left(new IllegalArgumentException("Invalid pattern: " + pattern));
    }
  }
}
