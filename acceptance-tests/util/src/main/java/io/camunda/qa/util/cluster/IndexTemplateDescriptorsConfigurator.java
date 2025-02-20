/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class IndexTemplateDescriptorsConfigurator {

  @Bean
  public DecisionIndex getDecisionIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new DecisionIndex(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public DecisionRequirementsIndex getDecisionRequirementsIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new DecisionRequirementsIndex(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public MetricIndex getMetricIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new MetricIndex(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public ImportPositionIndex getImportPositionIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ImportPositionIndex(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("operateProcessIndex")
  public ProcessIndex getProcessIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ProcessIndex(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public DecisionInstanceTemplate getDecisionInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new DecisionInstanceTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public EventTemplate getEventTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new EventTemplate(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("operateFlowNodeInstanceTemplate")
  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new FlowNodeInstanceTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public IncidentTemplate getIncidentTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new IncidentTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public ListViewTemplate getListViewTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ListViewTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public MessageTemplate getMessageTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new MessageTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public PostImporterQueueTemplate getPostImporterQueueTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new PostImporterQueueTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public SequenceFlowTemplate getSequenceFlowTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new SequenceFlowTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public JobTemplate getJobTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new JobTemplate(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("operateVariableTemplate")
  public VariableTemplate getVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new VariableTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public OperationTemplate getOperationTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new OperationTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public BatchOperationTemplate getBatchOperationTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new BatchOperationTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public DraftTaskVariableTemplate getDraftTaskVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new DraftTaskVariableTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public FormIndex getFormIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new FormIndex(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public TasklistMetricIndex getTasklistMetricIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new TasklistMetricIndex(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("operateSnapshotTaskVariableTemplate")
  public SnapshotTaskVariableTemplate getOperateSnapshotTaskVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new SnapshotTaskVariableTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("tasklistSnapshotTaskVariableTemplate")
  public SnapshotTaskVariableTemplate getTasklistSnapshotTaskVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new SnapshotTaskVariableTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public TaskTemplate getTaskTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new TaskTemplate(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("tasklistVariableTemplate")
  public VariableTemplate getTasklistVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new VariableTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("tasklistFlowNodeInstanceTemplate")
  public FlowNodeInstanceTemplate getTasklistFlowNodeInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new FlowNodeInstanceTemplate(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean
  public TasklistImportPositionIndex getTasklistImportPositionIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new TasklistImportPositionIndex(
        operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }

  @Bean("tasklistProcessIndex")
  public ProcessIndex getTasklistProcessIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ProcessIndex(operateProperties.getIndexPrefix(), databaseInfo.isElasticsearchDb());
  }
}
