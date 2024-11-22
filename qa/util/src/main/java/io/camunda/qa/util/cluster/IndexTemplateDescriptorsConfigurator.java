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
import io.camunda.webapps.schema.descriptors.operate.template.UserTaskTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class IndexTemplateDescriptorsConfigurator {

  @Bean
  public DecisionIndex getDecisionIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new DecisionIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public DecisionRequirementsIndex getDecisionRequirementsIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new DecisionRequirementsIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public MetricIndex getMetricIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new MetricIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public ImportPositionIndex getImportPositionIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ImportPositionIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public ProcessIndex getProcessIndex(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ProcessIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public DecisionInstanceTemplate getDecisionInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new DecisionInstanceTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public EventTemplate getEventTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new EventTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new FlowNodeInstanceTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public IncidentTemplate getIncidentTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new IncidentTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public ListViewTemplate getListViewTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new ListViewTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public MessageTemplate getMessageTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new MessageTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public PostImporterQueueTemplate getPostImporterQueueTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new PostImporterQueueTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public SequenceFlowTemplate getSequenceFlowTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new SequenceFlowTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public UserTaskTemplate getUserTaskTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new UserTaskTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public JobTemplate getJobTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new JobTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public VariableTemplate getVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    return new VariableTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public OperationTemplate getOperationTemplate(final DatabaseInfo databaseInfo) {
    return new OperationTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public BatchOperationTemplate getBatchOperationTemplate(final DatabaseInfo databaseInfo) {
    return new BatchOperationTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public DraftTaskVariableTemplate getDraftTaskVariableTemplate(final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new DraftTaskVariableTemplate("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public FormIndex getFormIndex(final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new FormIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public TasklistMetricIndex getTasklistMetricIndex(final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new TasklistMetricIndex("", databaseInfo.isElasticsearchDb());
  }

  @Bean
  public SnapshotTaskVariableTemplate getSnapshotTaskVariableTemplate(
      final DatabaseInfo databaseInfo) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new SnapshotTaskVariableTemplate("", databaseInfo.isElasticsearchDb());
  }
}
