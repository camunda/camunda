/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupServiceImpl;
import io.camunda.webapps.backup.DynamicIndicesProvider;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.profiles.ProfileWebApp;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import jakarta.websocket.OnClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
@Conditional(WebappEnabledCondition.class)
@ProfileWebApp
public class HistoryBackupComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistoryBackupComponent.class);
  private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
  private final BackupPriorities backupPriorities;
  private final BackupRepositoryProps backupRepositoryProps;
  private final BackupRepository backupRepository;
  private final DynamicIndicesProvider dynamicIndicesProvider;

  public HistoryBackupComponent(
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor,
      final BackupPriorities backupPriorities,
      final BackupRepositoryProps backupRepositoryProps,
      final BackupRepository backupRepository,
      final DynamicIndicesProvider dynamicIndicesProvider) {
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    this.backupPriorities = backupPriorities;
    this.backupRepositoryProps = backupRepositoryProps;
    this.backupRepository = backupRepository;
    this.dynamicIndicesProvider = dynamicIndicesProvider;
  }

  @Bean
  public BackupService backupService() {
    return new BackupServiceImpl(
        threadPoolTaskExecutor,
        backupPriorities,
        backupRepositoryProps,
        backupRepository,
        dynamicIndicesProvider);
  }

  @OnClose
  public void shutdownExecutor() {
    LOGGER.info("Shutting down history backup thread pool.");
    threadPoolTaskExecutor.shutdown();
  }
}
