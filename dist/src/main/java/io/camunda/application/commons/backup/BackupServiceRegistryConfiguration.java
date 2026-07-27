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

import io.camunda.application.commons.backup.BackupServiceRegistry.PhysicalTenantBackup;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.DocumentBasedSecondaryStorageBackup;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupServiceImpl;
import io.camunda.webapps.backup.BackupWiring;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository;
import io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate;
import io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.template.WaitStateTemplate;
import io.camunda.zeebe.util.VersionUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Builds the {@link BackupServiceRegistry}: one fully-wired {@link BackupService} per physical
 * tenant, each bound to that tenant's search cluster, index prefix, snapshot repository, and
 * tenant-scoped snapshot naming.
 *
 * <p>This replaces the previous fan-out of per-tenant {@code Map<String, ...>} beans (props,
 * priorities, ES/OS repositories, services). Those maps were only ever consumed together to
 * assemble the per-tenant services and are now assembled inline here, so the registry is the single
 * bean the {@code backupHistory} actuator and {@link
 * io.camunda.application.StandaloneBackupManager} depend on.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
public class BackupServiceRegistryConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(BackupServiceRegistryConfiguration.class);

  @Bean("backupThreadPoolExecutor")
  public ThreadPoolTaskExecutor backupThreadPoolExecutor() {
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

  @Bean
  public BackupServiceRegistry backupServiceRegistry(
      final PhysicalTenantResolver physicalTenantResolver,
      final SearchClients searchClients,
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant,
      @Qualifier("physicalTenantScopedIndexDescriptors")
          final Map<String, IndexDescriptors> indexDescriptorsByPhysicalTenant,
      @Qualifier("backupThreadPoolExecutor") final ThreadPoolTaskExecutor threadPoolTaskExecutor) {

    final var backups = new ArrayList<PhysicalTenantBackup>();
    physicalTenantResolver
        .getAll()
        .forEach(
            (physicalTenantId, tenantConfig) -> {
              final var secondaryStorage = tenantConfig.getData().getSecondaryStorage();
              final var props = props(VersionUtil.getVersion(), backupConfig(secondaryStorage));
              warnIfNoRepositoryConfigured(secondaryStorage, props);

              final var repository =
                  backupRepository(physicalTenantId, searchClients, props, threadPoolTaskExecutor);
              final var indexDescriptors = indexDescriptorsByPhysicalTenant.get(physicalTenantId);
              final var backupService =
                  new BackupServiceImpl(
                      threadPoolTaskExecutor,
                      new BackupWiring(backupPriorities(secondaryStorage), props, repository),
                      searchEngineConfigurationsByTenant.get(physicalTenantId),
                      indexDescriptors.indices(),
                      indexDescriptors.templates());

              backups.add(new PhysicalTenantBackup(physicalTenantId, backupService, props));
            });
    return new BackupServiceRegistry(backups);
  }

  private static BackupRepository backupRepository(
      final String physicalTenantId,
      final SearchClients searchClients,
      final BackupRepositoryProps props,
      final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
    final var snapshotNameProvider = snapshotNameProvider(physicalTenantId);
    final var esClient = searchClients.esClients().get(physicalTenantId);
    if (esClient != null) {
      return new ElasticsearchBackupRepository(
          esClient, props, snapshotNameProvider, threadPoolTaskExecutor);
    }
    return new OpensearchBackupRepository(
        searchClients.osClients().get(physicalTenantId),
        searchClients.osAsyncClients().get(physicalTenantId),
        props,
        snapshotNameProvider);
  }

  private static void warnIfNoRepositoryConfigured(
      final SecondaryStorage secondaryStorage, final BackupRepositoryProps props) {
    if (props.repositoryName() == null || props.repositoryName().isBlank()) {
      LOG.warn(
          "No backup repository configured for {} secondary storage. Backup endpoints are"
              + " active but will reject all requests until a repository is configured via"
              + " 'camunda.data.secondary-storage.{}.backup.repository-name'.",
          secondaryStorage.getType(),
          secondaryStorage.getType());
    }
  }

  static WebappsSnapshotNameProvider snapshotNameProvider(final String physicalTenantId) {
    return PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)
        ? new WebappsSnapshotNameProvider()
        : new WebappsSnapshotNameProvider(physicalTenantId);
  }

  static BackupRepositoryProps props(
      final String version, final DocumentBasedSecondaryStorageBackup backupConfig) {
    return new BackupRepositoryPropsRecord(
        version,
        backupConfig.getRepositoryName(),
        backupConfig.getSnapshotTimeout(),
        backupConfig.getIncompleteCheckTimeout().getSeconds());
  }

  private static DocumentBasedSecondaryStorageBackup backupConfig(
      final SecondaryStorage secondaryStorage) {
    return elasticsearch.equals(secondaryStorage.getType())
        ? secondaryStorage.getElasticsearch().getBackup()
        : secondaryStorage.getOpensearch().getBackup();
  }

  static BackupPriorities backupPriorities(final SecondaryStorage secondaryStorage) {
    final boolean isElasticsearch = secondaryStorage.getType().isElasticSearch();
    final var indexPrefix =
        isElasticsearch
            ? secondaryStorage.getElasticsearch().getIndexPrefix()
            : secondaryStorage.getOpensearch().getIndexPrefix();
    final List<Prio1Backup> prio1 =
        List.of(
            // OPERATE
            new MetadataIndex(indexPrefix, isElasticsearch),
            // HISTORY DELETION
            new HistoryDeletionIndex(indexPrefix, isElasticsearch));

    final List<Prio2Backup> prio2 =
        List.of(
            // OPERATE
            new ListViewTemplate(indexPrefix, isElasticsearch),
            // TASKLIST
            new TaskTemplate(indexPrefix, isElasticsearch));

    final List<Prio3Backup> prio3 =
        List.of(
            // CAMUNDA
            new AgentInstanceTemplate(indexPrefix, isElasticsearch),
            new CorrelatedMessageSubscriptionTemplate(indexPrefix, isElasticsearch),
            // OPERATE
            new BatchOperationTemplate(indexPrefix, isElasticsearch),
            new DecisionInstanceTemplate(indexPrefix, isElasticsearch),
            new FlowNodeInstanceTemplate(indexPrefix, isElasticsearch),
            new IncidentTemplate(indexPrefix, isElasticsearch),
            new JobTemplate(indexPrefix, isElasticsearch),
            new MessageSubscriptionTemplate(indexPrefix, isElasticsearch),
            new MessageTemplate(indexPrefix, isElasticsearch),
            new OperationTemplate(indexPrefix, isElasticsearch),
            new PostImporterQueueTemplate(indexPrefix, isElasticsearch),
            new SequenceFlowTemplate(indexPrefix, isElasticsearch),
            new VariableTemplate(indexPrefix, isElasticsearch),
            // TASKLIST
            new DraftTaskVariableTemplate(indexPrefix, isElasticsearch),
            new SnapshotTaskVariableTemplate(indexPrefix, isElasticsearch));

    final List<Prio4Backup> prio4 =
        List.of(
            // OPERATE
            new DecisionIndex(indexPrefix, isElasticsearch),
            new DecisionRequirementsIndex(indexPrefix, isElasticsearch),
            new ProcessIndex(indexPrefix, isElasticsearch),
            // TASKLIST
            new FormIndex(indexPrefix, isElasticsearch),
            // USER MANAGEMENT
            new AuthorizationIndex(indexPrefix, isElasticsearch),
            new GroupIndex(indexPrefix, isElasticsearch),
            new MappingRuleIndex(indexPrefix, isElasticsearch),
            new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch),
            new RoleIndex(indexPrefix, isElasticsearch),
            new TenantIndex(indexPrefix, isElasticsearch),
            new UserIndex(indexPrefix, isElasticsearch),
            // USAGE METRICS
            new UsageMetricTemplate(indexPrefix, isElasticsearch),
            new UsageMetricTUTemplate(indexPrefix, isElasticsearch),
            // AUDIT LOG
            new AuditLogCleanupIndex(indexPrefix, isElasticsearch),
            new AuditLogTemplate(indexPrefix, isElasticsearch),
            // CAMUNDA
            new AgentHistoryTemplate(indexPrefix, isElasticsearch),
            new ClusterVariableIndex(indexPrefix, isElasticsearch),
            new DeployedResourceIndex(indexPrefix, isElasticsearch),
            new GlobalListenerIndex(indexPrefix, isElasticsearch),
            new JobMetricsBatchTemplate(indexPrefix, isElasticsearch),
            // WAIT STATE
            new WaitStateTemplate(indexPrefix, isElasticsearch));

    LOG.debug("Prio1 are {}", prio1);
    LOG.debug("Prio2 are {}", prio2);
    LOG.debug("Prio3 are {}", prio3);
    LOG.debug("Prio4 are {}", prio4);
    return new BackupPriorities(prio1, prio2, prio3, prio4);
  }
}
