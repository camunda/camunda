/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.RdbmsWriterConfig;
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

  // history cleanup configuration
  private Duration defaultHistoryTTL = RdbmsWriterConfig.DEFAULT_HISTORY_TTL;
  private Duration defaultBatchOperationHistoryTTL =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
  // specific history TTLs for batch operations
  private Duration batchOperationCancelProcessInstanceHistoryTTL =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
  private Duration batchOperationMigrateProcessInstanceHistoryTTL =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
  private Duration batchOperationModifyProcessInstanceHistoryTTL =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
  private Duration batchOperationResolveIncidentHistoryTTL =
      RdbmsWriterConfig.DEFAULT_BATCH_OPERATION_HISTORY_TTL;
  private Duration minHistoryCleanupInterval =
      RdbmsWriterConfig.DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL;
  private Duration maxHistoryCleanupInterval =
      RdbmsWriterConfig.DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL;
  private int historyCleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;

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

  public Duration getCancelProcessInstanceHistoryTTL() {
    return batchOperationCancelProcessInstanceHistoryTTL;
  }

  public void setCancelProcessInstanceHistoryTTL(
      final Duration batchOperationCancelProcessInstanceHistoryTTL) {
    this.batchOperationCancelProcessInstanceHistoryTTL =
        batchOperationCancelProcessInstanceHistoryTTL;
  }

  public Duration getMigrateProcessInstanceHistoryTTL() {
    return batchOperationMigrateProcessInstanceHistoryTTL;
  }

  public void setMigrateProcessInstanceHistoryTTL(
      final Duration batchOperationMigrateProcessInstanceHistoryTTL) {
    this.batchOperationMigrateProcessInstanceHistoryTTL =
        batchOperationMigrateProcessInstanceHistoryTTL;
  }

  public Duration getModifyProcessInstanceHistoryTTL() {
    return batchOperationModifyProcessInstanceHistoryTTL;
  }

  public void setModifyProcessInstanceHistoryTTL(
      final Duration batchOperationModifyProcessInstanceHistoryTTL) {
    this.batchOperationModifyProcessInstanceHistoryTTL =
        batchOperationModifyProcessInstanceHistoryTTL;
  }

  public Duration getResolveIncidentHistoryTTL() {
    return batchOperationResolveIncidentHistoryTTL;
  }

  public void setResolveIncidentHistoryTTL(final Duration batchOperationResolveIncidentHistoryTTL) {
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
    final List<String> errors = new ArrayList<>();

    if (flushInterval.isNegative()) {
      errors.add(
          String.format("flushInterval must be a positive duration but was %s", flushInterval));
    }

    if (queueSize < 0) {
      errors.add(String.format("queueSize must be greater or equal 0 but was %d", queueSize));
    }

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
        batchOperationResolveIncidentHistoryTTL, "batchOperationResolveIncidentHistoryTTL", errors);
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
    return new RdbmsWriterConfig.Builder()
        .partitionId(partitionId)
        .queueSize(queueSize)
        .defaultHistoryTTL(defaultHistoryTTL)
        .batchOperationCancelProcessInstanceHistoryTTL(
            batchOperationCancelProcessInstanceHistoryTTL)
        .batchOperationMigrateProcessInstanceHistoryTTL(
            batchOperationMigrateProcessInstanceHistoryTTL)
        .batchOperationModifyProcessInstanceHistoryTTL(
            batchOperationModifyProcessInstanceHistoryTTL)
        .batchOperationResolveIncidentHistoryTTL(batchOperationResolveIncidentHistoryTTL)
        .minHistoryCleanupInterval(minHistoryCleanupInterval)
        .maxHistoryCleanupInterval(maxHistoryCleanupInterval)
        .historyCleanupBatchSize(historyCleanupBatchSize)
        .batchOperationItemInsertBlockSize(batchOperationItemInsertBlockSize)
        .exportBatchOperationItemsOnCreation(exportBatchOperationItemsOnCreation)
        .build();
  }

  private void checkPositiveDuration(
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
}
