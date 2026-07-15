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
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupServiceImpl;
import io.camunda.webapps.backup.BackupWiring;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
public class HistoryBackupComponent {

  private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
  private final Map<String, BackupWiring> backupWiringByPhysicalTenant;
  private final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant;
  private final Map<String, IndexDescriptors> indexDescriptorsByPhysicalTenant;

  public HistoryBackupComponent(
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor,
      @Qualifier("backupPrioritiesByTenant")
          final Map<String, BackupPriorities> backupPrioritiesByPhysicalTenant,
      @Qualifier("backupRepositoryPropsByTenant")
          final Map<String, BackupRepositoryProps> backupRepositoryPropsByPhysicalTenant,
      @Qualifier("backupRepositoriesByTenant")
          final Map<String, BackupRepository> backupRepositoriesByPhysicalTenant,
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant,
      @Qualifier("physicalTenantScopedIndexDescriptors")
          final Map<String, IndexDescriptors> indexDescriptorsByPhysicalTenant) {
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    backupWiringByPhysicalTenant =
        backupRepositoryPropsByPhysicalTenant.keySet().stream()
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    tenantId ->
                        new BackupWiring(
                            backupPrioritiesByPhysicalTenant.get(tenantId),
                            backupRepositoryPropsByPhysicalTenant.get(tenantId),
                            backupRepositoriesByPhysicalTenant.get(tenantId))));
    this.searchEngineConfigurationsByTenant = searchEngineConfigurationsByTenant;
    this.indexDescriptorsByPhysicalTenant = indexDescriptorsByPhysicalTenant;
  }

  @Bean
  public Map<String, BackupService> backupServicesByTenant() {
    final var byPhysicalTenant = new LinkedHashMap<String, BackupService>();
    searchEngineConfigurationsByTenant.forEach(
        (physicalTenantId, searchEngineConfiguration) -> {
          final var indexDescriptors = indexDescriptorsByPhysicalTenant.get(physicalTenantId);
          final var wiring = backupWiringByPhysicalTenant.get(physicalTenantId);
          byPhysicalTenant.put(
              physicalTenantId,
              new BackupServiceImpl(
                  threadPoolTaskExecutor,
                  wiring,
                  searchEngineConfiguration,
                  indexDescriptors.indices(),
                  indexDescriptors.templates()));
        });
    return Map.copyOf(byPhysicalTenant);
  }
}
