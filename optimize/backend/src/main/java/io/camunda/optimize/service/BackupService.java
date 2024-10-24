/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static io.camunda.optimize.dto.optimize.rest.SnapshotState.FAILED;
import static io.camunda.optimize.dto.optimize.rest.SnapshotState.INCOMPATIBLE;
import static io.camunda.optimize.dto.optimize.rest.SnapshotState.IN_PROGRESS;
import static io.camunda.optimize.dto.optimize.rest.SnapshotState.PARTIAL;
import static io.camunda.optimize.dto.optimize.rest.SnapshotState.SUCCESS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import io.camunda.optimize.dto.optimize.BackupState;
import io.camunda.optimize.dto.optimize.rest.BackupInfoDto;
import io.camunda.optimize.dto.optimize.rest.SnapshotInfoDto;
import io.camunda.optimize.dto.optimize.rest.SnapshotState;
import io.camunda.optimize.service.db.reader.BackupReader;
import io.camunda.optimize.service.db.writer.BackupWriter;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class BackupService {

  private static final int EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP = 2;
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(BackupService.class);

  private final BackupReader backupReader;
  private final BackupWriter backupWriter;
  private final ConfigurationService configurationService;

  public BackupService(
      final BackupReader backupReader,
      final BackupWriter backupWriter,
      final ConfigurationService configurationService) {
    this.backupReader = backupReader;
    this.backupWriter = backupWriter;
    this.configurationService = configurationService;
  }

  public synchronized void triggerBackup(final Long backupId) {
    backupReader.validateRepositoryExists();
    validateNoDuplicateBackupId(backupId);

    LOG.info("Triggering backup with ID {}", backupId);
    backupWriter.triggerSnapshotCreation(backupId);
  }

  public List<BackupInfoDto> getAllBackupInfo() {
    backupReader.validateRepositoryExists();
    return backupReader.getAllOptimizeSnapshotsByBackupId().entrySet().stream()
        .map(
            entry ->
                getSingleBackupInfo(
                    entry.getKey(),
                    entry.getValue().stream().collect(groupingBy(SnapshotInfoDto::getState))))
        .toList();
  }

  public BackupInfoDto getSingleBackupInfo(final Long backupId) {
    backupReader.validateRepositoryExists();
    return getSingleBackupInfo(
        backupId,
        backupReader.getOptimizeSnapshotsForBackupId(backupId).stream()
            .collect(groupingBy(SnapshotInfoDto::getState)));
  }

  private BackupInfoDto getSingleBackupInfo(
      final Long backupId, final Map<SnapshotState, List<SnapshotInfoDto>> snapshotInfosPerState) {
    if (snapshotInfosPerState.isEmpty()) {
      final String reason =
          String.format("No Optimize backup with ID [%d] could be found.", backupId);
      LOG.error(reason);
      throw new NotFoundException(reason);
    }
    return getBackupInfoDto(backupId, snapshotInfosPerState);
  }

  private BackupInfoDto getBackupInfoDto(
      final Long backupId, final Map<SnapshotState, List<SnapshotInfoDto>> snapshotInfosPerState) {
    final BackupState backupState = determineBackupState(snapshotInfosPerState);
    String failureReason = null;
    if (BackupState.FAILED == backupState) {
      failureReason =
          String.format(
              "The following snapshots failed: [%s]",
              snapshotInfosPerState.getOrDefault(FAILED, Collections.emptyList()).stream()
                  .map(SnapshotInfoDto::getSnapshotName)
                  .collect(joining(", ")));
    }
    return new BackupInfoDto(
        backupId,
        failureReason,
        backupState,
        snapshotInfosPerState.values().stream().flatMap(List::stream).toList());
  }

  public void deleteBackup(final Long backupId) {
    backupReader.validateRepositoryExists();
    backupWriter.deleteOptimizeSnapshots(backupId);
  }

  private BackupState determineBackupState(
      final Map<SnapshotState, List<SnapshotInfoDto>> snapshotInfosPerState) {
    if (snapshotInfosPerState.getOrDefault(SUCCESS, Collections.emptyList()).size()
        == EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP) {
      return BackupState.COMPLETED;
    } else if (snapshotInfosPerState.get(FAILED) != null
        || snapshotInfosPerState.get(PARTIAL) != null) {
      return BackupState.FAILED;
    } else if (snapshotInfosPerState.get(INCOMPATIBLE) != null) {
      return BackupState.INCOMPATIBLE;
    } else if (snapshotInfosPerState.get(IN_PROGRESS) != null) {
      return BackupState.IN_PROGRESS;
    } else if (snapshotInfosPerState.getOrDefault(SUCCESS, Collections.emptyList()).size()
        < EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP) {
      return BackupState.INCOMPLETE;
    } else {
      // this can for example occur if users create additional manual snapshots matching our naming
      // scheme
      return BackupState.FAILED;
    }
  }

  private void validateNoDuplicateBackupId(final Long backupId) {
    final List<SnapshotInfoDto> existingSnapshots =
        backupReader.getOptimizeSnapshotsForBackupId(backupId);
    if (!existingSnapshots.isEmpty()) {
      final String reason =
          String.format(
              "A backup with ID [%s] already exists. Found snapshots: [%s]",
              backupId,
              existingSnapshots.stream()
                  .map(SnapshotInfoDto::getSnapshotName)
                  .collect(joining(", ")));
      LOG.error(reason);
      throw new OptimizeConflictException(reason);
    }
  }
}
