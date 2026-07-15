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

@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
@Configuration
public class OpensearchBackupRepository {

  private final SearchClients searchClients;
  private final Map<String, BackupRepositoryProps> backupRepositoryPropsByPhysicalTenant;

  public OpensearchBackupRepository(
      final SearchClients searchClients,
      @Qualifier("backupRepositoryPropsByTenant")
          final Map<String, BackupRepositoryProps> backupRepositoryPropsByPhysicalTenant) {
    this.searchClients = searchClients;
    this.backupRepositoryPropsByPhysicalTenant = backupRepositoryPropsByPhysicalTenant;
  }

  @Bean("opensearchBackupRepositoriesByTenant")
  public Map<String, BackupRepository> opensearchBackupRepositoriesByTenant() {
    final var byPhysicalTenant = new LinkedHashMap<String, BackupRepository>();
    searchClients
        .osClients()
        .forEach(
            (physicalTenantId, client) ->
                byPhysicalTenant.put(
                    physicalTenantId,
                    new io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository(
                        client,
                        searchClients.osAsyncClients().get(physicalTenantId),
                        backupRepositoryPropsByPhysicalTenant.get(physicalTenantId),
                        BackupConfig.snapshotNameProvider(physicalTenantId))));
    return Map.copyOf(byPhysicalTenant);
  }
}
