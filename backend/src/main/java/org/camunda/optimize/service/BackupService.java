/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.BackupState;
import org.camunda.optimize.dto.optimize.rest.BackupInfoDto;
import org.camunda.optimize.dto.optimize.rest.SnapshotInfoDto;
import org.camunda.optimize.service.db.reader.BackupReader;
import org.camunda.optimize.service.db.writer.BackupWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotShardFailure;
import org.elasticsearch.snapshots.SnapshotState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackupService {

  private static final int EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP = 2;

  private final BackupReader backupReader;
  private final BackupWriter backupWriter;
  private final ConfigurationService configurationService;

  public synchronized void triggerBackup(final Long backupId) {
    validateRepositoryExists();
    backupReader.validateNoDuplicateBackupId(backupId);

    log.info("Triggering backup with ID {}", backupId);
    backupWriter.triggerSnapshotCreation(backupId);
  }

  public List<BackupInfoDto> getAllBackupInfo() {
    validateRepositoryExists();
    return backupReader.getAllOptimizeSnapshotsByBackupId().entrySet().stream()
      .map(entry -> getSingleBackupInfo(entry.getKey(), entry.getValue().stream().collect(groupingBy(SnapshotInfo::state))))
      .toList();
  }

  public BackupInfoDto getSingleBackupInfo(final Long backupId) {
    validateRepositoryExists();
    return getSingleBackupInfo(backupId, backupReader.getOptimizeSnapshotsForBackupId(backupId)
      .stream()
      .collect(groupingBy(SnapshotInfo::state)));
  }

  private BackupInfoDto getSingleBackupInfo(final Long backupId,
                                            final Map<SnapshotState, List<SnapshotInfo>> snapshotInfosPerState) {
    if (snapshotInfosPerState.isEmpty()) {
      final String reason = String.format("No Optimize backup with ID [%d] could be found.", backupId);
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return getBackupInfoDto(backupId, snapshotInfosPerState);
  }

  private BackupInfoDto getBackupInfoDto(final Long backupId,
                                         final Map<SnapshotState, List<SnapshotInfo>> snapshotInfosPerState) {
    final BackupState backupState = determineBackupState(snapshotInfosPerState);
    String failureReason = null;
    if (BackupState.FAILED == backupState) {
      failureReason = String.format(
        "The following snapshots failed: [%s]",
        snapshotInfosPerState.getOrDefault(SnapshotState.FAILED, Collections.emptyList()).stream()
          .map(snapshotInfo -> snapshotInfo.snapshot().getSnapshotId().getName())
          .collect(joining(", "))
      );
    }
    return new BackupInfoDto(
      backupId,
      failureReason,
      backupState,
      snapshotInfosPerState.values()
        .stream()
        .flatMap(List::stream)
        .map(snapshotInfo -> new SnapshotInfoDto(
          snapshotInfo.snapshot().getSnapshotId().getName(),
          snapshotInfo.state(),
          OffsetDateTime.ofInstant(Instant.ofEpochMilli(snapshotInfo.startTime()), ZoneId.systemDefault()),
          snapshotInfo.shardFailures().stream().map(SnapshotShardFailure::toString).toList()
        ))
        .toList()
    );
  }

  public void deleteBackup(final Long backupId) {
    validateRepositoryExists();
    backupWriter.deleteOptimizeSnapshots(backupId);
  }

  private BackupState determineBackupState(final Map<SnapshotState, List<SnapshotInfo>> snapshotInfosPerState) {
    if (snapshotInfosPerState.getOrDefault(SnapshotState.SUCCESS, Collections.emptyList())
      .size() == EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP) {
      return BackupState.COMPLETED;
    } else if (snapshotInfosPerState.get(SnapshotState.FAILED) != null
      || snapshotInfosPerState.get(SnapshotState.PARTIAL) != null) {
      return BackupState.FAILED;
    } else if (snapshotInfosPerState.get(SnapshotState.INCOMPATIBLE) != null) {
      return BackupState.INCOMPATIBLE;
    } else if (snapshotInfosPerState.get(SnapshotState.IN_PROGRESS) != null) {
      return BackupState.IN_PROGRESS;
    } else if (snapshotInfosPerState.getOrDefault(SnapshotState.SUCCESS, Collections.emptyList())
      .size() < EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP) {
      return BackupState.INCOMPLETE;
    } else {
      // this can for example occur if users create additional manual snapshots matching our naming scheme
      return BackupState.FAILED;
    }
  }

  //todo extract this validation to the readers to avoid using there configuration or extract it from db configuration by OPT-7245
  private void validateRepositoryExists() {
    if (StringUtils.isEmpty(configurationService.getElasticSearchConfiguration().getSnapshotRepositoryName())) {
      final String reason =
        "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize configuration.";
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      backupReader.validateRepositoryExistsOrFail();
    }
  }

}
