/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

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
  public DecisionIndex getDecisionIndex(final OperateProperties operateProperties) {
    return new DecisionIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public DecisionRequirementsIndex getDecisionRequirementsIndex(
      final OperateProperties operateProperties) {
    return new DecisionRequirementsIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public MetricIndex getMetricIndex(final OperateProperties operateProperties) {
    return new MetricIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public ImportPositionIndex getImportPositionIndex(final OperateProperties operateProperties) {
    return new ImportPositionIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("operateProcessIndex")
  public ProcessIndex getProcessIndex(final OperateProperties operateProperties) {
    return new ProcessIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public DecisionInstanceTemplate getDecisionInstanceTemplate(
      final OperateProperties operateProperties) {
    return new DecisionInstanceTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public EventTemplate getEventTemplate(final OperateProperties operateProperties) {
    return new EventTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("operateFlowNodeInstanceTemplate")
  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate(
      final OperateProperties operateProperties) {
    return new FlowNodeInstanceTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public IncidentTemplate getIncidentTemplate(final OperateProperties operateProperties) {
    return new IncidentTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public ListViewTemplate getListViewTemplate(final OperateProperties operateProperties) {
    return new ListViewTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public MessageTemplate getMessageTemplate(final OperateProperties operateProperties) {
    return new MessageTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public PostImporterQueueTemplate getPostImporterQueueTemplate(
      final OperateProperties operateProperties) {
    return new PostImporterQueueTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public SequenceFlowTemplate getSequenceFlowTemplate(final OperateProperties operateProperties) {
    return new SequenceFlowTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public JobTemplate getJobTemplate(final OperateProperties operateProperties) {
    return new JobTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("operateVariableTemplate")
  public VariableTemplate getVariableTemplate(final OperateProperties operateProperties) {
    return new VariableTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public OperationTemplate getOperationTemplate(final OperateProperties operateProperties) {
    return new OperationTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public BatchOperationTemplate getBatchOperationTemplate(
      final OperateProperties operateProperties) {
    return new BatchOperationTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public DraftTaskVariableTemplate getDraftTaskVariableTemplate(
      final OperateProperties operateProperties) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new DraftTaskVariableTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public FormIndex getFormIndex(final OperateProperties operateProperties) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new FormIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public TasklistMetricIndex getTasklistMetricIndex(final OperateProperties operateProperties) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new TasklistMetricIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("operateSnapshotTaskVariableTemplate")
  public SnapshotTaskVariableTemplate getOperateSnapshotTaskVariableTemplate(
      final OperateProperties operateProperties) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new SnapshotTaskVariableTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("tasklistSnapshotTaskVariableTemplate")
  public SnapshotTaskVariableTemplate getTasklistSnapshotTaskVariableTemplate(
      final OperateProperties operateProperties) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new SnapshotTaskVariableTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public TaskTemplate getTaskTemplate(final OperateProperties operateProperties) {
    // Just take the provided DatabaseInfo, no need to distinguish between Tasklist or Operate
    return new TaskTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("tasklistVariableTemplate")
  public VariableTemplate getTasklistVariableTemplate(final OperateProperties operateProperties) {
    return new VariableTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("tasklistFlowNodeInstanceTemplate")
  public FlowNodeInstanceTemplate getTasklistFlowNodeInstanceTemplate(
      final OperateProperties operateProperties) {
    return new FlowNodeInstanceTemplate(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean
  public TasklistImportPositionIndex getTasklistImportPositionIndex(
      final OperateProperties operateProperties) {
    return new TasklistImportPositionIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }

  @Bean("tasklistProcessIndex")
  public ProcessIndex getTasklistProcessIndex(final OperateProperties operateProperties) {
    return new ProcessIndex(
        operateProperties.getIndexPrefix(),
        operateProperties.getDatabase().equals(OperateProperties.ELASTIC_SEARCH));
  }
}
