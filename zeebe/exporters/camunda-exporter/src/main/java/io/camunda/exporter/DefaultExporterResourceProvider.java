/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.errorhandling.Error;
import io.camunda.exporter.errorhandling.ErrorHandler;
import io.camunda.exporter.errorhandling.ErrorHandlers;
import io.camunda.exporter.handlers.AuditLogHandler;
import io.camunda.exporter.handlers.AuthorizationCreatedUpdatedHandler;
import io.camunda.exporter.handlers.AuthorizationDeletedHandler;
import io.camunda.exporter.handlers.ClusterVariableCreatedHandler;
import io.camunda.exporter.handlers.ClusterVariableDeletedHandler;
import io.camunda.exporter.handlers.CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionHandler;
import io.camunda.exporter.handlers.CorrelatedMessageSubscriptionFromProcessMessageSubscriptionHandler;
import io.camunda.exporter.handlers.DecisionEvaluationHandler;
import io.camunda.exporter.handlers.DecisionHandler;
import io.camunda.exporter.handlers.DecisionRequirementsHandler;
import io.camunda.exporter.handlers.EmbeddedFormHandler;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceFromIncidentHandler;
import io.camunda.exporter.handlers.FlowNodeInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.FormHandler;
import io.camunda.exporter.handlers.GroupCreatedUpdatedHandler;
import io.camunda.exporter.handlers.GroupDeletedHandler;
import io.camunda.exporter.handlers.GroupEntityAddedHandler;
import io.camunda.exporter.handlers.GroupEntityRemovedHandler;
import io.camunda.exporter.handlers.HistoryDeletionDeletedHandler;
import io.camunda.exporter.handlers.IncidentHandler;
import io.camunda.exporter.handlers.JobBatchMetricsExportedHandler;
import io.camunda.exporter.handlers.JobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromIncidentHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromJobHandler;
import io.camunda.exporter.handlers.ListViewFlowNodeFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewProcessInstanceFromProcessInstanceHandler;
import io.camunda.exporter.handlers.ListViewVariableFromVariableHandler;
import io.camunda.exporter.handlers.MappingRuleCreatedUpdatedHandler;
import io.camunda.exporter.handlers.MappingRuleDeletedHandler;
import io.camunda.exporter.handlers.MessageSubscriptionFromProcessMessageSubscriptionHandler;
import io.camunda.exporter.handlers.MigratedVariableHandler;
import io.camunda.exporter.handlers.PostImporterQueueFromIncidentHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.exporter.handlers.RoleCreateUpdateHandler;
import io.camunda.exporter.handlers.RoleDeletedHandler;
import io.camunda.exporter.handlers.RoleMemberAddedHandler;
import io.camunda.exporter.handlers.RoleMemberRemovedHandler;
import io.camunda.exporter.handlers.SequenceFlowDeletedHandler;
import io.camunda.exporter.handlers.SequenceFlowHandler;
import io.camunda.exporter.handlers.TenantCreateUpdateHandler;
import io.camunda.exporter.handlers.TenantDeletedHandler;
import io.camunda.exporter.handlers.TenantEntityAddedHandler;
import io.camunda.exporter.handlers.TenantEntityRemovedHandler;
import io.camunda.exporter.handlers.UsageMetricExportedHandler;
import io.camunda.exporter.handlers.UserCreatedUpdatedHandler;
import io.camunda.exporter.handlers.UserDeletedHandler;
import io.camunda.exporter.handlers.UserTaskCompletionVariableHandler;
import io.camunda.exporter.handlers.UserTaskHandler;
import io.camunda.exporter.handlers.UserTaskJobBasedHandler;
import io.camunda.exporter.handlers.UserTaskProcessInstanceHandler;
import io.camunda.exporter.handlers.UserTaskVariableHandler;
import io.camunda.exporter.handlers.VariableHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedItemHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationCreatedHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationInitializedHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationLifecycleManagementHandler;
import io.camunda.exporter.handlers.batchoperation.ProcessDefinitionHistoryDeletionOperationHandler;
import io.camunda.exporter.handlers.batchoperation.ProcessInstanceCancellationOperationHandler;
import io.camunda.exporter.handlers.batchoperation.ProcessInstanceHistoryDeletionOperationHandler;
import io.camunda.exporter.handlers.batchoperation.ProcessInstanceMigrationOperationHandler;
import io.camunda.exporter.handlers.batchoperation.ProcessInstanceModificationOperationHandler;
import io.camunda.exporter.handlers.batchoperation.ResolveIncidentOperationHandler;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromChunkItemHandler;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromIncidentResolutionOperationHandler;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromProcessInstanceCancellationOperationHandler;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromProcessInstanceMigrationOperationHandler;
import io.camunda.exporter.handlers.batchoperation.listview.ListViewFromProcessInstanceModificationOperationHandler;
import io.camunda.exporter.handlers.operation.OperationFromHistoryDeletionHandler;
import io.camunda.exporter.handlers.operation.OperationFromIncidentHandler;
import io.camunda.exporter.handlers.operation.OperationFromProcessInstanceHandler;
import io.camunda.exporter.handlers.operation.OperationFromVariableDocumentHandler;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuthorizationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.BatchOperationCreationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.BatchOperationLifecycleManagementAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.DecisionAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.DecisionEvaluationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.DecisionRequirementsRecordAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.FormAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.GroupAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.GroupEntityAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.IncidentResolutionAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.MappingRuleAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceCancelAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceCreationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceMigrationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ProcessInstanceModificationAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.ResourceAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.RoleAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.RoleEntityAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.TenantAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.TenantEntityAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.UserAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.UserTaskAuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.VariableAddUpdateAuditLogTransformer;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * This is the class where teams should make their components such as handlers, and index/index
 * template descriptors available
 */
public class DefaultExporterResourceProvider implements ExporterResourceProvider {
  public static final String NAMESPACE = "zeebe.camunda.exporter.cache";

  /** The partition on which all process deployments and identity entities are published */
  public static final int PROCESS_DEFINITION_PARTITION = 1;

  private IndexDescriptors indexDescriptors;
  private Set<ExportHandler<?, ?>> exportHandlers;
  private Map<String, ErrorHandler> indicesWithCustomErrorHandlers;
  private ExporterEntityCacheImpl<String, CachedBatchOperationEntity> batchOperationCache;
  private ExporterEntityCacheImpl<String, CachedFormEntity> formCache;
  private ExporterEntityCacheImpl<Long, CachedProcessEntity> processCache;
  private ExporterEntityCacheImpl<Long, CachedDecisionRequirementsEntity> decisionRequirementsCache;

  @Override
  public void init(
      final ExporterConfiguration configuration,
      final ExporterEntityCacheProvider entityCacheProvider,
      final Context context,
      final ExporterMetadata exporterMetadata,
      final ObjectMapper objectMapper) {
    final var globalPrefix = configuration.getConnect().getIndexPrefix();
    final var isElasticsearch =
        ConnectionTypes.isElasticSearch(configuration.getConnect().getType());
    indexDescriptors = new IndexDescriptors(globalPrefix, isElasticsearch);
    final var meterRegistry = context.getMeterRegistry();
    final var partitionId = context.getPartitionId();

    batchOperationCache =
        new ExporterEntityCacheImpl<>(
            configuration.getBatchOperationCache().getMaxCacheSize(),
            entityCacheProvider.getBatchOperationCacheLoader(
                indexDescriptors
                    .get(BatchOperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new CaffeineCacheStatsCounter(NAMESPACE, "batchOperation", meterRegistry));

    processCache =
        new ExporterEntityCacheImpl<>(
            configuration.getProcessCache().getMaxCacheSize(),
            entityCacheProvider.getProcessCacheLoader(
                indexDescriptors.get(ProcessIndex.class).getShardedFullQualifiedName(partitionId)),
            new CaffeineCacheStatsCounter(NAMESPACE, "process", meterRegistry));

    decisionRequirementsCache =
        new ExporterEntityCacheImpl<>(
            configuration.getDecisionRequirementsCache().getMaxCacheSize(),
            entityCacheProvider.getDecisionRequirementsCacheLoader(
                indexDescriptors
                    .get(DecisionRequirementsIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new CaffeineCacheStatsCounter(NAMESPACE, "decisionRequirements", meterRegistry));

    formCache =
        new ExporterEntityCacheImpl<>(
            configuration.getFormCache().getMaxCacheSize(),
            entityCacheProvider.getFormCacheLoader(
                indexDescriptors.get(FormIndex.class).getShardedFullQualifiedName(partitionId)),
            new CaffeineCacheStatsCounter(NAMESPACE, "form", meterRegistry));

    exportHandlers = new LinkedHashSet<>();
    exportHandlers.addAll(
        ImmutableSet.of(
            new RoleCreateUpdateHandler(
                indexDescriptors.get(RoleIndex.class).getShardedFullQualifiedName(partitionId)),
            new RoleDeletedHandler(
                indexDescriptors.get(RoleIndex.class).getShardedFullQualifiedName(partitionId)),
            new RoleMemberAddedHandler(
                indexDescriptors.get(RoleIndex.class).getShardedFullQualifiedName(partitionId)),
            new RoleMemberRemovedHandler(
                indexDescriptors.get(RoleIndex.class).getShardedFullQualifiedName(partitionId)),
            new UserCreatedUpdatedHandler(
                indexDescriptors.get(UserIndex.class).getShardedFullQualifiedName(partitionId)),
            new UserDeletedHandler(
                indexDescriptors.get(UserIndex.class).getShardedFullQualifiedName(partitionId)),
            new AuthorizationCreatedUpdatedHandler(
                indexDescriptors
                    .get(AuthorizationIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new AuthorizationDeletedHandler(
                indexDescriptors
                    .get(AuthorizationIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new TenantCreateUpdateHandler(
                indexDescriptors.get(TenantIndex.class).getShardedFullQualifiedName(partitionId)),
            new TenantDeletedHandler(
                indexDescriptors.get(TenantIndex.class).getShardedFullQualifiedName(partitionId)),
            new TenantEntityAddedHandler(
                indexDescriptors.get(TenantIndex.class).getShardedFullQualifiedName(partitionId)),
            new TenantEntityRemovedHandler(
                indexDescriptors.get(TenantIndex.class).getShardedFullQualifiedName(partitionId)),
            new GroupCreatedUpdatedHandler(
                indexDescriptors.get(GroupIndex.class).getShardedFullQualifiedName(partitionId)),
            new GroupEntityAddedHandler(
                indexDescriptors.get(GroupIndex.class).getShardedFullQualifiedName(partitionId)),
            new GroupEntityRemovedHandler(
                indexDescriptors.get(GroupIndex.class).getShardedFullQualifiedName(partitionId)),
            new GroupDeletedHandler(
                indexDescriptors.get(GroupIndex.class).getShardedFullQualifiedName(partitionId)),
            new DecisionHandler(
                indexDescriptors.get(DecisionIndex.class).getShardedFullQualifiedName(partitionId),
                decisionRequirementsCache),
            new ListViewProcessInstanceFromProcessInstanceHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                processCache),
            new ListViewFlowNodeFromIncidentHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new ListViewFlowNodeFromJobHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new ListViewFlowNodeFromProcessInstanceHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new ListViewVariableFromVariableHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new ClusterVariableCreatedHandler(
                indexDescriptors
                    .get(ClusterVariableIndex.class)
                    .getShardedFullQualifiedName(partitionId),
                configuration.getIndex().getVariableSizeThreshold()),
            new ClusterVariableDeletedHandler(
                indexDescriptors
                    .get(ClusterVariableIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new VariableHandler(
                indexDescriptors
                    .get(VariableTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                configuration.getIndex().getVariableSizeThreshold()),
            new DecisionRequirementsHandler(
                indexDescriptors
                    .get(DecisionRequirementsIndex.class)
                    .getShardedFullQualifiedName(partitionId),
                decisionRequirementsCache),
            new PostImporterQueueFromIncidentHandler(
                indexDescriptors
                    .get(PostImporterQueueTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new FlowNodeInstanceFromIncidentHandler(
                indexDescriptors
                    .get(FlowNodeInstanceTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new FlowNodeInstanceFromProcessInstanceHandler(
                indexDescriptors
                    .get(FlowNodeInstanceTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                processCache),
            new IncidentHandler(
                indexDescriptors
                    .get(IncidentTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                processCache),
            new SequenceFlowHandler(
                indexDescriptors
                    .get(SequenceFlowTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new SequenceFlowDeletedHandler(
                indexDescriptors
                    .get(SequenceFlowTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new DecisionEvaluationHandler(
                indexDescriptors
                    .get(DecisionInstanceTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new ProcessHandler(
                indexDescriptors.get(ProcessIndex.class).getShardedFullQualifiedName(partitionId),
                processCache),
            new EmbeddedFormHandler(
                indexDescriptors.get(FormIndex.class).getShardedFullQualifiedName(partitionId)),
            new FormHandler(
                indexDescriptors.get(FormIndex.class).getShardedFullQualifiedName(partitionId),
                formCache),
            new HistoryDeletionDeletedHandler(
                indexDescriptors
                    .get(HistoryDeletionIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new MessageSubscriptionFromProcessMessageSubscriptionHandler(
                indexDescriptors
                    .get(MessageSubscriptionTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                exporterMetadata),
            new UserTaskHandler(
                indexDescriptors.get(TaskTemplate.class).getShardedFullQualifiedName(partitionId),
                formCache,
                processCache,
                exporterMetadata),
            new UserTaskJobBasedHandler(
                indexDescriptors.get(TaskTemplate.class).getShardedFullQualifiedName(partitionId),
                formCache,
                processCache,
                exporterMetadata,
                objectMapper),
            new UserTaskProcessInstanceHandler(
                indexDescriptors.get(TaskTemplate.class).getShardedFullQualifiedName(partitionId)),
            new UserTaskVariableHandler(
                indexDescriptors.get(TaskTemplate.class).getShardedFullQualifiedName(partitionId),
                configuration.getIndex().getVariableSizeThreshold()),
            new UserTaskCompletionVariableHandler(
                indexDescriptors
                    .get(SnapshotTaskVariableTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                configuration.getIndex().getVariableSizeThreshold(),
                objectMapper),
            new OperationFromProcessInstanceHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new OperationFromVariableDocumentHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new OperationFromIncidentHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new OperationFromHistoryDeletionHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new MappingRuleCreatedUpdatedHandler(
                indexDescriptors
                    .get(MappingRuleIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new MappingRuleDeletedHandler(
                indexDescriptors
                    .get(MappingRuleIndex.class)
                    .getShardedFullQualifiedName(partitionId)),
            new JobHandler(
                indexDescriptors.get(JobTemplate.class).getShardedFullQualifiedName(partitionId)),
            new MigratedVariableHandler(
                indexDescriptors
                    .get(VariableTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            // Batch Operation Handler
            new BatchOperationCreatedHandler(
                indexDescriptors
                    .get(BatchOperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new BatchOperationInitializedHandler(
                indexDescriptors
                    .get(BatchOperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new BatchOperationLifecycleManagementHandler(
                indexDescriptors
                    .get(BatchOperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new BatchOperationChunkCreatedHandler(
                indexDescriptors
                    .get(BatchOperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new ProcessInstanceCancellationOperationHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ProcessInstanceMigrationOperationHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ProcessInstanceModificationOperationHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ResolveIncidentOperationHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ProcessInstanceHistoryDeletionOperationHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ProcessDefinitionHistoryDeletionOperationHandler(
                indexDescriptors
                    .get(OperationTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ListViewFromProcessInstanceCancellationOperationHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ListViewFromProcessInstanceMigrationOperationHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ListViewFromProcessInstanceModificationOperationHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new ListViewFromIncidentResolutionOperationHandler(
                indexDescriptors
                    .get(ListViewTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                batchOperationCache),
            new UsageMetricExportedHandler(
                indexDescriptors
                    .get(UsageMetricTemplate.class)
                    .getShardedFullQualifiedName(partitionId),
                indexDescriptors
                    .get(UsageMetricTUTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new JobBatchMetricsExportedHandler(
                indexDescriptors
                    .get(JobMetricsBatchTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionHandler(
                indexDescriptors
                    .get(CorrelatedMessageSubscriptionTemplate.class)
                    .getShardedFullQualifiedName(partitionId)),
            new CorrelatedMessageSubscriptionFromProcessMessageSubscriptionHandler(
                indexDescriptors
                    .get(CorrelatedMessageSubscriptionTemplate.class)
                    .getShardedFullQualifiedName(partitionId))));

    if (configuration.getAuditLog().isEnabled()) {
      addAuditLogHandlers(configuration.getAuditLog(), partitionId);
    }

    if (configuration.getBatchOperation().isExportItemsOnCreation()) {
      // only add this handler when the items are exported on creation
      exportHandlers.add(
          new BatchOperationChunkCreatedItemHandler(
              indexDescriptors
                  .get(OperationTemplate.class)
                  .getShardedFullQualifiedName(partitionId),
              batchOperationCache));
      exportHandlers.add(
          new ListViewFromChunkItemHandler(
              indexDescriptors
                  .get(ListViewTemplate.class)
                  .getShardedFullQualifiedName(partitionId)));
    }

    indicesWithCustomErrorHandlers =
        Map.of(
            indexDescriptors.get(OperationTemplate.class).getShardedFullQualifiedName(partitionId),
            ErrorHandlers.IGNORE_DOCUMENT_DOES_NOT_EXIST);
  }

  @Override
  public void reset() {
    // clean up all references
    indexDescriptors = null;
    if (exportHandlers != null) {
      exportHandlers.clear();
      exportHandlers = null;
    }
    if (batchOperationCache != null) {
      batchOperationCache.clear();
      batchOperationCache = null;
    }
    if (formCache != null) {
      formCache.clear();
      formCache = null;
    }
    if (processCache != null) {
      processCache.clear();
      processCache = null;
    }
    if (decisionRequirementsCache != null) {
      decisionRequirementsCache.clear();
      decisionRequirementsCache = null;
    }
  }

  @Override
  public Collection<IndexDescriptor> getIndexDescriptors() {
    return indexDescriptors.indices();
  }

  @VisibleForTesting
  void setIndexDescriptors(final IndexDescriptors indexDescriptors) {
    this.indexDescriptors = indexDescriptors;
  }

  @Override
  public Collection<IndexTemplateDescriptor> getIndexTemplateDescriptors() {
    return indexDescriptors.templates();
  }

  @Override
  public <T extends IndexTemplateDescriptor> T getIndexTemplateDescriptor(
      final Class<T> descriptorClass) {
    return indexDescriptors.get(descriptorClass);
  }

  @Override
  public <T extends IndexDescriptor> T getIndexDescriptor(final Class<T> descriptorClass) {
    return indexDescriptors.get(descriptorClass);
  }

  @Override
  public Set<ExportHandler<?, ?>> getExportHandlers() {
    // Register all handlers here
    return Collections.unmodifiableSet(exportHandlers);
  }

  @Override
  public BiConsumer<String, Error> getCustomErrorHandlers() {
    return (index, error) -> {
      indicesWithCustomErrorHandlers.getOrDefault(index, ErrorHandlers.THROWING).handle(error);
    };
  }

  @Override
  public ExporterEntityCacheImpl<Long, CachedProcessEntity> getProcessCache() {
    return processCache;
  }

  @Override
  public ExporterEntityCacheImpl<Long, CachedDecisionRequirementsEntity>
      getDecisionRequirementsCache() {
    return decisionRequirementsCache;
  }

  @Override
  public ExporterEntityCacheImpl<String, CachedFormEntity> getFormCache() {
    return formCache;
  }

  private void addAuditLogHandlers(final AuditLogConfiguration auditLog, final int partitionId) {
    final var indexName =
        (indexDescriptors.get(AuditLogTemplate.class).getShardedFullQualifiedName(partitionId));
    final var auditLogBuilder = AuditLogHandler.builder(indexName, auditLog);

    if (partitionId == PROCESS_DEFINITION_PARTITION) {
      auditLogBuilder
          .addHandler(new AuthorizationAuditLogTransformer())
          .addHandler(new BatchOperationCreationAuditLogTransformer())
          .addHandler(new DecisionRequirementsRecordAuditLogTransformer())
          .addHandler(new DecisionAuditLogTransformer())
          .addHandler(new FormAuditLogTransformer())
          .addHandler(new GroupAuditLogTransformer())
          .addHandler(new GroupEntityAuditLogTransformer())
          .addHandler(new MappingRuleAuditLogTransformer())
          .addHandler(new ProcessAuditLogTransformer())
          .addHandler(new ResourceAuditLogTransformer())
          .addHandler(new RoleAuditLogTransformer())
          .addHandler(new RoleEntityAuditLogTransformer())
          .addHandler(new TenantAuditLogTransformer())
          .addHandler(new TenantEntityAuditLogTransformer())
          .addHandler(new UserAuditLogTransformer());
    }

    auditLogBuilder
        .addHandler(new BatchOperationLifecycleManagementAuditLogTransformer())
        .addHandler(new DecisionEvaluationAuditLogTransformer())
        .addHandler(new IncidentResolutionAuditLogTransformer())
        .addHandler(new ProcessInstanceCancelAuditLogTransformer())
        .addHandler(new ProcessInstanceCreationAuditLogTransformer())
        .addHandler(new ProcessInstanceMigrationAuditLogTransformer())
        .addHandler(new ProcessInstanceModificationAuditLogTransformer())
        .addHandler(new UserTaskAuditLogTransformer())
        .addHandler(new VariableAddUpdateAuditLogTransformer())
        .build()
        .forEach(exportHandlers::add);
  }
}
