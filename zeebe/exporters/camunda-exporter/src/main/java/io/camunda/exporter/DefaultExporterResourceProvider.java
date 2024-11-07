/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static java.util.Map.entry;

import io.camunda.exporter.cache.ExporterEntityCacheImpl;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.handlers.AuthorizationHandler;
import io.camunda.exporter.handlers.DecisionEvaluationHandler;
import io.camunda.exporter.handlers.DecisionHandler;
import io.camunda.exporter.handlers.DecisionRequirementsHandler;
import io.camunda.exporter.handlers.EmbeddedFormHandler;
import io.camunda.exporter.handlers.EventFromIncidentHandler;
import io.camunda.exporter.handlers.EventFromJobHandler;
import io.camunda.exporter.handlers.EventFromProcessInstanceHandler;
import io.camunda.exporter.handlers.EventFromProcessMessageSubscriptionHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceIncidentHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceProcessInstanceHandler;
import io.camunda.exporter.handlers.FormHandler;
import io.camunda.exporter.handlers.GroupCreatedUpdatedHandler;
import io.camunda.exporter.handlers.IncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromIncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromJobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewProcessInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewVariableFromVariableHandler;
import io.camunda.exporter.handlers.MappingCreatedHandler;
import io.camunda.exporter.handlers.MappingDeletedHandler;
import io.camunda.exporter.handlers.MetricFromProcessInstanceHandler;
import io.camunda.exporter.handlers.PostImporterQueueFromIncidentHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.exporter.handlers.SequenceFlowHandler;
import io.camunda.exporter.handlers.TaskCompletedMetricHandler;
import io.camunda.exporter.handlers.TenantCreateUpdateHandler;
import io.camunda.exporter.handlers.UserCreatedUpdatedHandler;
import io.camunda.exporter.handlers.UserDeletedHandler;
import io.camunda.exporter.handlers.UserTaskCompletionVariableHandler;
import io.camunda.exporter.handlers.UserTaskHandler;
import io.camunda.exporter.handlers.UserTaskJobBasedHandler;
import io.camunda.exporter.handlers.UserTaskProcessInstanceHandler;
import io.camunda.exporter.handlers.UserTaskVariableHandler;
import io.camunda.exporter.handlers.VariableHandler;
import io.camunda.exporter.handlers.operation.OperationFromIncidentHandler;
import io.camunda.exporter.handlers.operation.OperationFromProcessInstanceHandler;
import io.camunda.exporter.handlers.operation.OperationFromVariableDocumentHandler;
import io.camunda.exporter.utils.XMLUtil;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.MappingIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * This is the class where teams should make their components such as handlers, and index/index
 * template descriptors available
 */
public class DefaultExporterResourceProvider implements ExporterResourceProvider {

  private Map<? extends Class<? extends AbstractIndexDescriptor>, IndexDescriptor>
      indexDescriptorsMap;
  private Map<Class<? extends IndexTemplateDescriptor>, IndexTemplateDescriptor>
      templateDescriptorsMap;

  private Set<ExportHandler> exportHandlers;
  private boolean isElasticsearch;

  @Override
  public void init(
      final ExporterConfiguration configuration,
      final ExporterEntityCacheProvider entityCacheProvider) {
    final var globalPrefix = configuration.getIndex().getPrefix();
    isElasticsearch =
        ConnectionTypes.from(configuration.getConnect().getType())
            .equals(ConnectionTypes.ELASTICSEARCH);

    templateDescriptorsMap =
        Map.ofEntries(
            entry(ListViewTemplate.class, new ListViewTemplate(globalPrefix, isElasticsearch)),
            entry(VariableTemplate.class, new VariableTemplate(globalPrefix, isElasticsearch)),
            entry(
                PostImporterQueueTemplate.class,
                new PostImporterQueueTemplate(globalPrefix, isElasticsearch)),
            entry(
                FlowNodeInstanceTemplate.class,
                new FlowNodeInstanceTemplate(globalPrefix, isElasticsearch)),
            entry(IncidentTemplate.class, new IncidentTemplate(globalPrefix, isElasticsearch)),
            entry(
                SequenceFlowTemplate.class,
                new SequenceFlowTemplate(globalPrefix, isElasticsearch)),
            entry(
                DecisionInstanceTemplate.class,
                new DecisionInstanceTemplate(globalPrefix, isElasticsearch)),
            entry(EventTemplate.class, new EventTemplate(globalPrefix, isElasticsearch)),
            entry(TaskTemplate.class, new TaskTemplate(globalPrefix, isElasticsearch)),
            entry(OperationTemplate.class, new OperationTemplate(globalPrefix, isElasticsearch)),
            entry(
                BatchOperationTemplate.class,
                new BatchOperationTemplate(globalPrefix, isElasticsearch)));

    indexDescriptorsMap =
        Map.of(
            DecisionIndex.class,
            new DecisionIndex(globalPrefix, isElasticsearch),
            DecisionRequirementsIndex.class,
            new DecisionRequirementsIndex(globalPrefix, isElasticsearch),
            MetricIndex.class,
            new MetricIndex(globalPrefix, isElasticsearch),
            ProcessIndex.class,
            new ProcessIndex(globalPrefix, isElasticsearch),
            FormIndex.class,
            new FormIndex(globalPrefix, isElasticsearch),
            TasklistMetricIndex.class,
            new TasklistMetricIndex(globalPrefix, isElasticsearch),
            UserIndex.class,
            new UserIndex(globalPrefix, isElasticsearch),
            AuthorizationIndex.class,
            new AuthorizationIndex(globalPrefix, isElasticsearch),
            MappingIndex.class,
            new MappingIndex(globalPrefix, isElasticsearch),
            TenantIndex.class,
            new TenantIndex(globalPrefix, isElasticsearch),
            GroupIndex.class,
            new GroupIndex(globalPrefix, isElasticsearch));

    final var processCache =
        new ExporterEntityCacheImpl<Long, CachedProcessEntity>(
            10000,
            entityCacheProvider.getProcessCacheLoader(
                indexDescriptorsMap.get(ProcessIndex.class).getFullQualifiedName()));

    final var formCache =
        new ExporterEntityCacheImpl<String, CachedFormEntity>(
            10000,
            entityCacheProvider.getFormCacheLoader(
                indexDescriptorsMap.get(FormIndex.class).getFullQualifiedName()));

    exportHandlers =
        Set.of(
            new UserCreatedUpdatedHandler(
                indexDescriptorsMap.get(UserIndex.class).getFullQualifiedName()),
            new UserDeletedHandler(indexDescriptorsMap.get(UserIndex.class).getFullQualifiedName()),
            new AuthorizationHandler(
                indexDescriptorsMap.get(AuthorizationIndex.class).getFullQualifiedName()),
            new TenantCreateUpdateHandler(
                indexDescriptorsMap.get(TenantIndex.class).getFullQualifiedName()),
            new GroupCreatedUpdatedHandler(
                indexDescriptorsMap.get(GroupIndex.class).getFullQualifiedName()),
            new DecisionHandler(
                indexDescriptorsMap.get(DecisionIndex.class).getFullQualifiedName()),
            new ListViewProcessInstanceFromProcessInstanceHandler(
                templateDescriptorsMap.get(ListViewTemplate.class).getFullQualifiedName(),
                false,
                processCache),
            new ListViewFlowNodeFromIncidentHandler(
                templateDescriptorsMap.get(ListViewTemplate.class).getFullQualifiedName(), false),
            new ListViewFlowNodeFromJobHandler(
                templateDescriptorsMap.get(ListViewTemplate.class).getFullQualifiedName(), false),
            new ListViewFlowNodeFromProcessInstanceHandler(
                templateDescriptorsMap.get(ListViewTemplate.class).getFullQualifiedName(), false),
            new ListViewVariableFromVariableHandler(
                templateDescriptorsMap.get(ListViewTemplate.class).getFullQualifiedName(), false),
            new VariableHandler(
                templateDescriptorsMap.get(VariableTemplate.class).getFullQualifiedName(),
                configuration.getIndex().getVariableSizeThreshold()),
            new DecisionRequirementsHandler(
                indexDescriptorsMap.get(DecisionRequirementsIndex.class).getFullQualifiedName()),
            new PostImporterQueueFromIncidentHandler(
                templateDescriptorsMap.get(PostImporterQueueTemplate.class).getFullQualifiedName()),
            new FlowNodeInstanceIncidentHandler(
                templateDescriptorsMap.get(FlowNodeInstanceTemplate.class).getFullQualifiedName()),
            new FlowNodeInstanceProcessInstanceHandler(
                templateDescriptorsMap.get(FlowNodeInstanceTemplate.class).getFullQualifiedName()),
            new IncidentHandler(
                templateDescriptorsMap.get(IncidentTemplate.class).getFullQualifiedName(), false),
            new SequenceFlowHandler(
                templateDescriptorsMap.get(SequenceFlowTemplate.class).getFullQualifiedName()),
            new DecisionEvaluationHandler(
                templateDescriptorsMap.get(DecisionInstanceTemplate.class).getFullQualifiedName()),
            new ProcessHandler(
                indexDescriptorsMap.get(ProcessIndex.class).getFullQualifiedName(),
                new XMLUtil(),
                processCache),
            new MetricFromProcessInstanceHandler(
                indexDescriptorsMap.get(MetricIndex.class).getFullQualifiedName()),
            new TaskCompletedMetricHandler(
                indexDescriptorsMap.get(TasklistMetricIndex.class).getFullQualifiedName()),
            new EmbeddedFormHandler(
                indexDescriptorsMap.get(FormIndex.class).getFullQualifiedName(), new XMLUtil()),
            new FormHandler(
                indexDescriptorsMap.get(FormIndex.class).getFullQualifiedName(), formCache),
            new EventFromIncidentHandler(
                templateDescriptorsMap.get(EventTemplate.class).getFullQualifiedName(), false),
            new EventFromJobHandler(
                templateDescriptorsMap.get(EventTemplate.class).getFullQualifiedName(), false),
            new EventFromProcessInstanceHandler(
                templateDescriptorsMap.get(EventTemplate.class).getFullQualifiedName(), false),
            new EventFromProcessMessageSubscriptionHandler(
                templateDescriptorsMap.get(EventTemplate.class).getFullQualifiedName(), false),
            new UserTaskHandler(
                templateDescriptorsMap.get(TaskTemplate.class).getFullQualifiedName(), formCache),
            new UserTaskJobBasedHandler(
                templateDescriptorsMap.get(TaskTemplate.class).getFullQualifiedName(), formCache),
            new UserTaskProcessInstanceHandler(
                templateDescriptorsMap.get(TaskTemplate.class).getFullQualifiedName()),
            new UserTaskVariableHandler(
                templateDescriptorsMap.get(TaskTemplate.class).getFullQualifiedName(),
                configuration.getIndex().getVariableSizeThreshold()),
            new UserTaskCompletionVariableHandler(
                templateDescriptorsMap.get(TaskTemplate.class).getFullQualifiedName(),
                configuration.getIndex().getVariableSizeThreshold()),
            new OperationFromProcessInstanceHandler(
                templateDescriptorsMap.get(OperationTemplate.class).getFullQualifiedName()),
            new OperationFromVariableDocumentHandler(
                templateDescriptorsMap.get(OperationTemplate.class).getFullQualifiedName()),
            new OperationFromIncidentHandler(
                templateDescriptorsMap.get(OperationTemplate.class).getFullQualifiedName()),
            new MappingCreatedHandler(
                indexDescriptorsMap.get(MappingIndex.class).getFullQualifiedName()),
            new MappingDeletedHandler(
                indexDescriptorsMap.get(MappingIndex.class).getFullQualifiedName()));
  }

  @Override
  public Collection<IndexDescriptor> getIndexDescriptors() {
    return indexDescriptorsMap.values();
  }

  @Override
  public Collection<IndexTemplateDescriptor> getIndexTemplateDescriptors() {
    return templateDescriptorsMap.values();
  }

  @Override
  public <T extends IndexTemplateDescriptor> T getIndexTemplateDescriptor(
      final Class<T> descriptorClass) {
    return descriptorClass.cast(templateDescriptorsMap.get(descriptorClass));
  }

  @Override
  public Set<ExportHandler> getExportHandlers() {
    // Register all handlers here
    return exportHandlers;
  }
}
