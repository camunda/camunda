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
import io.camunda.zeebe.exporter.api.ExporterException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ExporterConfiguration {
  private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMillis(500);
  private static final int DEFAULT_CLEANUP_BATCH_SIZE = 1000;
  private static final int DEFAULT_MAX_CACHE_SIZE = 10_000;

  private Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
  private int queueSize = RdbmsWriterConfig.DEFAULT_QUEUE_SIZE;

  private HistoryConfiguration history = new HistoryConfiguration();

  // batch operation configuration
  private boolean exportBatchOperationItemsOnCreation =
      RdbmsWriterConfig.DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION;
  private int batchOperationItemInsertBlockSize =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE;

  // caches
  private CacheConfiguration processCache = new CacheConfiguration();
  private CacheConfiguration batchOperationCache = new CacheConfiguration();

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

  public CacheConfiguration getBatchOperationCache() {
    return batchOperationCache;
  }

  public void setBatchOperationCache(final CacheConfiguration batchOperationCache) {
    this.batchOperationCache = batchOperationCache;
  }

  public void validate() {

    final List<String> errors = new ArrayList<>(history.validate());

    if (flushInterval.isNegative()) {
      errors.add(
          String.format("flushInterval must be a positive duration but was %s", flushInterval));
    }

    if (queueSize < 0) {
      errors.add(String.format("queueSize must be greater or equal 0 but was %d", queueSize));
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

  public RdbmsWriterConfig createRdbmsWriterConfig(final int partitionId) {
    final var historyConfig =
        new HistoryConfig.Builder()
            .defaultHistoryTTL(history.getDefaultHistoryTTL())
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
            .build();

    return new RdbmsWriterConfig.Builder()
        .partitionId(partitionId)
        .queueSize(queueSize)
        .batchOperationItemInsertBlockSize(batchOperationItemInsertBlockSize)
        .exportBatchOperationItemsOnCreation(exportBatchOperationItemsOnCreation)
        .history(historyConfig)
        .build();
  }

  private static void checkPositiveDuration(
      final Duration duration, final String name, final List<String> errors) {
    if (duration.isNegative() || duration.isZero()) {
      errors.add(String.format("%s must be a positive duration but was %s", name, duration));
    }
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

  public static class HistoryConfiguration {
    // history cleanup configuration
    private Duration defaultHistoryTTL = RdbmsWriterConfig.HistoryConfig.DEFAULT_HISTORY_TTL;
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
    private int historyCleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;

    public Duration getDefaultHistoryTTL() {
      return defaultHistoryTTL;
    }

    public void setDefaultHistoryTTL(final Duration defaultHistoryTTL) {
      this.defaultHistoryTTL = defaultHistoryTTL;
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
  }
}
