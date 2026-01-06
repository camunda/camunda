/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.configuration.SecondaryStorage.SecondaryStorageType.elasticsearch;
import static io.camunda.configuration.SecondaryStorage.SecondaryStorageType.opensearch;

import io.camunda.configuration.Backup;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.zeebe.util.VersionUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
public class BackupConfig {

  @Bean
  public BackupRepositoryProps backupRepositoryProps(final Camunda camunda) {
    return props(VersionUtil.getVersion(), camunda.getData().getBackup());
  }

  @Bean("backupThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(8);
    executor.setKeepAliveSeconds(60);
    executor.setThreadNamePrefix("webapps_backup_");
    executor.setStrictEarlyShutdown(true);
    executor.setQueueCapacity(4096);
    executor.initialize();
    return executor;
  }

  public static BackupRepositoryProps props(final String version, final Backup backupConfig) {
    return new BackupRepositoryPropsRecord(
        version,
        backupConfig.getRepositoryName(),
        backupConfig.getSnapshotTimeout(),
        backupConfig.getIncompleteCheckTimeout().getSeconds());
  }
}
