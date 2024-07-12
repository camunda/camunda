/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import io.camunda.operate.conditions.DatabaseInfoProvider;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.MessageTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IndexTemplateDescriptorsConfigurator {

  @Bean
  public DecisionIndex getDecisionIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new DecisionIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public DecisionRequirementsIndex getDecisionRequirementsIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new DecisionRequirementsIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public ImportPositionIndex getImportPositionIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new ImportPositionIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public MetricIndex getMetricIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new MetricIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public MigrationRepositoryIndex getMigrationRepositoryIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new MigrationRepositoryIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public OperateWebSessionIndex getOperateWebSessionIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new OperateWebSessionIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public ProcessIndex getProcessIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new ProcessIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public UserIndex getUserIndex(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new UserIndex(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public BatchOperationTemplate getBatchOperationTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new BatchOperationTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public DecisionInstanceTemplate getDecisionInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new DecisionInstanceTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public EventTemplate getEventTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new EventTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new FlowNodeInstanceTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public IncidentTemplate getIncidentTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new IncidentTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public ListViewTemplate getListViewTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new ListViewTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public MessageTemplate getMessageTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new MessageTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public OperationTemplate getOperationTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new OperationTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public PostImporterQueueTemplate getPostImporterQueueTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new PostImporterQueueTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public SequenceFlowTemplate getSequenceFlowTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new SequenceFlowTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public UserTaskTemplate getUserTaskTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new UserTaskTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }

  @Bean
  public VariableTemplate getVariableTemplate(
      final OperateProperties operateProperties, final DatabaseInfoProvider databaseInfoProvider) {
    return new VariableTemplate(
        operateProperties.getIndexPrefix(databaseInfoProvider.getCurrent()), databaseInfoProvider);
  }
}
