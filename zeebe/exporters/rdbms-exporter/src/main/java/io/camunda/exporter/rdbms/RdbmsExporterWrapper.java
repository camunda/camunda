/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static io.camunda.db.rdbms.write.RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE;
import static io.camunda.db.rdbms.write.RdbmsWriterConfig.DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.exporter.rdbms.cache.RdbmsBatchOperationCacheLoader;
import io.camunda.exporter.rdbms.cache.RdbmsProcessCacheLoader;
import io.camunda.exporter.rdbms.handlers.DecisionDefinitionExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionInstanceExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionRequirementsExportHandler;
import io.camunda.exporter.rdbms.handlers.FlowNodeExportHandler;
import io.camunda.exporter.rdbms.handlers.FlowNodeInstanceIncidentExportHandler;
import io.camunda.exporter.rdbms.handlers.FormExportHandler;
import io.camunda.exporter.rdbms.handlers.GroupExportHandler;
import io.camunda.exporter.rdbms.handlers.IncidentExportHandler;
import io.camunda.exporter.rdbms.handlers.JobExportHandler;
import io.camunda.exporter.rdbms.handlers.MappingRuleExportHandler;
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
import io.camunda.exporter.rdbms.handlers.batchoperation.BatchOperationLifecycleManagementExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.IncidentBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceCancellationBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceMigrationBatchOperationExportHandler;
import io.camunda.exporter.rdbms.handlers.batchoperation.ProcessInstanceModificationBatchOperationExportHandler;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import java.time.Duration;
import java.util.Map;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public class RdbmsExporterWrapper implements Exporter {

  /** The partition on which all process deployments are published */
  public static final long PROCESS_DEFINITION_PARTITION = 1L;

  public static final String NAMESPACE = "camunda.rdbms.exporter.cache";
  private static final int DEFAULT_FLUSH_INTERVAL = 500;
  private static final int DEFAULT_MAX_QUEUE_SIZE = 1000;
  private static final int DEFAULT_CLEANUP_BATCH_SIZE = 1000;
  private static final long DEFAULT_MAX_CACHE_SIZE = 10_000;

  private static final String CONFIG_CACHE_PROCESS = "processCache";
  private static final String CONFIG_CACHE_BATCH_OPERATION = "batchOperationCache";

  private final RdbmsService rdbmsService;

  private RdbmsExporter exporter;

  private ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public RdbmsExporterWrapper(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public void configure(final Context context) {
    final var maxQueueSize = readMaxQueueSize(context);
    final int partitionId = context.getPartitionId();
    final RdbmsWriter rdbmsWriter =
        rdbmsService.createWriter(
            new RdbmsWriterConfig.Builder()
                .partitionId(partitionId)
                .maxQueueSize(maxQueueSize)
                .historyCleanupBatchSize(readCleanupBatchSize(context))
                .defaultHistoryTTL(readHistoryTTL(context))
                .minHistoryCleanupInterval(readMinHistoryCleanupInterval(context))
                .maxHistoryCleanupInterval(readMaxHistoryCleanupInterval(context))
                .batchOperationItemInsertBlockSize(readBatchOperationItemInsertBlockSize(context))
                .exportBatchOperationItemsOnCreation(
                    readExportBatchOperationItemsOnCreation(context))
                .build());

    final var builder =
        new RdbmsExporterConfig.Builder()
            .partitionId(partitionId)
            .flushInterval(readFlushInterval(context))
            .maxQueueSize(maxQueueSize)
            .rdbmsWriter(rdbmsWriter);

    processCache =
        new ExporterEntityCacheImpl<>(
            readMaxCacheSize(context, CONFIG_CACHE_PROCESS),
            new RdbmsProcessCacheLoader(rdbmsService.getProcessDefinitionReader()),
            new CaffeineCacheStatsCounter(NAMESPACE, "process", context.getMeterRegistry()));

    batchOperationCache =
        new ExporterEntityCacheImpl<>(
            readMaxCacheSize(context, CONFIG_CACHE_BATCH_OPERATION),
            new RdbmsBatchOperationCacheLoader(rdbmsService.getBatchOperationReader()),
            new CaffeineCacheStatsCounter(NAMESPACE, "batchOperation", context.getMeterRegistry()));

    createHandlers(partitionId, rdbmsWriter, builder);
    createBatchOperationHandlers(rdbmsWriter, builder);

    exporter = new RdbmsExporter(builder.build());
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

  private Duration readFlushInterval(final Context context) {
    return readDuration(context, "flushInterval", Duration.ofMillis(DEFAULT_FLUSH_INTERVAL));
  }

  private Duration readHistoryTTL(final Context context) {
    return readDuration(context, "defaultHistoryTTL", RdbmsWriterConfig.DEFAULT_HISTORY_TTL);
  }

  private Duration readMinHistoryCleanupInterval(final Context context) {
    return readDuration(
        context,
        "minHistoryCleanupInterval",
        RdbmsWriterConfig.DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL);
  }

  private Duration readMaxHistoryCleanupInterval(final Context context) {
    return readDuration(
        context,
        "maxHistoryCleanupInterval",
        RdbmsWriterConfig.DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL);
  }

  private int readMaxQueueSize(final Context context) {
    return readInt(context, "maxQueueSize", DEFAULT_MAX_QUEUE_SIZE);
  }

  private int readCleanupBatchSize(final Context context) {
    return readInt(context, "historyCleanupBatchSize", DEFAULT_CLEANUP_BATCH_SIZE);
  }

  private int readBatchOperationItemInsertBlockSize(final Context context) {
    return readInt(
        context,
        "batchOperationItemInsertBlockSize",
        DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE);
  }

  private boolean readExportBatchOperationItemsOnCreation(final Context context) {
    return readBoolean(
        context,
        "exportBatchOperationItemsOnCreation",
        DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION);
  }

  private Duration readDuration(
      final Context context, final String property, final Duration defaultValue) {
    final var arguments = context.getConfiguration().getArguments();
    if (arguments != null && arguments.containsKey(property)) {
      return Duration.parse((String) arguments.get(property));
    } else {
      return defaultValue;
    }
  }

  private int readInt(final Context context, final String property, final int defaultValue) {
    final var arguments = context.getConfiguration().getArguments();
    if (arguments != null && arguments.containsKey(property)) {
      return (Integer) arguments.get(property);
    } else {
      return defaultValue;
    }
  }

  private boolean readBoolean(
      final Context context, final String property, final boolean defaultValue) {
    final var arguments = context.getConfiguration().getArguments();
    if (arguments != null && arguments.containsKey(property)) {
      return (Boolean) arguments.get(property);
    } else {
      return defaultValue;
    }
  }

  private long readMaxCacheSize(final Context context, final String cacheConfigName) {
    final var arguments = context.getConfiguration().getArguments();
    if (arguments != null && arguments.containsKey(cacheConfigName)) {
      final var processCacheObject = arguments.get(cacheConfigName);
      if (processCacheObject instanceof final Map<?, ?> processCacheMap) {
        final var maxCacheSize = processCacheMap.get("maxCacheSize");
        if (maxCacheSize instanceof Number) {
          return ((Number) maxCacheSize).longValue();
        }
      }
    }
    return DEFAULT_MAX_CACHE_SIZE;
  }

  private void createHandlers(
      final long partitionId,
      final RdbmsWriter rdbmsWriter,
      final RdbmsExporterConfig.Builder builder) {

    if (partitionId == PROCESS_DEFINITION_PARTITION) {
      builder.withHandler(
          ValueType.PROCESS,
          new ProcessExportHandler(rdbmsWriter.getProcessDefinitionWriter(), processCache));
      builder.withHandler(
          ValueType.MAPPING_RULE, new MappingRuleExportHandler(rdbmsWriter.getMappingRuleWriter()));
      builder.withHandler(ValueType.TENANT, new TenantExportHandler(rdbmsWriter.getTenantWriter()));
      builder.withHandler(ValueType.ROLE, new RoleExportHandler(rdbmsWriter.getRoleWriter()));
      builder.withHandler(ValueType.USER, new UserExportHandler(rdbmsWriter.getUserWriter()));
      builder.withHandler(
          ValueType.AUTHORIZATION,
          new AuthorizationExportHandler(rdbmsWriter.getAuthorizationWriter()));
      builder.withHandler(
          ValueType.DECISION,
          new DecisionDefinitionExportHandler(rdbmsWriter.getDecisionDefinitionWriter()));
      builder.withHandler(
          ValueType.DECISION_REQUIREMENTS,
          new DecisionRequirementsExportHandler(rdbmsWriter.getDecisionRequirementsWriter()));
    }
    builder.withHandler(
        ValueType.DECISION_EVALUATION,
        new DecisionInstanceExportHandler(rdbmsWriter.getDecisionInstanceWriter()));
    builder.withHandler(ValueType.GROUP, new GroupExportHandler(rdbmsWriter.getGroupWriter()));
    builder.withHandler(
        ValueType.INCIDENT,
        new IncidentExportHandler(rdbmsWriter.getIncidentWriter(), processCache));
    builder.withHandler(
        ValueType.INCIDENT,
        new ProcessInstanceIncidentExportHandler(rdbmsWriter.getProcessInstanceWriter()));
    builder.withHandler(
        ValueType.INCIDENT,
        new FlowNodeInstanceIncidentExportHandler(rdbmsWriter.getFlowNodeInstanceWriter()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new ProcessInstanceExportHandler(
            rdbmsWriter.getProcessInstanceWriter(),
            rdbmsWriter.getHistoryCleanupService(),
            processCache));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new FlowNodeExportHandler(rdbmsWriter.getFlowNodeInstanceWriter(), processCache));
    builder.withHandler(
        ValueType.VARIABLE, new VariableExportHandler(rdbmsWriter.getVariableWriter()));
    builder.withHandler(
        ValueType.USER_TASK,
        new UserTaskExportHandler(rdbmsWriter.getUserTaskWriter(), processCache));
    builder.withHandler(ValueType.FORM, new FormExportHandler(rdbmsWriter.getFormWriter()));
    builder.withHandler(ValueType.JOB, new JobExportHandler(rdbmsWriter.getJobWriter()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new SequenceFlowExportHandler(rdbmsWriter.getSequenceFlowWriter()));
    builder.withHandler(
        ValueType.USAGE_METRIC, new UsageMetricExportHandler(rdbmsWriter.getUsageMetricWriter()));
  }

  private void createBatchOperationHandlers(
      final RdbmsWriter rdbmsWriter, final RdbmsExporterConfig.Builder builder) {
    builder.withHandler(
        ValueType.BATCH_OPERATION_CREATION,
        new BatchOperationCreatedExportHandler(
            rdbmsWriter.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.BATCH_OPERATION_CHUNK,
        new BatchOperationChunkExportHandler(rdbmsWriter.getBatchOperationWriter()));
    builder.withHandler(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        new BatchOperationLifecycleManagementExportHandler(rdbmsWriter.getBatchOperationWriter()));

    // Handlers per batch operation to track status
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new ProcessInstanceCancellationBatchOperationExportHandler(
            rdbmsWriter.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.INCIDENT,
        new IncidentBatchOperationExportHandler(
            rdbmsWriter.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        new ProcessInstanceMigrationBatchOperationExportHandler(
            rdbmsWriter.getBatchOperationWriter(), batchOperationCache));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        new ProcessInstanceModificationBatchOperationExportHandler(
            rdbmsWriter.getBatchOperationWriter(), batchOperationCache));
  }
}
