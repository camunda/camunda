/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.BackupResponseDto;
import org.camunda.optimize.service.es.reader.BackupReader;
import org.camunda.optimize.service.es.reader.BackupWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class BackupService {
  private final BackupReader backupReader;
  private final BackupWriter backupWriter;
  private final ConfigurationService configurationService;

  public synchronized BackupResponseDto triggerBackup(final String backupId) {
    validateRepositoryExists();
    backupReader.validateNoDuplicateBackupId(backupId);

    log.info("Triggering backup with ID {}", backupId);
    final List<String> scheduledSnapshotNames = backupWriter.triggerSnapshotCreation(backupId);

    return new BackupResponseDto(scheduledSnapshotNames);
  }

  private void validateRepositoryExists() {
    if (StringUtils.isEmpty(configurationService.getEsSnapshotRepositoryName())) {
      final String reason =
        "Cannot trigger backup because no Elasticsearch snapshot repository name found in Optimize configuration.";
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      backupReader.validateRepositoryExistsOrFail();
    }
  }
}
