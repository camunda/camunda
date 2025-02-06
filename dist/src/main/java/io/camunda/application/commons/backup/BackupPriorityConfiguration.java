/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.application.commons.backup.ConfigValidation.allMatch;
import static io.camunda.application.commons.backup.ConfigValidation.skipEmptyOptional;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.conditions.DatabaseType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import io.camunda.optimize.service.db.es.schema.index.index.TimestampBasedImportIndexES;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.index.PositionBasedImportIndexOS;
import io.camunda.optimize.service.db.os.schema.index.index.TimestampBasedImportIndexOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.profiles.ProfileWebApp;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.BackupPriority;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio6Backup;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.MessageTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.MappingIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.PersistentWebSessionIndexDescriptor;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Conditional(WebappEnabledCondition.class)
@ProfileWebApp
public class BackupPriorityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(BackupPriorityConfiguration.class);
  private static final String NO_CONFIG_ERROR_MESSAGE =
      "Expected operate, tasklist or optimize to be configured, but none of them are.";

  final String[] profiles;
  // all nullable
  final OperateProperties operateProperties;
  final TasklistProperties tasklistProperties;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final Boolean optimizeIsElasticSearch;

  public BackupPriorityConfiguration(
      @Autowired(required = false) final OperateProperties operateProperties,
      @Autowired(required = false) final TasklistProperties tasklistProperties,
      @Autowired(required = false) final OptimizeIndexNameService optimizeIndexNameService,
      @Autowired final Environment environment) {
    profiles = environment.getActiveProfiles();
    if (environment.matchesProfiles("operate")) {
      this.operateProperties = operateProperties;
    } else {
      this.operateProperties = null;
    }
    if (environment.matchesProfiles("tasklist")) {
      this.tasklistProperties = tasklistProperties;
    } else {
      this.tasklistProperties = null;
    }
    if (environment.matchesProfiles("optimize") && optimizeIndexNameService != null) {
      this.optimizeIndexNameService = optimizeIndexNameService;
      optimizeIsElasticSearch =
          ConfigurationService.getDatabaseType(environment)
              .equals(io.camunda.optimize.service.util.configuration.DatabaseType.ELASTICSEARCH);
    } else {
      this.optimizeIndexNameService =
          Optional.ofNullable(optimizeIndexNameService)
              .orElse(new OptimizeIndexNameService(OptimizeIndexNameService.defaultIndexPrefix));
      optimizeIsElasticSearch = null;
    }
  }

  private <A> Function<Map<String, A>, String> differentConfigFor(final String field) {
    return values ->
        String.format(
            "Expected %s to be configured with the same value in operate and tasklist. Got %s. Active profiles: %s",
            field, values, Arrays.asList(profiles));
  }

  @Bean
  public BackupPriorities backupPriorities() {
    final var indexPrefix = getIndexPrefix();

    final boolean isElasticsearch = getIsElasticsearch();

    final List<Prio1Backup> prio1 =
        List.of(
            // OPERATE
            new ImportPositionIndex(indexPrefix, isElasticsearch),
            // TASKLIST
            new TasklistImportPositionIndex(indexPrefix, isElasticsearch),
            // OPTIMIZE
            new OptimizePrio1Delegate<>(
                isElasticsearch
                    ? new PositionBasedImportIndexES()
                    : new PositionBasedImportIndexOS(),
                optimizeIndexNameService),
            new OptimizePrio1Delegate<>(
                isElasticsearch
                    ? new TimestampBasedImportIndexES()
                    : new TimestampBasedImportIndexOS(),
                optimizeIndexNameService));

    final List<Prio2Backup> prio2 =
        List.of(
            // OPERATE
            new ListViewTemplate(indexPrefix, isElasticsearch),
            // TASKLIST
            new TaskTemplate(indexPrefix, isElasticsearch));

    final List<Prio3Backup> prio3 =
        List.of(
            // OPERATE
            new BatchOperationTemplate(indexPrefix, isElasticsearch),
            new OperationTemplate(indexPrefix, isElasticsearch));

    final List<Prio4Backup> prio4 =
        List.of(
            // OPERATE
            new DecisionIndex(indexPrefix, isElasticsearch),
            new DecisionInstanceTemplate(indexPrefix, isElasticsearch),
            new EventTemplate(indexPrefix, isElasticsearch),
            new FlowNodeInstanceTemplate(indexPrefix, isElasticsearch),
            new IncidentTemplate(indexPrefix, isElasticsearch),
            new JobTemplate(indexPrefix, isElasticsearch),
            new MessageTemplate(indexPrefix, isElasticsearch),
            new PostImporterQueueTemplate(indexPrefix, isElasticsearch),
            new SequenceFlowTemplate(indexPrefix, isElasticsearch),
            new VariableTemplate(indexPrefix, isElasticsearch),
            // TASKLIST
            new DraftTaskVariableTemplate(indexPrefix, isElasticsearch),
            new SnapshotTaskVariableTemplate(indexPrefix, isElasticsearch));

    final List<Prio5Backup> prio5 =
        List.of(
            // OPERATE
            new DecisionRequirementsIndex(indexPrefix, isElasticsearch),
            new MetricIndex(indexPrefix, isElasticsearch),
            new ProcessIndex(indexPrefix, isElasticsearch),
            // TASKLIST
            new FormIndex(indexPrefix, isElasticsearch),
            new TasklistMetricIndex(indexPrefix, isElasticsearch),
            // USER MANAGEMENT
            new AuthorizationIndex(indexPrefix, isElasticsearch),
            new GroupIndex(indexPrefix, isElasticsearch),
            new MappingIndex(indexPrefix, isElasticsearch),
            new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch),
            new RoleIndex(indexPrefix, isElasticsearch),
            new TenantIndex(indexPrefix, isElasticsearch),
            new UserIndex(indexPrefix, isElasticsearch));

    // OPTIMIZE static indices
    final List<Prio6Backup> prio6 = getPrio6Backups(isElasticsearch);

    LOG.debug("Prio1 are {}", prio1);
    LOG.debug("Prio2 are {}", prio2);
    LOG.debug("Prio3 are {}", prio3);
    LOG.debug("Prio4 are {}", prio4);
    LOG.debug("Prio5 are {}", prio5);
    LOG.debug("Prio6 are {}", prio6);
    return new BackupPriorities(prio1, prio2, prio3, prio4, prio5, prio6);
  }

  private boolean getIsElasticsearch() {
    final Optional<Boolean> result =
        allMatch(
            Optional::empty,
            differentConfigFor("database.type"),
            Map.of(
                "operate",
                Optional.ofNullable(operateProperties)
                    .map(ignored -> DatabaseInfo.isCurrent(DatabaseType.Elasticsearch)),
                "tasklist",
                Optional.ofNullable(tasklistProperties)
                    .map(prop -> prop.getDatabase().equals(TasklistProperties.ELASTIC_SEARCH)),
                "optimize",
                Optional.ofNullable(optimizeIsElasticSearch)),
            skipEmptyOptional());
    if (result.isEmpty()) {
      throw new IllegalArgumentException(NO_CONFIG_ERROR_MESSAGE);
    }
    return result.get();
  }

  private List<Prio6Backup> getPrio6Backups(final Boolean isElasticsearch) {
    final List<Prio6Backup> prio6 = new ArrayList<>();
    {
      final var indices =
          isElasticsearch
              ? ElasticSearchSchemaManager.getAllNonDynamicMappings()
              : OpenSearchSchemaManager.getAllNonDynamicMappings();
      for (final IndexMappingCreator<?> index : indices) {
        // Optimize only has index with Priority 1 & 6, but it only has 1 static index with
        // Priority 1. Here we can just focus on those with Prio6
        if (index instanceof final Prio6Backup p) {
          prio6.add(
              new OptimizePrio6Delegate<>(
                  (IndexMappingCreator<?> & Prio6Backup) p, optimizeIndexNameService));
        }
      }
    }
    return prio6;
  }

  private String getIndexPrefix() {
    final var indexOptional =
        allMatch(
            Optional::empty,
            differentConfigFor("indexPrefix"),
            Map.of(
                "operate",
                Optional.ofNullable(operateProperties).map(OperateProperties::getIndexPrefix),
                "tasklist",
                Optional.ofNullable(tasklistProperties).map(TasklistProperties::getIndexPrefix)),
            // optimize does not use the global index prefix as the other apps, so it's not included
            // in this check.
            skipEmptyOptional());
    if (indexOptional.isEmpty()) {
      throw new IllegalArgumentException(NO_CONFIG_ERROR_MESSAGE);
    }
    return indexOptional.get();
  }

  /**
   * Optimize indices do not return the complete index name when {@link
   * BackupPriority#getFullQualifiedName()} is called, they only return a part of the index name.
   * For this reason, we need {@link OptimizeIndexNameService} to get the full index name.
   */
  record OptimizePrio6Delegate<I extends IndexMappingCreator<?> & Prio6Backup>(
      I index, OptimizeIndexNameService indexService) implements Prio6Backup {

    @Override
    public String getFullQualifiedName() {
      return indexService.getOptimizeIndexNameWithVersion(index);
    }

    @Override
    public boolean required() {
      return index.required();
    }
  }

  /** Same reasoning as {@link OptimizePrio1Delegate} */
  record OptimizePrio1Delegate<I extends IndexMappingCreator<?> & Prio1Backup>(
      I index, OptimizeIndexNameService indexService) implements Prio1Backup {

    @Override
    public String getFullQualifiedName() {
      return indexService.getOptimizeIndexNameWithVersion(index);
    }

    @Override
    public boolean required() {
      return index.required();
    }
  }
}
