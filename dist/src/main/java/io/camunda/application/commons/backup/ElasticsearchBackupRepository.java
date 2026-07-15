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

import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
@Configuration
public class ElasticsearchBackupRepository {

  private final SearchClients searchClients;
  private final Map<String, BackupRepositoryProps> backupRepositoryPropsByPhysicalTenant;
  private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

  public ElasticsearchBackupRepository(
      final SearchClients searchClients,
      @Qualifier("backupRepositoryPropsByTenant")
          final Map<String, BackupRepositoryProps> backupRepositoryPropsByPhysicalTenant,
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
    this.searchClients = searchClients;
    this.backupRepositoryPropsByPhysicalTenant = backupRepositoryPropsByPhysicalTenant;
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
  }

  @Bean("elasticsearchBackupRepositoriesByTenant")
  public Map<String, BackupRepository> elasticsearchBackupRepositoriesByTenant() {
    final var byPhysicalTenant = new LinkedHashMap<String, BackupRepository>();
    searchClients
        .esClients()
        .forEach(
            (physicalTenantId, client) ->
                byPhysicalTenant.put(
                    physicalTenantId,
                    new io.camunda.webapps.backup.repository.elasticsearch
                        .ElasticsearchBackupRepository(
                        client,
                        backupRepositoryPropsByPhysicalTenant.get(physicalTenantId),
                        BackupConfig.snapshotNameProvider(physicalTenantId),
                        threadPoolTaskExecutor)));
    return Map.copyOf(byPhysicalTenant);
  }
}
