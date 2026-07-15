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

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.DocumentBasedSecondaryStorageBackup;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.zeebe.util.VersionUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
public class BackupConfig {

  private static final Logger LOG = LoggerFactory.getLogger(BackupConfig.class);

  @Bean
  public Map<String, BackupRepositoryProps> backupRepositoryPropsByTenant(
      final PhysicalTenantResolver physicalTenantResolver) {
    return physicalTenantResolver.mapValues(
        physicalTenantConfig -> {
          final SecondaryStorage secondaryStorage =
              physicalTenantConfig.getData().getSecondaryStorage();
          final DocumentBasedSecondaryStorageBackup backupConfig = backupConfig(secondaryStorage);
          final String repositoryName = backupConfig.getRepositoryName();
          if (repositoryName == null || repositoryName.isBlank()) {
            LOG.warn(
                "No backup repository configured for {} secondary storage. Backup endpoints are"
                    + " active but will reject all requests until a repository is configured via"
                    + " 'camunda.data.secondary-storage.{}.backup.repository-name'.",
                secondaryStorage.getType(),
                secondaryStorage.getType());
          }
          return props(VersionUtil.getVersion(), backupConfig);
        });
  }

  @Bean("backupRepositoriesByTenant")
  public Map<String, BackupRepository> backupRepositoriesByTenant(
      @Qualifier("elasticsearchBackupRepositoriesByTenant")
          final Map<String, BackupRepository> elasticsearchBackupRepositoriesByTenant,
      @Qualifier("opensearchBackupRepositoriesByTenant")
          final Map<String, BackupRepository> opensearchBackupRepositoriesByTenant) {
    final var byPhysicalTenant = new LinkedHashMap<String, BackupRepository>();
    byPhysicalTenant.putAll(elasticsearchBackupRepositoriesByTenant);
    byPhysicalTenant.putAll(opensearchBackupRepositoriesByTenant);
    return Map.copyOf(byPhysicalTenant);
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

  public static WebappsSnapshotNameProvider snapshotNameProvider(final String physicalTenantId) {
    return PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
        ? new WebappsSnapshotNameProvider()
        : new WebappsSnapshotNameProvider(physicalTenantId);
  }

  public static BackupRepositoryProps props(
      final String version, final DocumentBasedSecondaryStorageBackup backupConfig) {
    return new BackupRepositoryPropsRecord(
        version,
        backupConfig.getRepositoryName(),
        backupConfig.getSnapshotTimeout(),
        backupConfig.getIncompleteCheckTimeout().getSeconds());
  }

  private DocumentBasedSecondaryStorageBackup backupConfig(
      final SecondaryStorage secondaryStorage) {
    return elasticsearch.equals(secondaryStorage.getType())
        ? secondaryStorage.getElasticsearch().getBackup()
        : secondaryStorage.getOpensearch().getBackup();
  }
}
