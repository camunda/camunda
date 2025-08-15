/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.OperateUserIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class IndexTemplateDescriptorsConfigurator {

  @Bean
  public DecisionIndex getDecisionIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new DecisionIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public DecisionRequirementsIndex getDecisionRequirementsIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new DecisionRequirementsIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public MetricIndex getMetricIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new MetricIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public UsageMetricIndex getUsageMetricIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new UsageMetricIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public ImportPositionIndex getImportPositionIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new ImportPositionIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean("operateProcessIndex")
  public ProcessIndex getProcessIndex(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new ProcessIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public DecisionInstanceTemplate getDecisionInstanceTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new DecisionInstanceTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public EventTemplate getEventTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new EventTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean("operateFlowNodeInstanceTemplate")
  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new FlowNodeInstanceTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public IncidentTemplate getIncidentTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new IncidentTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public ListViewTemplate getListViewTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new ListViewTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public MessageTemplate getMessageTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new MessageTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public PostImporterQueueTemplate getPostImporterQueueTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new PostImporterQueueTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public SequenceFlowTemplate getSequenceFlowTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new SequenceFlowTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public TaskTemplate getTaskTemplate(
      final DatabaseInfo databaseInfo, final IndexPrefixHolder indexPrefixHolder) {
    return new TaskTemplate("", databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public JobTemplate getJobTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new JobTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean("operateVariableTemplate")
  public VariableTemplate getVariableTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new VariableTemplate(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public OperationTemplate getOperationTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new OperationTemplate(
        operateProperties.getIndexPrefix(DatabaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public BatchOperationTemplate getBatchOperationTemplate(
      final OperateProperties operateProperties,
      final DatabaseInfo databaseInfo,
      final IndexPrefixHolder indexPrefixHolder) {
    return new BatchOperationTemplate(
        operateProperties.getIndexPrefix(DatabaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean("operateSnapshotTaskVariableTemplate")
  public SnapshotTaskVariableTemplate getSnapshotTaskVariableTemplate(
      final DatabaseInfo databaseInfo, final IndexPrefixHolder indexPrefixHolder) {
    return new SnapshotTaskVariableTemplate("", databaseInfo.isElasticsearchDb()) {
      @Override
      public String getIndexPrefix() {
        return indexPrefixHolder.getIndexPrefix();
      }
    };
  }

  @Bean
  public OperateUserIndex getOperateUserIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new OperateUserIndex(
        operateProperties.getIndexPrefix(databaseInfo.getCurrent()),
        databaseInfo.isElasticsearchDb());
  }
}
