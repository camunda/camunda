/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio6Backup;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackupPriorityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(BackupPriorityConfiguration.class);

  final BackupPriorities backupPriorities;

  public BackupPriorityConfiguration(
      final List<Prio1Backup> prio1BackupIndices,
      final List<Prio2Backup> prio2BackupTemplates,
      final List<Prio3Backup> prio3BackupTemplates,
      final List<Prio4Backup> prio4BackupTemplates,
      final List<Prio5Backup> prio5BackupIndices,
      final List<Prio6Backup> prio6BackupIndices) {
    LOG.debug("Prio1BackupIndices are {}", prio1BackupIndices);
    LOG.debug("Prio2BackupTemplates are {}", prio2BackupTemplates);
    LOG.debug("Prio3BackupTemplates are {}", prio3BackupTemplates);
    LOG.debug("Prio4BackupIndices are {}", prio4BackupTemplates);
    LOG.debug("Prio5BackupIndices are {}", prio5BackupIndices);
    LOG.debug("Prio6BackupIndices are {}", prio6BackupIndices);
    backupPriorities =
        new BackupPriorities(
            prio1BackupIndices,
            prio2BackupTemplates,
            prio3BackupTemplates,
            prio4BackupTemplates,
            prio5BackupIndices,
            prio6BackupIndices);
  }

  @Bean
  public BackupPriorities backupPriorities() {
    return backupPriorities;
  }
}
