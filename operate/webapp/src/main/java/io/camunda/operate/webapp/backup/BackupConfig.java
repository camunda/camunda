/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.backup;

import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BackupConfig {

  @Bean("backupThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setThreadNamePrefix("backup_");
    executor.setQueueCapacity(6);
    executor.initialize();
    return executor;
  }

  @Bean
  public static BackupRepositoryProps backupRepositoryProps(
      final OperateProperties operateProperties) {
    return props(operateProperties.getVersion(), operateProperties.getBackup());
  }

  public static BackupRepositoryProps props(
      final String operateVersion, final BackupProperties operateProperties) {
    return new BackupRepositoryProps() {
      @Override
      public String version() {
        return operateVersion;
      }

      @Override
      public String repositoryName() {
        return operateProperties.getRepositoryName();
      }

      @Override
      public int snapshotTimeout() {
        return operateProperties.getSnapshotTimeout();
      }

      @Override
      public Long incompleteCheckTimeoutInSeconds() {
        return operateProperties.getIncompleteCheckTimeoutInSeconds();
      }

      @Override
      public boolean includeGlobalState() {
        // was not set in operate
        return false;
      }
    };
  }
}
