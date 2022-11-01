/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.BackupState;
import org.camunda.optimize.dto.optimize.rest.BackupResponseDto;
import org.camunda.optimize.dto.optimize.rest.BackupStateResponseDto;
import org.camunda.optimize.service.es.reader.BackupReader;
import org.camunda.optimize.service.es.reader.BackupWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.snapshots.Snapshot;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackupService {
  private final BackupReader backupReader;
  private final BackupWriter backupWriter;
  private final ConfigurationService configurationService;
  private static final int EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP = 2;

  public synchronized BackupResponseDto triggerBackup(final String backupId) {
    validateRepositoryExists();
    backupReader.validateNoDuplicateBackupId(backupId);

    log.info("Triggering backup with ID {}", backupId);
    final List<String> scheduledSnapshotNames = backupWriter.triggerSnapshotCreation(backupId);

    return new BackupResponseDto(scheduledSnapshotNames);
  }

  public BackupStateResponseDto getBackupState(final String backupId) {
    validateRepositoryExists();
    final Map<SnapshotState, List<SnapshotInfo>> snapshotInfosPerState = backupReader.getAllOptimizeSnapshots(backupId)
      .stream()
      .collect(groupingBy(SnapshotInfo::state));
    final long snapshotCount = snapshotInfosPerState.values().stream().mapToInt(List::size).sum();
    if (snapshotCount > EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP) {
      final String reason = String.format(
        "Unable to determine backup state because unexpected number of snapshots exist for backupID [%s]. Expected [%s] " +
          "snapshots but found [%s]. Found snapshots: [%s].",
        backupId,
        EXPECTED_NUMBER_OF_SNAPSHOTS_PER_BACKUP,
        snapshotCount,
        snapshotInfosPerState.values()
          .stream()
          .flatMap(snapshotInfos -> snapshotInfos.stream().map(SnapshotInfo::snapshot).map(Snapshot::toString))
          .collect(joining(", "))
      );
      log.error(reason);
      throw new OptimizeRuntimeException(reason);
    }

    if (snapshotInfosPerState.isEmpty()) {
      final String reason = String.format("No Optimize backup with ID [%s] could be found.", backupId);
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return new BackupStateResponseDto(determineBackupState(snapshotInfosPerState));
  }

  public void deleteBackup(final String backupId) {
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
    } else {
      return BackupState.INCOMPLETE;
    }
  }

  private void validateRepositoryExists() {
    if (StringUtils.isEmpty(configurationService.getEsSnapshotRepositoryName())) {
      final String reason =
        "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize configuration.";
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      backupReader.validateRepositoryExistsOrFail();
    }
  }
}
