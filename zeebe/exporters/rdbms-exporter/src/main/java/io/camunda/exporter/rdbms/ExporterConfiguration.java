/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryConfig;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.InsertBatchingConfig;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateConfiguration;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ExporterConfiguration {
  public static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMillis(500);
  public static final int DEFAULT_MAX_CACHE_SIZE = 10_000;
  // AuditLog
  private AuditLogConfiguration auditLog = new AuditLogConfiguration();
  private HistoryDeletionConfiguration historyDeletion = new HistoryDeletionConfiguration();
  // WaitState
  private WaitStateConfiguration waitState = new WaitStateConfiguration();
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
  private ReplicationConfiguration asyncReplication = new ReplicationConfiguration();
  private ExtensionPropertyConfiguration extensionProperties = new ExtensionPropertyConfiguration();

  public AuditLogConfiguration getAuditLog() {
    return auditLog;
  }

  public void setAuditLog(final AuditLogConfiguration auditLog) {
    this.auditLog = auditLog;
  }

  public WaitStateConfiguration getWaitState() {
    return waitState;
  }

  public void setWaitState(final WaitStateConfiguration waitState) {
    this.waitState = waitState;
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

  public ReplicationConfiguration getAsyncReplication() {
    return asyncReplication;
  }

  public void setAsyncReplication(final ReplicationConfiguration asyncReplication) {
    this.asyncReplication = asyncReplication;
  }

  public ExtensionPropertyConfiguration getExtensionProperties() {
    return extensionProperties;
  }

  public void setExtensionProperties(final ExtensionPropertyConfiguration extensionProperties) {
    this.extensionProperties = extensionProperties;
  }

  public void validate() {

    final List<String> errors = new ArrayList<>(history.validate());
    errors.addAll(insertBatching.validate());
    errors.addAll(asyncReplication.validate());

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
      final int partitionId, final String physicalTenantId, final InstantSource clock) {
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
            .jobBatchMetricsCleanupInterval(history.getJobBatchMetricsCleanup())
            .jobBatchMetricsTTL(history.getJobBatchMetricsTTL())
            .maxHistoryCleanupUsage(history.getMaxHistoryCleanupUsage())
            .build();

    return new RdbmsWriterConfig.Builder()
        .partitionId(partitionId)
        .physicalTenantId(physicalTenantId)
        .queueSize(queueSize)
        .queueMemoryLimit(queueMemoryLimit)
        .batchOperationItemInsertBlockSize(batchOperationItemInsertBlockSize)
        .exportBatchOperationItemsOnCreation(exportBatchOperationItemsOnCreation)
        .history(historyConfig)
        .insertBatchingConfig(createInsertBatchingConfig())
        .clock(clock)
        .build();
  }

  private InsertBatchingConfig createInsertBatchingConfig() {
    return InsertBatchingConfig.builder()
        .auditLogInsertBatchSize(insertBatching.getMaxAuditLogInsertBatchSize())
        .variableInsertBatchSize(insertBatching.getMaxVariableInsertBatchSize())
        .jobInsertBatchSize(insertBatching.getMaxJobInsertBatchSize())
        .flowNodeInsertBatchSize(insertBatching.getMaxFlowNodeInsertBatchSize())
        .build();
  }

  private static void checkPositiveDuration(
      final Duration duration, final String name, final List<String> errors) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
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
        + ", asyncReplication="
        + asyncReplication
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
    private Duration jobBatchMetricsCleanup =
        HistoryConfig.DEFAULT_JOB_METRICS_BATCH_CLEANUP_INTERVAL;
    private Duration jobBatchMetricsTTL = HistoryConfig.DEFAULT_HISTORY_TTL;
    private double maxHistoryCleanupUsage =
        RdbmsWriterConfig.HistoryConfig.DEFAULT_MAX_HISTORY_CLEANUP_USAGE;

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

    public Duration getJobBatchMetricsCleanup() {
      return jobBatchMetricsCleanup;
    }

    public void setJobBatchMetricsCleanup(final Duration jobBatchMetricsCleanup) {
      this.jobBatchMetricsCleanup = jobBatchMetricsCleanup;
    }

    public Duration getJobBatchMetricsTTL() {
      return jobBatchMetricsTTL;
    }

    public void setJobBatchMetricsTTL(final Duration jobBatchMetricsTTL) {
      this.jobBatchMetricsTTL = jobBatchMetricsTTL;
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
      checkPositiveDuration(jobBatchMetricsCleanup, "jobBatchMetricsCleanup", errors);
      checkPositiveDuration(jobBatchMetricsTTL, "jobBatchMetricsTTL", errors);

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

      if (!Double.isFinite(maxHistoryCleanupUsage)
          || maxHistoryCleanupUsage <= 0
          || maxHistoryCleanupUsage > 1) {
        errors.add(
            String.format(
                "maxHistoryCleanupUsage must be between 0 (exclusive) and 1 (inclusive) but was %s",
                maxHistoryCleanupUsage));
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

    public double getMaxHistoryCleanupUsage() {
      return maxHistoryCleanupUsage;
    }

    public void setMaxHistoryCleanupUsage(final double maxHistoryCleanupUsage) {
      this.maxHistoryCleanupUsage = maxHistoryCleanupUsage;
    }
  }

  public static class ReplicationConfiguration {
    public static final boolean DEFAULT_ENABLED = false;
    public static final ReplicationType DEFAULT_TYPE = ReplicationType.LOG_SEQ;
    public static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(15);
    public static final Duration DEFAULT_MAX_LAG = Duration.ofMinutes(15);
    public static final int DEFAULT_MIN_SYNC_REPLICAS = 1;
    public static final boolean DEFAULT_PAUSE_ON_MAX_LAG_EXCEEDED = false;
    public static final int DEFAULT_QUEUE_CAPACITY = 8192;
    public static final Duration DEFAULT_QUEUE_DEBOUNCE_TIME = Duration.ofSeconds(5);

    private boolean enabled = DEFAULT_ENABLED;
    private ReplicationType type = DEFAULT_TYPE;
    private Duration pollingInterval = DEFAULT_POLLING_INTERVAL;
    private int minSyncReplicas = DEFAULT_MIN_SYNC_REPLICAS;
    private Duration maxLag = DEFAULT_MAX_LAG;
    private boolean pauseOnMaxLagExceeded = DEFAULT_PAUSE_ON_MAX_LAG_EXCEEDED;
    private Duration delay;
    private Duration queueDebounceTime = DEFAULT_QUEUE_DEBOUNCE_TIME;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    private RegionAwarenessConfiguration regionAwareness = new RegionAwarenessConfiguration();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(final boolean enabled) {
      this.enabled = enabled;
    }

    public ReplicationType getType() {
      return type;
    }

    public void setType(final ReplicationType type) {
      this.type = type;
    }

    public Duration getPollingInterval() {
      return pollingInterval;
    }

    public void setPollingInterval(final Duration pollingInterval) {
      this.pollingInterval = pollingInterval;
    }

    /**
     * The number of replicas required to confirm a position. Ignored when {@link
     * #regionAwareness}.{@link RegionAwarenessConfiguration#isEnabled() isEnabled()} is {@code
     * true} - each declared region enforces its own {@link RegionConfiguration#getMinReplicas()}
     * instead.
     */
    public int getMinSyncReplicas() {
      return minSyncReplicas;
    }

    public void setMinSyncReplicas(final int minSyncReplicas) {
      this.minSyncReplicas = minSyncReplicas;
    }

    public Duration getMaxLag() {
      return maxLag;
    }

    public void setMaxLag(final Duration maxLag) {
      this.maxLag = maxLag;
    }

    public boolean isPauseOnMaxLagExceeded() {
      return pauseOnMaxLagExceeded;
    }

    public void setPauseOnMaxLagExceeded(final boolean pauseOnMaxLagExceeded) {
      this.pauseOnMaxLagExceeded = pauseOnMaxLagExceeded;
    }

    public Duration getDelay() {
      return delay;
    }

    public void setDelay(final Duration delay) {
      this.delay = delay;
    }

    public Duration getQueueDebounceTime() {
      return queueDebounceTime;
    }

    public void setQueueDebounceTime(final Duration queueDebounceTime) {
      this.queueDebounceTime = queueDebounceTime;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(final int queueCapacity) {
      this.queueCapacity = queueCapacity;
    }

    public RegionAwarenessConfiguration getRegionAwareness() {
      return regionAwareness;
    }

    public void setRegionAwareness(final RegionAwarenessConfiguration regionAwareness) {
      this.regionAwareness = regionAwareness;
    }

    public List<String> validate() {
      final List<String> errors = new ArrayList<>();

      if (!enabled) {
        return errors;
      }

      if (minSyncReplicas <= 0) {
        errors.add(
            String.format(
                "asyncReplication.minSyncReplicas must be greater 0 but was %d", minSyncReplicas));
      }

      // queueCapacity, queueDebounceTime, pollingInterval, and maxLag apply to every
      // replication type, so they are validated unconditionally.
      checkNonNegativeDuration(queueDebounceTime, "asyncReplication.queueDebounceTime", errors);
      if (queueCapacity <= 0) {
        errors.add(
            String.format(
                "asyncReplication.queueCapacity must be greater 0 but was %d", queueCapacity));
      }
      checkPositiveDuration(pollingInterval, "asyncReplication.pollingInterval", errors);
      checkPositiveDuration(maxLag, "asyncReplication.maxLag", errors);

      if (type == ReplicationType.DELAY) {
        checkPositiveDuration(delay, "asyncReplication.delay", errors);
      }
      errors.addAll(regionAwareness.validate());
      return errors;
    }

    private static void checkNonNegativeDuration(
        final Duration duration, final String name, final List<String> errors) {
      if (duration == null || duration.isNegative()) {
        errors.add(String.format("%s must be a non-negative duration but was %s", name, duration));
      }
    }

    public enum ReplicationType {
      LOG_SEQ,
      TIME_LAG,
      DELAY
    }

    /**
     * Optional region-aware replication quorum, for topologies where a flat {@link
     * #minSyncReplicas} can't express per-region redundancy requirements (e.g. "at least 2 of 3
     * nodes healthy in each of 3 regions"). Every declared {@link RegionConfiguration} is
     * mandatory: if any one of them doesn't meet its own {@code minReplicas}, the position stays
     * unconfirmed / the exporter pauses, even if the other regions are fully healthy.
     */
    public static class RegionAwarenessConfiguration {
      public static final boolean DEFAULT_ENABLED = false;

      private boolean enabled = DEFAULT_ENABLED;
      private String primaryRegion;
      private List<RegionConfiguration> regions = new ArrayList<>();

      public boolean isEnabled() {
        return enabled;
      }

      public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
      }

      /**
       * The name of the region hosting the primary, matching one of {@link #regions}'s {@link
       * RegionConfiguration#getName()}. When set, that region's quorum gets one synthetic,
       * always-confirmed entry credited to the primary, so its {@code minReplicas} can be sized as
       * the desired total healthy node count (primary + secondaries) rather than secondaries only.
       * Optional - leave unset if no region should get primary credit.
       */
      public String getPrimaryRegion() {
        return primaryRegion;
      }

      public void setPrimaryRegion(final String primaryRegion) {
        this.primaryRegion = primaryRegion;
      }

      public List<RegionConfiguration> getRegions() {
        return regions;
      }

      public void setRegions(final List<RegionConfiguration> regions) {
        this.regions = regions;
      }

      public List<String> validate() {
        final List<String> errors = new ArrayList<>();
        if (!enabled) {
          return errors;
        }
        if (regions.isEmpty()) {
          errors.add("asyncReplication.regionAwareness.regions must not be empty when enabled");
          return errors;
        }

        final Set<String> names = new HashSet<>();
        for (final RegionConfiguration region : regions) {
          final String name = region.getName();
          if (name == null || name.isBlank()) {
            errors.add("asyncReplication.regionAwareness.regions[].name must not be blank");
          } else if (!names.add(name)) {
            errors.add(
                String.format(
                    "asyncReplication.regionAwareness.regions[].name '%s' is declared more than"
                        + " once",
                    name));
          }
          if (region.getPattern() == null || region.getPattern().isBlank()) {
            errors.add(
                String.format(
                    "asyncReplication.regionAwareness.regions[%s].pattern must not be blank",
                    name));
          } else {
            try {
              Pattern.compile(region.getPattern());
            } catch (final PatternSyntaxException e) {
              errors.add(
                  String.format(
                      "asyncReplication.regionAwareness.regions[%s].pattern is not a valid regex:"
                          + " %s",
                      name, e.getMessage()));
            }
          }
          if (region.getMinReplicas() < 1) {
            errors.add(
                String.format(
                    "asyncReplication.regionAwareness.regions[%s].minReplicas must be at least 1"
                        + " but was %d",
                    name, region.getMinReplicas()));
          }
        }
        if (primaryRegion != null && !names.contains(primaryRegion)) {
          errors.add(
              String.format(
                  "asyncReplication.regionAwareness.primaryRegion '%s' does not match any"
                      + " declared region",
                  primaryRegion));
        }
        return errors;
      }
    }

    /**
     * A single region: replicas whose label (see {@code replicaLabel} in {@link
     * io.camunda.db.rdbms.read.replication.ReplicationStatus}) matches {@link #pattern} are grouped
     * into this region and counted against its own {@link #minReplicas}.
     */
    public static class RegionConfiguration {
      private String name;
      private String pattern;
      private int minReplicas;

      public String getName() {
        return name;
      }

      public void setName(final String name) {
        this.name = name;
      }

      /** A regex matched against a replica's label; the first matching region wins. */
      public String getPattern() {
        return pattern;
      }

      public void setPattern(final String pattern) {
        this.pattern = pattern;
      }

      public int getMinReplicas() {
        return minReplicas;
      }

      public void setMinReplicas(final int minReplicas) {
        this.minReplicas = minReplicas;
      }
    }
  }
}
