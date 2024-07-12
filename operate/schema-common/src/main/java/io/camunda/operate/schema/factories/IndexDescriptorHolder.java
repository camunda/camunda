/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.factories;

import io.camunda.operate.conditions.DatabaseInfoProvider;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.indices.IndexDescriptor;
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
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import java.util.ArrayList;
import java.util.List;

public class IndexDescriptorHolder {

  private static IndexDescriptorHolder _instance;

  private List<AbstractIndexDescriptor> indexDescriptors;

  private DecisionIndex decisionIndex;
  private DecisionRequirementsIndex decisionRequirementsIndex;
  private ImportPositionIndex importPositionIndex;
  private MetricIndex metricIndex;
  private MigrationRepositoryIndex migrationRepositoryIndex;
  private OperateWebSessionIndex operateWebSessionIndex;
  private ProcessIndex processIndex;
  private UserIndex userIndex;

  private List<TemplateDescriptor> templateDescriptors;

  private BatchOperationTemplate batchOperationTemplate;
  private DecisionInstanceTemplate decisionInstanceTemplate;
  private EventTemplate eventTemplate;
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  private IncidentTemplate incidentTemplate;
  private ListViewTemplate listViewTemplate;
  private MessageTemplate messageTemplate;
  private OperationTemplate operationTemplate;
  private PostImporterQueueTemplate postImporterQueueTemplate;
  private SequenceFlowTemplate sequenceFlowTemplate;
  private UserTaskTemplate userTaskTemplate;
  private VariableTemplate variableTemplate;

  public IndexDescriptorHolder init(
      final String indexPrefix, final DatabaseInfoProvider databaseInfoProvider) {
    // TODO synchronize?
    if (_instance != null) {
      // already initialized
      return _instance;
    }
    createIndexDescriptors(indexPrefix, databaseInfoProvider);
    createTemplateDescriptors(indexPrefix, databaseInfoProvider);
    _instance = this;
    return _instance;
  }

  private void createIndexDescriptors(
      final String indexPrefix, final DatabaseInfoProvider databaseInfoProvider) {
    indexDescriptors = new ArrayList<>();
    indexDescriptors.add(decisionIndex = new DecisionIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(
        decisionRequirementsIndex =
            new DecisionRequirementsIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(
        importPositionIndex = new ImportPositionIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(metricIndex = new MetricIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(
        migrationRepositoryIndex = new MigrationRepositoryIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(
        operateWebSessionIndex = new OperateWebSessionIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(processIndex = new ProcessIndex(indexPrefix, databaseInfoProvider));
    indexDescriptors.add(userIndex = new UserIndex(indexPrefix, databaseInfoProvider));
  }

  private void createTemplateDescriptors(
      final String indexPrefix, final DatabaseInfoProvider databaseInfoProvider) {
    templateDescriptors = new ArrayList<>();
    templateDescriptors.add(
        batchOperationTemplate = new BatchOperationTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        decisionInstanceTemplate = new DecisionInstanceTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(eventTemplate = new EventTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        flowNodeInstanceTemplate = new FlowNodeInstanceTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        incidentTemplate = new IncidentTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        listViewTemplate = new ListViewTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        messageTemplate = new MessageTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        operationTemplate = new OperationTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        postImporterQueueTemplate =
            new PostImporterQueueTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        sequenceFlowTemplate = new SequenceFlowTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        userTaskTemplate = new UserTaskTemplate(indexPrefix, databaseInfoProvider));
    templateDescriptors.add(
        variableTemplate = new VariableTemplate(indexPrefix, databaseInfoProvider));
  }

  public static IndexDescriptorHolder getInstance() {
    return _instance;
  }

  public List<AbstractIndexDescriptor> getIndexDescriptors() {
    checkInitialized();
    return indexDescriptors;
  }

  public List<TemplateDescriptor> getTemplateDescriptors() {
    checkInitialized();
    return templateDescriptors;
  }

  public List<IndexDescriptor> getIndexAndTemplateDescriptors() {
    checkInitialized();
    final List<IndexDescriptor> descriptors = new ArrayList<>();
    descriptors.addAll(getIndexDescriptors());
    descriptors.addAll(getTemplateDescriptors());
    return descriptors;
  }

  public DecisionIndex getDecisionIndex() {
    checkInitialized();
    return decisionIndex;
  }

  private void checkInitialized() {
    if (_instance == null) {
      new IllegalStateException("IndexDescriptorHolder is not yet initialized.");
    }
  }

  public DecisionRequirementsIndex getDecisionRequirementsIndex() {
    return decisionRequirementsIndex;
  }

  public ImportPositionIndex getImportPositionIndex() {
    checkInitialized();
    return importPositionIndex;
  }

  public MetricIndex getMetricIndex() {
    checkInitialized();
    return metricIndex;
  }

  public MigrationRepositoryIndex getMigrationRepositoryIndex() {
    checkInitialized();
    return migrationRepositoryIndex;
  }

  public OperateWebSessionIndex getOperateWebSessionIndex() {
    checkInitialized();
    return operateWebSessionIndex;
  }

  public ProcessIndex getProcessIndex() {
    checkInitialized();
    return processIndex;
  }

  public UserIndex getUserIndex() {
    checkInitialized();
    return userIndex;
  }

  public BatchOperationTemplate getBatchOperationTemplate() {
    checkInitialized();
    return batchOperationTemplate;
  }

  public DecisionInstanceTemplate getDecisionInstanceTemplate() {
    checkInitialized();
    return decisionInstanceTemplate;
  }

  public EventTemplate getEventTemplate() {
    checkInitialized();
    return eventTemplate;
  }

  public FlowNodeInstanceTemplate getFlowNodeInstanceTemplate() {
    checkInitialized();
    return flowNodeInstanceTemplate;
  }

  public IncidentTemplate getIncidentTemplate() {
    checkInitialized();
    return incidentTemplate;
  }

  public ListViewTemplate getListViewTemplate() {
    checkInitialized();
    return listViewTemplate;
  }

  public MessageTemplate getMessageTemplate() {
    checkInitialized();
    return messageTemplate;
  }

  public OperationTemplate getOperationTemplate() {
    checkInitialized();
    return operationTemplate;
  }

  public PostImporterQueueTemplate getPostImporterQueueTemplate() {
    checkInitialized();
    return postImporterQueueTemplate;
  }

  public SequenceFlowTemplate getSequenceFlowTemplate() {
    checkInitialized();
    return sequenceFlowTemplate;
  }

  public UserTaskTemplate getUserTaskTemplate() {
    checkInitialized();
    return userTaskTemplate;
  }

  public VariableTemplate getVariableTemplate() {
    checkInitialized();
    return variableTemplate;
  }
}
