/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupServiceImpl;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class BackupComponent {

  private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
  private final List<Prio1Backup> prio1BackupIndices;
  private final List<Prio2Backup> prio2BackupTemplates;
  private final List<Prio3Backup> prio3BackupTemplates;
  private final List<Prio4Backup> prio4BackupIndices;
  private final BackupRepositoryProps backupRepositoryProps;
  private final BackupRepository backupRepository;

  public BackupComponent(
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor,
      final List<Prio1Backup> prio1BackupIndices,
      final List<Prio2Backup> prio2BackupTemplates,
      final List<Prio3Backup> prio3BackupTemplates,
      final List<Prio4Backup> prio4BackupIndices,
      final BackupRepositoryProps backupRepositoryProps,
      final BackupRepository backupRepository) {
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    this.prio1BackupIndices = prio1BackupIndices;
    this.prio2BackupTemplates = prio2BackupTemplates;
    this.prio3BackupTemplates = prio3BackupTemplates;
    this.prio4BackupIndices = prio4BackupIndices;
    this.backupRepositoryProps = backupRepositoryProps;
    this.backupRepository = backupRepository;
  }

  @Bean
  public BackupService backupService() {
    return new BackupServiceImpl(
        threadPoolTaskExecutor,
        prio1BackupIndices,
        prio2BackupTemplates,
        prio3BackupTemplates,
        prio4BackupIndices,
        backupRepositoryProps,
        backupRepository);
  }
}
