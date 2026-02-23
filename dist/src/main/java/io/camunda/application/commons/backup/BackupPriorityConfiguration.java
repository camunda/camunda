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

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.Prio1Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import io.camunda.webapps.schema.descriptors.index.AuditLogCleanupIndex;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GlobalListenerIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
public class BackupPriorityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(BackupPriorityConfiguration.class);
  private final SecondaryStorage secondaryStorage;

  public BackupPriorityConfiguration(final Camunda configuration) {
    secondaryStorage = configuration.getData().getSecondaryStorage();
  }

  @Bean
  public BackupPriorities backupPriorities() {
    final boolean isElasticsearch = secondaryStorage.getType().isElasticSearch();
    final var indexPrefix =
        isElasticsearch
            ? secondaryStorage.getElasticsearch().getIndexPrefix()
            : secondaryStorage.getOpensearch().getIndexPrefix();
    return getBackupPriorities(indexPrefix, isElasticsearch);
  }

  private BackupPriorities getBackupPriorities(
      final String indexPrefix, final boolean isElasticsearch) {
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
            // OPERATE
            new BatchOperationTemplate(indexPrefix, isElasticsearch),
            new OperationTemplate(indexPrefix, isElasticsearch));

    final List<Prio4Backup> prio4 =
        List.of(
            // CAMUNDA
            new CorrelatedMessageSubscriptionTemplate(indexPrefix, isElasticsearch),
            // OPERATE
            new DecisionIndex(indexPrefix, isElasticsearch),
            new DecisionInstanceTemplate(indexPrefix, isElasticsearch),
            new MessageSubscriptionTemplate(indexPrefix, isElasticsearch),
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
            new MappingRuleIndex(indexPrefix, isElasticsearch),
            new PersistentWebSessionIndexDescriptor(indexPrefix, isElasticsearch),
            new RoleIndex(indexPrefix, isElasticsearch),
            new TenantIndex(indexPrefix, isElasticsearch),
            new UserIndex(indexPrefix, isElasticsearch),
            // USAGE METRICS
            new UsageMetricTemplate(indexPrefix, isElasticsearch),
            new UsageMetricTUTemplate(indexPrefix, isElasticsearch),
            // AUDIT LOG
            new AuditLogTemplate(indexPrefix, isElasticsearch),
            new AuditLogCleanupIndex(indexPrefix, isElasticsearch),
            // CAMUNDA
            new ClusterVariableIndex(indexPrefix, isElasticsearch),
            new JobMetricsBatchTemplate(indexPrefix, isElasticsearch),
            new GlobalListenerIndex(indexPrefix, isElasticsearch));

    LOG.debug("Prio1 are {}", prio1);
    LOG.debug("Prio2 are {}", prio2);
    LOG.debug("Prio3 are {}", prio3);
    LOG.debug("Prio4 are {}", prio4);
    LOG.debug("Prio5 are {}", prio5);
    return new BackupPriorities(prio1, prio2, prio3, prio4, prio5);
  }
}
