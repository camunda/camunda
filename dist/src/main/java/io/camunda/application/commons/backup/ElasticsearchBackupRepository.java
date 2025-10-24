/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.profiles.ProfileWebApp;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ConditionalOnBackupWebappsEnabled
@ConditionalOnSecondaryStorageType(SecondaryStorageType.elasticsearch)
@Configuration
@ProfileWebApp
public class ElasticsearchBackupRepository {

  private final ElasticsearchClient elasticsearchClient;
  private final BackupRepositoryProps backupRepositoryProps;
  private final Executor threadPoolTaskExecutor;

  public ElasticsearchBackupRepository(
      final ElasticsearchClient elasticsearchClient,
      final BackupRepositoryProps backupRepositoryProps,
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
    this.elasticsearchClient = elasticsearchClient;
    this.backupRepositoryProps = backupRepositoryProps;
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
  }

  @Bean
  public BackupRepository backupRepository() {
    return new io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository(
        elasticsearchClient,
        backupRepositoryProps,
        new WebappsSnapshotNameProvider(),
        threadPoolTaskExecutor);
  }
}
