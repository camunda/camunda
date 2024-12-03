/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
    prefix = "camunda.database",
    name = "type",
    havingValue = "elasticsearch",
    matchIfMissing = true)
@Component
@Profile("tasklist & standalone")
// only active if standalone, otherwise the operate one is used
public class ElasticsearchTasklistBackupRepository {

  private final RestHighLevelClient esClient;
  private final ObjectMapper objectMapper;
  private final BackupRepositoryProps backupRepositoryProps;

  public ElasticsearchTasklistBackupRepository(
      @Qualifier("tasklistEsClient") final RestHighLevelClient esClient,
      final ObjectMapper objectMapper,
      final BackupRepositoryProps backupRepositoryProps) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.backupRepositoryProps = backupRepositoryProps;
  }

  @Bean
  public BackupRepository backupRepository() {
    return new ElasticsearchBackupRepository(
        esClient, objectMapper, backupRepositoryProps, new WebappsSnapshotNameProvider());
  }
}
