/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import io.camunda.exporter.rdbms.RdbmsExporter.Builder;
import io.camunda.exporter.rdbms.cache.RdbmsBatchOperationCacheLoader;
import io.camunda.exporter.rdbms.cache.RdbmsDecisionRequirementsCacheLoader;
import io.camunda.exporter.rdbms.cache.RdbmsProcessCacheLoader;
import io.camunda.exporter.rdbms.handlers.AuditLogExportHandler;
import io.camunda.exporter.rdbms.handlers.ClusterVariableExportHandler;
import io.camunda.exporter.rdbms.handlers.CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler;
import io.camunda.exporter.rdbms.handlers.CorrelatedMessageSubscriptionFromProcessMessageSubscriptionExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionDefinitionExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionInstanceExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionRequirementsExportHandler;
import io.camunda.exporter.rdbms.handlers.FlowNodeExportHandler;
import io.camunda.exporter.rdbms.handlers.FlowNodeInstanceIncidentExportHandler;
import io.camunda.exporter.rdbms.handlers.FormExportHandler;
import io.camunda.exporter.rdbms.handlers.GroupExportHandler;
import io.camunda.exporter.rdbms.handlers.HistoryDeletionDeletedHandler;
import io.camunda.exporter.rdbms.handlers.IncidentExportHandler;
import io.camunda.exporter.rdbms.handlers.JobExportHandler;
import io.camunda.exporter.rdbms.handlers.JobMetricsBatchExportHandler;
import io.camunda.exporter.rdbms.handlers.MappingRuleExportHandler;
import io.camunda.exporter.rdbms.handlers.MessageSubscriptionExportHandler;
import io.camunda.exporter.rdbms.handlers.ProcessExportHandler;
import io.camunda.exporter.rdbms.handlers.ProcessInstanceExportHandler;
import io.camunda.exporter.rdbms.handlers.ProcessInstanceIncidentExportHandler;
import io.camunda.exporter.rdbms.handlers.RoleExportHandler;
import io.camunda.exporter.rdbms.handlers.SequenceFlowExportHandler;
import io.camunda.exporter.rdbms.handlers.TenantExportHandler;
import io.camunda.exporter.rdbms.handlers.UsageMetricExportHandler;
import io.camunda.exporter.rdbms.handlers.UserExportHandler;
import io.camunda.exporter.rdbms.handlers.UserTaskExportHandler;
import io.camunda.exporter.rdbms.handlers.VariableExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.BatchOperationChunkExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.BatchOperationCreatedExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.BatchOperationInitializedExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.BatchOperationLifecycleManagementExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.DecisionInstanceHistoryDeletionBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.IncidentBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceCancellationBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceHistoryDeletionBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceMigrationBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceModificationBatchOperationExportHandler;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerRegistry;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import java.util.HashSet;
import java.util.Set;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public class RdbmsExporterWrapper implements Exporter {

  /** The partition on which all process deployments are published */
  public static final long PROCESS_DEFINITION_PARTITION = 1L;

  public static final String NAMESPACE = "camunda.rdbms.exporter.cache";

  private final RdbmsService rdbmsService;
  private final RdbmsSchemaManager rdbmsSchemaManager;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  private RdbmsExporter exporter;

  private ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private ExporterEntityCache<Long, CachedDecisionRequirementsEntity> decisionRequirementsCache;
  private ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public RdbmsExporterWrapper(
      final RdbmsService rdbmsService,
      final RdbmsSchemaManager rdbmsSchemaManager,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.rdbmsService = rdbmsService;
    this.rdbmsSchemaManager = rdbmsSchemaManager;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  @Override
  public void configure(final Context context) {
    final var config = context.getConfiguration().instantiate(ExporterConfiguration.class);
    config.validate(); // throws exception if configuration is invalid

    final int partitionId = context.getPartitionId();
    final var rdbmsWriterConfig =
        config.createRdbmsWriterConfig(partitionId, vendorDatabaseProperties);
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(rdbmsWriterConfig);

    final var builder =
        new RdbmsExporter.Builder()
            .partitionId(partitionId)
            .flushInterval(config.getFlushInterval())
            .queueSize(config.getQueueSize())
            .rdbmsWriter(rdbmsWriters);

    processCache =
        new ExporterEntityCacheImpl<>(
            config.getProcessCache().getMaxSize(),
            new RdbmsProcessCacheLoader(rdbmsService.getProcessDefinitionReader()),
            new CaffeineCacheStatsCounter(NAMESPACE, "process", context.getMeterRegistry()));

    decisionRequirementsCache =
        new ExporterEntityCacheImpl<>(
            config.getDecisionRequirementsCache().getMaxSize(),
            new RdbmsDecisionRequirementsCacheLoader(rdbmsService.getDecisionRequirementsReader()),
            new CaffeineCacheStatsCounter(
                NAMESPACE, "decisionRequirements", context.getMeterRegistry()));

    batchOperationCache =
        new ExporterEntityCacheImpl<>(
            config.getBatchOperationCache().getMaxSize(),
            new RdbmsBatchOperationCacheLoader(rdbmsService.getBatchOperationReader()),
            new CaffeineCacheStatsCounter(NAMESPACE, "batchOperation", context.getMeterRegistry()));

    final var historyCleanupService =
        new HistoryCleanupService(
            rdbmsWriterConfig, rdbmsWriters, rdbmsService.getProcessInstanceReader());
    builder.historyCleanupService(historyCleanupService);
    final var historyDeletionService =
        new HistoryDeletionService(
            rdbmsWriters,
            rdbmsService.getHistoryDeletionDbReader(),
            rdbmsService.getProcessInstanceReader(),
            rdbmsService.getDecisionInstanceReader(),
            new HistoryDeletionConfig(
                config.getHistoryDeletion().getDelayBetweenRuns(),
                config.getHistoryDeletion().getMaxDelayBetweenRuns(),
                config.getHistoryDeletion().getQueueBatchSize(),
                config.getHistoryDeletion().getDependentRowLimit()));
    builder.historyDeletionService(historyDeletionService);

    createHandlers(partitionId, rdbmsWriters, builder, config, historyCleanupService);
    createBatchOperationHandlers(rdbmsWriters, builder, historyCleanupService);

    exporter = builder.rdbmsSchemaManager(rdbmsSchemaManager).build();
  }

  @Override
  public void open(final Controller controller) {
    exporter.open(controller);
  }

  @Override
  public void close() {
    exporter.close();
  }

  @Override
  public void export(final Record<?> record) {
    exporter.export(record);
  }

  @Override
  public void purge() throws Exception {
    exporter.purge();
  }

  @VisibleForTesting("Allows verification of handler registration in tests")
  RdbmsExporter getExporter() {
    return exporter;
  }

  private void createHandlers(
      final int partitionId,
      final RdbmsWriters rdbmsWriters,
      final Builder builder,
      final ExporterConfiguration config,
      final HistoryCleanupService historyCleanupService) {

    if (partitionId == PROCESS_DEFINITION_PARTITION) {
      builder.withHandler(
          ValueType.PROCESS,
          new ProcessExportHandler(rdbmsWriters.getProcessDefinitionWriter(), processCache));
      builder.withHandler(
          ValueType.MAPPING_RULE,
          new MappingRuleExportHandler(rdbmsWriters.getMappingRuleWriter()));
      builder.withHandler(
          ValueType.TENANT, new TenantExportHandler(rdbmsWriters.getTenantWriter()));
      builder.withHandler(ValueType.ROLE, new RoleExportHandler(rdbmsWriters.getRoleWriter()));
      builder.withHandler(ValueType.USER, new UserExportHandler(rdbmsWriters.getUserWriter()));
      builder.withHandler(
          ValueType.AUTHORIZATION,
          new AuthorizationExportHandler(rdbmsWriters.getAuthorizationWriter()));
      builder.withHandler(
          ValueType.DECISION,
          new DecisionDefinitionExportHandler(
              rdbmsWriters.getDecisionDefinitionWriter(), decisionRequirementsCache));
      builder.withHandler(
          ValueType.DECISION_REQUIREMENTS,
          new DecisionRequirementsExportHandler(
              rdbmsWriters.getDecisionRequirementsWriter(), decisionRequirementsCache));
      builder.withHandler(ValueType.FORM, new FormExportHandler(rdbmsWriters.getFormWriter()));
    }

    builder.withHandler(
        ValueType.DECISION_EVALUATION,
        new DecisionInstanceExportHandler(rdbmsWriters.getDecisionInstanceWriter()));
    builder.withHandler(ValueType.GROUP, new GroupExportHandler(rdbmsWriters.getGroupWriter()));
    builder.withHandler(
        ValueType.INCIDENT,
        new IncidentExportHandler(
            rdbmsWriters.getIncidentWriter(), processCache, rdbmsWriters.getErrorMessageSize()));
    builder.withHandler(
        ValueType.INCIDENT,
        new ProcessInstanceIncidentExportHandler(rdbmsWriters.getProcessInstanceWriter()));
    builder.withHandler(
        ValueType.INCIDENT,
        new FlowNodeInstanceIncidentExportHandler(rdbmsWriters.getFlowNodeInstanceWriter()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new ProcessInstanceExportHandler(
            rdbmsWriters.getProcessInstanceWriter(),
            historyCleanupService,
            processCache,
            rdbmsWriters.getErrorMessageSize()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new FlowNodeExportHandler(
            rdbmsWriters.getFlowNodeInstanceWriter(),
            processCache,
            rdbmsWriters.getErrorMessageSize()));
    builder.withHandler(
        ValueType.VARIABLE, new VariableExportHandler(rdbmsWriters.getVariableWriter()));
    builder.withHandler(
        ValueType.CLUSTER_VARIABLE,
        new ClusterVariableExportHandler(rdbmsWriters.getClusterVariableWriter()));
    builder.withHandler(
        ValueType.USER_TASK,
        new UserTaskExportHandler(rdbmsWriters.getUserTaskWriter(), processCache));
    builder.withHandler(ValueType.JOB, new JobExportHandler(rdbmsWriters.getJobWriter()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new SequenceFlowExportHandler(rdbmsWriters.getSequenceFlowWriter()));
    builder.withHandler(
        ValueType.USAGE_METRIC,
        new UsageMetricExportHandler(
            rdbmsWriters.getUsageMetricWriter(), rdbmsWriters.getUsageMetricTUWriter()));
    builder.withHandler(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        new MessageSubscriptionExportHandler(rdbmsWriters.getMessageSubscriptionWriter()));
    builder.withHandler(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        new CorrelatedMessageSubscriptionFromProcessMessageSubscriptionExportHandler(
            rdbmsWriters.getCorrelatedMessageSubscriptionWriter()));
    builder.withHandler(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        new CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler(
            rdbmsWriters.getCorrelatedMessageSubscriptionWriter()));
    builder.withHandler(
        ValueType.HISTORY_DELETION,
        new HistoryDeletionDeletedHandler(rdbmsWriters.getHistoryDeletionWriter()));
    builder.withHandler(
        ValueType.JOB_METRICS_BATCH,
        new JobMetricsBatchExportHandler(rdbmsWriters.getJobMetricsBatchWriter()));

    if (config.getAuditLog().isEnabled()) {
      registerAuditLogHandlers(rdbmsWriters, builder, config, partitionId);
    }
  }

  private void createBatchOperationHandlers(
      final RdbmsWriters rdbmsWriters,
      final Builder builder,
      final HistoryCleanupService historyCleanupService) {
    builder.withHandler(
        ValueType.BATCH_OPERATION_CREATION,
        new BatchOperationCreatedExportHandler(
            rdbmsWriters.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.BATCH_OPERATION_INITIALIZATION,
        new BatchOperationInitializedExportHandler(rdbmsWriters.getBatchOperationWriter()));
    builder.withHandler(
        ValueType.BATCH_OPERATION_CHUNK,
        new BatchOperationChunkExportHandler(rdbmsWriters.getBatchOperationWriter()));
    builder.withHandler(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        new BatchOperationLifecycleManagementExportHandler(
            rdbmsWriters.getBatchOperationWriter(), historyCleanupService, batchOperationCache));

    // Handlers per batch operation to track status
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new ProcessInstanceCancellationBatchOperationExportHandler(
            rdbmsWriters.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.INCIDENT,
        new IncidentBatchOperationExportHandler(
            rdbmsWriters.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        new ProcessInstanceMigrationBatchOperationExportHandler(
            rdbmsWriters.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        new ProcessInstanceModificationBatchOperationExportHandler(
            rdbmsWriters.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.HISTORY_DELETION,
        new ProcessInstanceHistoryDeletionBatchOperationExportHandler(
            rdbmsWriters.getBatchOperationWriter(),
            batchOperationCache,
            BatchOperationType.DELETE_PROCESS_INSTANCE));
    builder.withHandler(
        ValueType.HISTORY_DELETION,
        new DecisionInstanceHistoryDeletionBatchOperationExportHandler(
            rdbmsWriters.getBatchOperationWriter(),
            batchOperationCache,
            BatchOperationType.DELETE_DECISION_INSTANCE));
  }

  private void registerAuditLogHandlers(
      final RdbmsWriters rdbmsWriters,
      final Builder builder,
      final ExporterConfiguration config,
      final int partitionId) {
    final Set<AuditLogTransformer<?>> transformers = new HashSet<>();
    transformers.addAll(AuditLogTransformerRegistry.createAllPartitionTransformers());

    if (partitionId == PROCESS_DEFINITION_PARTITION) {
      transformers.addAll(AuditLogTransformerRegistry.createPartitionSpecificTransformers());
    }

    transformers.forEach(
        transformer ->
            builder.withHandler(
                transformer.config().valueType(),
                new AuditLogExportHandler<>(
                    rdbmsWriters.getAuditLogWriter(),
                    vendorDatabaseProperties,
                    transformer,
                    config.getAuditLog())));
  }
}
