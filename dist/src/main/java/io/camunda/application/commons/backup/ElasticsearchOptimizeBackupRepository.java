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
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * A backup repository to be used when tasklist is not run together with Operate, because of the
 * different qualifiers for the elasticSearch clients between tasklist & operate When the ES client
 * is shared between operate & tasklist then this class can be removed after adding tasklist to the
 * valid profiles for {@link ElasticsearchBackupRepository}
 *
 * <p>Note that the condition used refers to tasklist ElasticSearchCondition
 */
@Conditional({ElasticSearchCondition.class, WebappEnabledCondition.class})
@Configuration
@Profile("optimize")
@ConditionalOnMissingBean({
  ElasticsearchBackupRepository.class,
  ElasticsearchTasklistBackupRepository.class
})
// only active if standalone, otherwise the operate one is used
public class ElasticsearchOptimizeBackupRepository {

  private final ElasticsearchClient esClient;
  private final BackupRepositoryProps backupRepositoryProps;
  private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

  public ElasticsearchOptimizeBackupRepository(
      final ElasticsearchClient esClient,
      final BackupRepositoryProps backupRepositoryProps,
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
    this.esClient = esClient;
    this.backupRepositoryProps = backupRepositoryProps;
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
  }

  @Bean
  public BackupRepository backupRepository() {
    return new ElasticsearchBackupRepository(
        esClient, backupRepositoryProps, new WebappsSnapshotNameProvider(), threadPoolTaskExecutor);
  }
}
