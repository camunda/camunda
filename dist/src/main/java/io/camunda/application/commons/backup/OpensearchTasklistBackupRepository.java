/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * A backup repository to be used when tasklist is not run together with Operate, because of the
 * different qualifiers for the opensearchClients between tasklist & operate When the OS client is
 * shared between operate & tasklist then this class can be removed after adding tasklist to the
 * valid profiles for {@link OpensearchBackupRepository}
 *
 * <p>Note that the condition used refers to tasklist OpensearchCondition
 */
@Conditional({OpenSearchCondition.class, WebappEnabledCondition.class})
@Configuration
@Profile("tasklist")
@ConditionalOnMissingBean(OpensearchBackupRepository.class)
// only active if standalone, otherwise the operate one is used
public class OpensearchTasklistBackupRepository {

  private final OpenSearchClient openSearchClient;
  private final OpenSearchAsyncClient openSearchAsyncClient;
  private final BackupRepositoryProps backupProps;

  public OpensearchTasklistBackupRepository(
      @Qualifier("tasklistOsClient") final OpenSearchClient openSearchClient,
      @Qualifier("tasklistOsAsyncClient") final OpenSearchAsyncClient openSearchAsyncClient,
      final BackupRepositoryProps backupProps) {
    this.openSearchClient = openSearchClient;
    this.openSearchAsyncClient = openSearchAsyncClient;
    this.backupProps = backupProps;
  }

  @Bean
  public BackupRepository backupRepository() {
    return new OpensearchBackupRepository(openSearchClient, openSearchAsyncClient, backupProps);
  }
}
