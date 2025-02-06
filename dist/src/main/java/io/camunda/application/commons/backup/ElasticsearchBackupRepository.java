/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Note that the condition used refers to operate ElasticSearchCondition */
@Conditional({ElasticsearchCondition.class, WebappEnabledCondition.class})
@Configuration
@Profile("operate")
public class ElasticsearchBackupRepository {

  private final ElasticsearchClient esClient;
  private final BackupRepositoryProps backupRepositoryProps;
  private final Executor threadPoolTaskExecutor;

  public ElasticsearchBackupRepository(
      @Qualifier("elasticsearchClient") final ElasticsearchClient esClient,
      final BackupRepositoryProps backupRepositoryProps,
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
    this.esClient = esClient;
    this.backupRepositoryProps = backupRepositoryProps;
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
  }

  @Bean
  public BackupRepository backupRepository() {
    return new io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository(
        esClient, backupRepositoryProps, new WebappsSnapshotNameProvider(), threadPoolTaskExecutor);
  }
}
