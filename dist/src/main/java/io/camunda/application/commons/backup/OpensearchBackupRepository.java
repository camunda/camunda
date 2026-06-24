/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.configuration.SecondaryStorage.SecondaryStorageType.opensearch;

import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnSecondaryStorageType(opensearch)
@Configuration
public class OpensearchBackupRepository {

  private final OpenSearchClient openSearchClient;
  private final OpenSearchAsyncClient openSearchAsyncClient;
  private final BackupRepositoryProps backupProps;

  public OpensearchBackupRepository(
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final BackupRepositoryProps backupProps) {
    this.openSearchClient = openSearchClient;
    this.openSearchAsyncClient = openSearchAsyncClient;
    this.backupProps = backupProps;
  }

  @Bean
  public BackupRepository backupRepository() {
    return new io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository(
        openSearchClient, openSearchAsyncClient, backupProps, new WebappsSnapshotNameProvider());
  }
}
