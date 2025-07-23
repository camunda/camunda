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

import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.profiles.ProfileWebApp;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.OperateUserIndex;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnBackupWebappsEnabled
@ProfileWebApp
public class BackupPriorityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(BackupPriorityConfiguration.class);
  private static final String NO_CONFIG_ERROR_MESSAGE =
      "Expected operate or tasklist to be configured, but neither of them are.";

  final String[] profiles;
  // all nullable
  final OperateProperties operateProperties;
  final TasklistProperties tasklistProperties;

  public BackupPriorityConfiguration(
      @Autowired(required = false) final OperateProperties operateProperties,
      @Autowired(required = false) final TasklistProperties tasklistProperties,
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
    return getBackupPriorities(indexPrefix, isElasticsearch);
  }

  public static BackupPriorities getBackupPriorities(
      final String indexPrefix, final boolean isElasticsearch) {
    final List<Prio1Backup> prio1 =
        List.of(
            // OPERATE
            new ImportPositionIndex(indexPrefix, isElasticsearch),
            // TASKLIST
            new TasklistImportPositionIndex(indexPrefix, isElasticsearch));

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
            new OperateUserIndex(indexPrefix, isElasticsearch),
            new ProcessIndex(indexPrefix, isElasticsearch),
            // TASKLIST
            new FormIndex(indexPrefix, isElasticsearch),
            new TasklistMetricIndex(indexPrefix, isElasticsearch),
            // USER MANAGEMENT
            new AuthorizationIndex(indexPrefix, isElasticsearch),
            new GroupIndex(indexPrefix, isElasticsearch),
            new MappingRuleIndex(indexPrefix, isElasticsearch),
            new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch),
            new RoleIndex(indexPrefix, isElasticsearch),
            new TenantIndex(indexPrefix, isElasticsearch),
            new UserIndex(indexPrefix, isElasticsearch),
            // USAGE METRICS
            new UsageMetricIndex(indexPrefix, isElasticsearch),
            new UsageMetricTUIndex(indexPrefix, isElasticsearch));

    LOG.debug("Prio1 are {}", prio1);
    LOG.debug("Prio2 are {}", prio2);
    LOG.debug("Prio3 are {}", prio3);
    LOG.debug("Prio4 are {}", prio4);
    LOG.debug("Prio5 are {}", prio5);
    return new BackupPriorities(prio1, prio2, prio3, prio4, prio5);
  }

  private boolean getIsElasticsearch() {
    final Optional<Boolean> result =
        allMatch(
            Optional::empty,
            differentConfigFor("database.type"),
            Map.of(
                "operate",
                Optional.ofNullable(operateProperties).map(OperateProperties::isElasticsearchDB),
                "tasklist",
                Optional.ofNullable(tasklistProperties)
                    .map(prop -> prop.getDatabase().equals(TasklistProperties.ELASTIC_SEARCH))),
            skipEmptyOptional());
    if (result.isEmpty()) {
      throw new IllegalArgumentException(NO_CONFIG_ERROR_MESSAGE);
    }
    return result.get();
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
            skipEmptyOptional());
    if (indexOptional.isEmpty()) {
      throw new IllegalArgumentException(NO_CONFIG_ERROR_MESSAGE);
    }
    return indexOptional.get();
  }
}
