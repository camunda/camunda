/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.InsertBatchingConfig;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExporterConfiguration {
  public static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMillis(500);
  public static final int DEFAULT_MAX_CACHE_SIZE = 10_000;
  private static final Logger LOG = LoggerFactory.getLogger(ExporterConfiguration.class);
  // AuditLog
  private AuditLogConfiguration auditLog = new AuditLogConfiguration();
  private HistoryDeletionConfiguration historyDeletion = new HistoryDeletionConfiguration();
  private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
  private int queueSize = RdbmsWriterConfig.DEFAULT_QUEUE_SIZE;
  private int queueMemoryLimit = RdbmsWriterConfig.DEFAULT_QUEUE_MEMORY_LIMIT;
  private HistoryConfiguration history = new HistoryConfiguration();
  // batch operation configuration
  private boolean exportBatchOperationItemsOnCreation =
      RdbmsWriterConfig.DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION;
  private int batchOperationItemInsertBlockSize =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE;
  // insert batching configuration
  private InsertBatchingConfiguration insertBatching = new InsertBatchingConfiguration();
  // caches
  private CacheConfiguration processCache = new CacheConfiguration();
  private CacheConfiguration decisionRequirementsCache = new CacheConfiguration();
  private CacheConfiguration batchOperationCache = new CacheConfiguration();

  public AuditLogConfiguration getAuditLog() {
    return auditLog;
  }

  public void setAuditLog(final AuditLogConfiguration auditLog) {
    this.auditLog = auditLog;
  }

  public HistoryDeletionConfiguration getHistoryDeletion() {
    return historyDeletion;
  }

  public void setHistoryDeletion(final HistoryDeletionConfiguration historyDeletion) {
    this.historyDeletion = historyDeletion;
  }

  public Duration getFlushInterval() {
    return flushInterval;
  }

  public void setFlushInterval(final Duration flushInterval) {
    this.flushInterval = flushInterval;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(final int queueSize) {
    this.queueSize = queueSize;
  }

  public int getQueueMemoryLimit() {
    return queueMemoryLimit;
  }

  public void setQueueMemoryLimit(final int queueMemoryLimit) {
    this.queueMemoryLimit = queueMemoryLimit;
  }

  public boolean isExportBatchOperationItemsOnCreation() {
    return exportBatchOperationItemsOnCreation;
  }

  public void setExportBatchOperationItemsOnCreation(
      final boolean exportBatchOperationItemsOnCreation) {
    this.exportBatchOperationItemsOnCreation = exportBatchOperationItemsOnCreation;
  }

  public int getBatchOperationItemInsertBlockSize() {
    return batchOperationItemInsertBlockSize;
  }

  public void setBatchOperationItemInsertBlockSize(final int batchOperationItemInsertBlockSize) {
    this.batchOperationItemInsertBlockSize = batchOperationItemInsertBlockSize;
  }

  public HistoryConfiguration getHistory() {
    return history;
  }

  public void setHistory(final HistoryConfiguration history) {
    this.history = history;
  }

  public CacheConfiguration getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final CacheConfiguration processCache) {
    this.processCache = processCache;
  }

  public CacheConfiguration getDecisionRequirementsCache() {
    return decisionRequirementsCache;
  }

  public void setDecisionRequirementsCache(final CacheConfiguration decisionRequirementsCache) {
    this.decisionRequirementsCache = decisionRequirementsCache;
  }

  public CacheConfiguration getBatchOperationCache() {
    return batchOperationCache;
  }

  public void setBatchOperationCache(final CacheConfiguration batchOperationCache) {
    this.batchOperationCache = batchOperationCache;
  }

  public InsertBatchingConfiguration getInsertBatching() {
    return insertBatching;
  }

  public void setInsertBatching(final InsertBatchingConfiguration insertBatching) {
    this.insertBatching = insertBatching;
  }

  public void validate() {

    final List<String> errors = new ArrayList<>(history.validate());
    errors.addAll(insertBatching.validate());

    if (flushInterval.isNegative()) {
      errors.add(
          String.format("flushInterval must be a positive duration but was %s", flushInterval));
    }

    if (queueSize < 0) {
      errors.add(String.format("queueSize must be greater or equal 0 but was %d", queueSize));
    }

    if (queueMemoryLimit < 0) {
      errors.add(
          String.format(
              "queueMemoryLimit must be greater or equal 0 but was %d", queueMemoryLimit));
    }

    if (batchOperationItemInsertBlockSize < 1) {
      errors.add(
          String.format(
              "batchOperationItemInsertBlockSize must be greater than 0 but was %d",
              batchOperationItemInsertBlockSize));
    }

    if (processCache.getMaxSize() < 1) {
      errors.add(
          String.format(
              "processCache.maxSize must be greater than 0 but was %d", processCache.getMaxSize()));
    }

    if (decisionRequirementsCache.getMaxSize() < 1) {
      errors.add(
          String.format(
              "decisionRequirementsCache.maxSize must be greater than 0 but was %d",
              decisionRequirementsCache.getMaxSize()));
    }

    if (batchOperationCache.getMaxSize() < 1) {
      errors.add(
          String.format(
              "batchOperationCache.maxSize must be greater than 0 but was %d",
              batchOperationCache.getMaxSize()));
    }

    if (!errors.isEmpty()) {
      throw new ExporterException(
          "Invalid RDBMS Exporter configuration: " + String.join(", ", errors));
    }
  }

  public RdbmsWriterConfig createRdbmsWriterConfig(
      final int partitionId, final VendorDatabaseProperties vendorDatabaseProperties) {
    final var historyConfig =
        new HistoryConfig.Builder()
            .defaultHistoryTTL(history.getDefaultHistoryTTL())
            .decisionInstanceTTL(history.getDecisionInstanceTTL())
            .batchOperationCancelProcessInstanceHistoryTTL(
                history.getBatchOperationCancelProcessInstanceHistoryTTL())
            .batchOperationMigrateProcessInstanceHistoryTTL(
                history.getBatchOperationMigrateProcessInstanceHistoryTTL())
            .batchOperationModifyProcessInstanceHistoryTTL(
                history.getBatchOperationModifyProcessInstanceHistoryTTL())
            .batchOperationResolveIncidentHistoryTTL(
                history.getBatchOperationResolveIncidentHistoryTTL())
            .minHistoryCleanupInterval(history.getMinHistoryCleanupInterval())
            .maxHistoryCleanupInterval(history.getMaxHistoryCleanupInterval())
            .historyCleanupBatchSize(history.getHistoryCleanupBatchSize())
            .historyCleanupProcessInstanceBatchSize(
                history.getHistoryCleanupProcessInstanceBatchSize())
            .usageMetricsCleanup(history.getUsageMetricsCleanup())
            .usageMetricsTTL(history.getUsageMetricsTTL())
            .build();

    return new RdbmsWriterConfig.Builder()
        .partitionId(partitionId)
        .queueSize(queueSize)
        .queueMemoryLimit(queueMemoryLimit)
        .batchOperationItemInsertBlockSize(batchOperationItemInsertBlockSize)
        .exportBatchOperationItemsOnCreation(exportBatchOperationItemsOnCreation)
        .history(historyConfig)
        .insertBatchingConfig(createInsertBatchingConfig(vendorDatabaseProperties))
        .build();
  }

  private InsertBatchingConfig createInsertBatchingConfig(
      final VendorDatabaseProperties vendorDatabaseProperties) {
    // If the database vendor doesn't support insert batching (e.g., Oracle 19c),
    // set all batch sizes to 1 to disable batching
    final int auditLogBatchSize;
    final int variableBatchSize;
    final int jobBatchSize;
    final int flowNodeBatchSize;

    if (!vendorDatabaseProperties.supportsInsertBatching()) {
      LOG.info(
          "Insert batching is not supported by the database vendor. Overriding insert batch sizes to 1 to disable batching.");
      auditLogBatchSize = 1;
      variableBatchSize = 1;
      jobBatchSize = 1;
      flowNodeBatchSize = 1;
    } else {
      auditLogBatchSize = insertBatching.getMaxAuditLogInsertBatchSize();
      variableBatchSize = insertBatching.getMaxVariableInsertBatchSize();
      jobBatchSize = insertBatching.getMaxJobInsertBatchSize();
      flowNodeBatchSize = insertBatching.getMaxFlowNodeInsertBatchSize();
    }

    return InsertBatchingConfig.builder()
        .auditLogInsertBatchSize(auditLogBatchSize)
        .variableInsertBatchSize(variableBatchSize)
        .jobInsertBatchSize(jobBatchSize)
        .flowNodeInsertBatchSize(flowNodeBatchSize)
        .build();
  }

  private static void checkPositiveDuration(
      final Duration duration, final String name, final List<String> errors) {
    if (duration.isNegative() || duration.isZero()) {
      errors.add(String.format("%s must be a positive duration but was %s", name, duration));
    }
  }

  @Override
  public String toString() {
    return "ExporterConfiguration{"
        + "auditLog="
        + auditLog
        + ", flushInterval="
        + flushInterval
        + ", queueSize="
        + queueSize
        + ", queueMemoryLimit="
        + queueMemoryLimit
        + ", history="
        + history
        + ", exportBatchOperationItemsOnCreation="
        + exportBatchOperationItemsOnCreation
        + ", batchOperationItemInsertBlockSize="
        + batchOperationItemInsertBlockSize
        + ", processCache="
        + processCache
        + ", decisionRequirementsCache="
        + decisionRequirementsCache
        + ", batchOperationCache="
        + batchOperationCache
        + '}';
  }

  public static class CacheConfiguration {
    private int maxSize = DEFAULT_MAX_CACHE_SIZE;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final int maxSize) {
      this.maxSize = maxSize;
    }
  }

  public static class InsertBatchingConfiguration {
    private int maxVariableInsertBatchSize =
        RdbmsWriterConfig.InsertBatchingConfig.DEFAULT_VARIABLE_INSERT_BATCH_SIZE;
    private int maxAuditLogInsertBatchSize =
        RdbmsWriterConfig.InsertBatchingConfig.DEFAULT_AUDIT_LOG_INSERT_BATCH_SIZE;
    private int maxJobInsertBatchSize =
        RdbmsWriterConfig.InsertBatchingConfig.DEFAULT_JOB_INSERT_BATCH_SIZE;
    private int maxFlowNodeInsertBatchSize =
        RdbmsWriterConfig.InsertBatchingConfig.DEFAULT_FLOW_NODE_INSERT_BATCH_SIZE;

    public int getMaxVariableInsertBatchSize() {
      return maxVariableInsertBatchSize;
    }

    public void setMaxVariableInsertBatchSize(final int maxVariableInsertBatchSize) {
      this.maxVariableInsertBatchSize = maxVariableInsertBatchSize;
    }

    public int getMaxAuditLogInsertBatchSize() {
      return maxAuditLogInsertBatchSize;
    }

    public void setMaxAuditLogInsertBatchSize(final int maxAuditLogInsertBatchSize) {
      this.maxAuditLogInsertBatchSize = maxAuditLogInsertBatchSize;
    }

    public int getMaxJobInsertBatchSize() {
      return maxJobInsertBatchSize;
    }

    public void setMaxJobInsertBatchSize(final int maxJobInsertBatchSize) {
      this.maxJobInsertBatchSize = maxJobInsertBatchSize;
    }

    public int getMaxFlowNodeInsertBatchSize() {
      return maxFlowNodeInsertBatchSize;
    }

    public void setMaxFlowNodeInsertBatchSize(final int maxFlowNodeInsertBatchSize) {
      this.maxFlowNodeInsertBatchSize = maxFlowNodeInsertBatchSize;
    }

    public List<String> validate() {
      final List<String> errors = new ArrayList<>();

      if (maxVariableInsertBatchSize < 1) {
        errors.add(
            String.format(
                "insertBatching.maxVariableInsertBatchSize must be greater than 0 but was %d",
                maxVariableInsertBatchSize));
      }

      if (maxAuditLogInsertBatchSize < 1) {
        errors.add(
            String.format(
                "insertBatching.maxAuditLogInsertBatchSize must be greater than 0 but was %d",
                maxAuditLogInsertBatchSize));
      }

      if (maxJobInsertBatchSize < 1) {
        errors.add(
            String.format(
                "insertBatching.maxJobInsertBatchSize must be greater than 0 but was %d",
                maxJobInsertBatchSize));
      }

      if (maxFlowNodeInsertBatchSize < 1) {
        errors.add(
            String.format(
                "insertBatching.maxFlowNodeInsertBatchSize must be greater than 0 but was %d",
                maxFlowNodeInsertBatchSize));
      }

      return errors;
    }
  }

  public static class HistoryConfiguration {
    // history cleanup configuration
    private Duration defaultHistoryTTL = RdbmsWriterConfig.HistoryConfig.DEFAULT_HISTORY_TTL;
    private Duration decisionInstanceTTL = RdbmsWriterConfig.HistoryConfig.DEFAULT_HISTORY_TTL;
    private Duration defaultBatchOperationHistoryTTL =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
    // specific history TTLs for batch operations
    private Duration batchOperationCancelProcessInstanceHistoryTTL =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
    private Duration batchOperationMigrateProcessInstanceHistoryTTL =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
    private Duration batchOperationModifyProcessInstanceHistoryTTL =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
    private Duration batchOperationResolveIncidentHistoryTTL =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
    private Duration minHistoryCleanupInterval =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL;
    private Duration maxHistoryCleanupInterval =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL;
    private int historyCleanupBatchSize =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_HISTORY_CLEANUP_BATCH_SIZE;
    private int historyCleanupProcessInstanceBatchSize =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_HISTORY_CLEANUP_PROCESS_INSTANCE_BATCH_SIZE;
    private Duration usageMetricsCleanup =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_USAGE_METRICS_CLEANUP;
    private Duration usageMetricsTTL = RdbmsWriterConfig.HistoryConfig.DEFAULT_USAGE_METRICS_TTL;

    public Duration getDefaultHistoryTTL() {
      return defaultHistoryTTL;
    }

    public void setDefaultHistoryTTL(final Duration defaultHistoryTTL) {
      this.defaultHistoryTTL = defaultHistoryTTL;
    }

    public Duration getDecisionInstanceTTL() {
      return decisionInstanceTTL;
    }

    public void setDecisionInstanceTTL(final Duration decisionInstanceTTL) {
      this.decisionInstanceTTL = decisionInstanceTTL;
    }

    public Duration getDefaultBatchOperationHistoryTTL() {
      return defaultBatchOperationHistoryTTL;
    }

    public void setDefaultBatchOperationHistoryTTL(final Duration defaultBatchOperationHistoryTTL) {
      this.defaultBatchOperationHistoryTTL = defaultBatchOperationHistoryTTL;
    }

    public Duration getBatchOperationCancelProcessInstanceHistoryTTL() {
      return batchOperationCancelProcessInstanceHistoryTTL;
    }

    public void setBatchOperationCancelProcessInstanceHistoryTTL(
        final Duration batchOperationCancelProcessInstanceHistoryTTL) {
      this.batchOperationCancelProcessInstanceHistoryTTL =
          batchOperationCancelProcessInstanceHistoryTTL;
    }

    public Duration getBatchOperationMigrateProcessInstanceHistoryTTL() {
      return batchOperationMigrateProcessInstanceHistoryTTL;
    }

    public void setBatchOperationMigrateProcessInstanceHistoryTTL(
        final Duration batchOperationMigrateProcessInstanceHistoryTTL) {
      this.batchOperationMigrateProcessInstanceHistoryTTL =
          batchOperationMigrateProcessInstanceHistoryTTL;
    }

    public Duration getBatchOperationModifyProcessInstanceHistoryTTL() {
      return batchOperationModifyProcessInstanceHistoryTTL;
    }

    public void setBatchOperationModifyProcessInstanceHistoryTTL(
        final Duration batchOperationModifyProcessInstanceHistoryTTL) {
      this.batchOperationModifyProcessInstanceHistoryTTL =
          batchOperationModifyProcessInstanceHistoryTTL;
    }

    public Duration getBatchOperationResolveIncidentHistoryTTL() {
      return batchOperationResolveIncidentHistoryTTL;
    }

    public void setBatchOperationResolveIncidentHistoryTTL(
        final Duration batchOperationResolveIncidentHistoryTTL) {
      this.batchOperationResolveIncidentHistoryTTL = batchOperationResolveIncidentHistoryTTL;
    }

    public Duration getMinHistoryCleanupInterval() {
      return minHistoryCleanupInterval;
    }

    public void setMinHistoryCleanupInterval(final Duration minHistoryCleanupInterval) {
      this.minHistoryCleanupInterval = minHistoryCleanupInterval;
    }

    public Duration getMaxHistoryCleanupInterval() {
      return maxHistoryCleanupInterval;
    }

    public void setMaxHistoryCleanupInterval(final Duration maxHistoryCleanupInterval) {
      this.maxHistoryCleanupInterval = maxHistoryCleanupInterval;
    }

    public int getHistoryCleanupBatchSize() {
      return historyCleanupBatchSize;
    }

    public void setHistoryCleanupBatchSize(final int historyCleanupBatchSize) {
      this.historyCleanupBatchSize = historyCleanupBatchSize;
    }

    public Duration getUsageMetricsCleanup() {
      return usageMetricsCleanup;
    }

    public void setUsageMetricsCleanup(final Duration usageMetricsCleanup) {
      this.usageMetricsCleanup = usageMetricsCleanup;
    }

    public Duration getUsageMetricsTTL() {
      return usageMetricsTTL;
    }

    public void setUsageMetricsTTL(final Duration usageMetricsTTL) {
      this.usageMetricsTTL = usageMetricsTTL;
    }

    public List<String> validate() {
      final List<String> errors = new ArrayList<>();

      checkPositiveDuration(defaultHistoryTTL, "defaultHistoryTTL", errors);
      checkPositiveDuration(
          defaultBatchOperationHistoryTTL, "defaultBatchOperationHistoryTTL", errors);
      checkPositiveDuration(
          batchOperationCancelProcessInstanceHistoryTTL,
          "batchOperationCancelProcessInstanceHistoryTTL",
          errors);
      checkPositiveDuration(
          batchOperationMigrateProcessInstanceHistoryTTL,
          "batchOperationMigrateProcessInstanceHistoryTTL",
          errors);
      checkPositiveDuration(
          batchOperationModifyProcessInstanceHistoryTTL,
          "batchOperationModifyProcessInstanceHistoryTTL",
          errors);
      checkPositiveDuration(
          batchOperationResolveIncidentHistoryTTL,
          "batchOperationResolveIncidentHistoryTTL",
          errors);
      checkPositiveDuration(minHistoryCleanupInterval, "minHistoryCleanupInterval", errors);
      checkPositiveDuration(maxHistoryCleanupInterval, "maxHistoryCleanupInterval", errors);
      checkPositiveDuration(usageMetricsCleanup, "usageMetricsCleanup", errors);
      checkPositiveDuration(usageMetricsTTL, "usageMetricsTTL", errors);

      if (maxHistoryCleanupInterval.compareTo(minHistoryCleanupInterval) <= 0) {
        errors.add(
            String.format(
                "maxHistoryCleanupInterval must be greater than minHistoryCleanupInterval but max was %s and min was %s",
                maxHistoryCleanupInterval, minHistoryCleanupInterval));
      }

      if (historyCleanupBatchSize < 1) {
        errors.add(
            String.format(
                "historyCleanupBatchSize must be greater than 0 but was %d",
                historyCleanupBatchSize));
      }

      return errors;
    }

    public int getHistoryCleanupProcessInstanceBatchSize() {
      return historyCleanupProcessInstanceBatchSize;
    }

    public void setHistoryCleanupProcessInstanceBatchSize(
        final int historyCleanupProcessInstanceBatchSize) {
      this.historyCleanupProcessInstanceBatchSize = historyCleanupProcessInstanceBatchSize;
    }
  }
}
