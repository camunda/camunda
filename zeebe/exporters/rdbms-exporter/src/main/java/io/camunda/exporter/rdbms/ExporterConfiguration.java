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

    if (defaultHistoryTTL.isNegative() || defaultHistoryTTL.isZero()) {
      errors.add(
          String.format(
              "defaultHistoryTTL must be a positive duration but was %s", defaultHistoryTTL));
    }

    if (minHistoryCleanupInterval.isNegative() || minHistoryCleanupInterval.isZero()) {
      errors.add(
          String.format(
              "minHistoryCleanupInterval must be a positive duration but was %s",
              minHistoryCleanupInterval));
    }

    if (maxHistoryCleanupInterval.isNegative() || maxHistoryCleanupInterval.isZero()) {
      errors.add(
          String.format(
              "maxHistoryCleanupInterval must be a positive duration but was %s",
              maxHistoryCleanupInterval));
    }

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
