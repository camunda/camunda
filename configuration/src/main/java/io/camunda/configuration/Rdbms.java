/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class Rdbms extends SecondaryStorageDatabase {

  private String flushInterval;

  private Integer queueSize;

  private History history;

  private String usageMetricsCleanup;

  private String usageMetricsTTL;

  private Cache processCache;

  private Cache batchOperationCache;

  private boolean exportBatchOperationItemsOnCreation;
  private int batchOperationItemInsertBlockSize;

  @Override
  public String getUrl() {
    return url;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getIndexPrefix() {
    return indexPrefix;
  }

  @Override
  protected String databaseName() {
    return "rdbms";
  }

  public String getFlushInterval() {
    return flushInterval;
  }

  public void setFlushInterval(final String flushInterval) {
    this.flushInterval = flushInterval;
  }

  public Integer getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(final Integer queueSize) {
    this.queueSize = queueSize;
  }

  public History getHistory() {
    return history;
  }

  public void setHistory(final History history) {
    this.history = history;
  }

  public String getUsageMetricsCleanup() {
    return usageMetricsCleanup;
  }

  public void setUsageMetricsCleanup(final String usageMetricsCleanup) {
    this.usageMetricsCleanup = usageMetricsCleanup;
  }

  public String getUsageMetricsTTL() {
    return usageMetricsTTL;
  }

  public void setUsageMetricsTTL(final String usageMetricsTTL) {
    this.usageMetricsTTL = usageMetricsTTL;
  }

  public Cache getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final Cache processCache) {
    this.processCache = processCache;
  }

  public Cache getBatchOperationCache() {
    return batchOperationCache;
  }

  public void setBatchOperationCache(final Cache batchOperationCache) {
    this.batchOperationCache = batchOperationCache;
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

  public static class History {
    private String defaultHistoryTTL;
    private String defaultBatchOperationHistoryTTL;
    private String batchOperationCancelProcessInstanceHistoryTTL;
    private String batchOperationMigrateProcessInstanceHistoryTTL;
    private String batchOperationModifyProcessInstanceHistoryTTL;
    private String batchOperationResolveIncidentHistoryTTL;
    private String minHistoryCleanupInterval;
    private String maxHistoryCleanupInterval;
    private String historyCleanupBatchSize;

    public String getHistoryCleanupBatchSize() {
      return historyCleanupBatchSize;
    }

    public void setHistoryCleanupBatchSize(final String historyCleanupBatchSize) {
      this.historyCleanupBatchSize = historyCleanupBatchSize;
    }

    public String getMaxHistoryCleanupInterval() {
      return maxHistoryCleanupInterval;
    }

    public void setMaxHistoryCleanupInterval(final String maxHistoryCleanupInterval) {
      this.maxHistoryCleanupInterval = maxHistoryCleanupInterval;
    }

    public String getMinHistoryCleanupInterval() {
      return minHistoryCleanupInterval;
    }

    public void setMinHistoryCleanupInterval(final String minHistoryCleanupInterval) {
      this.minHistoryCleanupInterval = minHistoryCleanupInterval;
    }

    public String getBatchOperationResolveIncidentHistoryTTL() {
      return batchOperationResolveIncidentHistoryTTL;
    }

    public void setBatchOperationResolveIncidentHistoryTTL(
        final String batchOperationResolveIncidentHistoryTTL) {
      this.batchOperationResolveIncidentHistoryTTL = batchOperationResolveIncidentHistoryTTL;
    }

    public String getBatchOperationModifyProcessInstanceHistoryTTL() {
      return batchOperationModifyProcessInstanceHistoryTTL;
    }

    public void setBatchOperationModifyProcessInstanceHistoryTTL(
        final String batchOperationModifyProcessInstanceHistoryTTL) {
      this.batchOperationModifyProcessInstanceHistoryTTL =
          batchOperationModifyProcessInstanceHistoryTTL;
    }

    public String getBatchOperationMigrateProcessInstanceHistoryTTL() {
      return batchOperationMigrateProcessInstanceHistoryTTL;
    }

    public void setBatchOperationMigrateProcessInstanceHistoryTTL(
        final String batchOperationMigrateProcessInstanceHistoryTTL) {
      this.batchOperationMigrateProcessInstanceHistoryTTL =
          batchOperationMigrateProcessInstanceHistoryTTL;
    }

    public String getBatchOperationCancelProcessInstanceHistoryTTL() {
      return batchOperationCancelProcessInstanceHistoryTTL;
    }

    public void setBatchOperationCancelProcessInstanceHistoryTTL(
        final String batchOperationCancelProcessInstanceHistoryTTL) {
      this.batchOperationCancelProcessInstanceHistoryTTL =
          batchOperationCancelProcessInstanceHistoryTTL;
    }

    public String getDefaultBatchOperationHistoryTTL() {
      return defaultBatchOperationHistoryTTL;
    }

    public void setDefaultBatchOperationHistoryTTL(final String defaultBatchOperationHistoryTTL) {
      this.defaultBatchOperationHistoryTTL = defaultBatchOperationHistoryTTL;
    }

    public String getDefaultHistoryTTL() {
      return defaultHistoryTTL;
    }

    public void setDefaultHistoryTTL(final String defaultHistoryTTL) {
      this.defaultHistoryTTL = defaultHistoryTTL;
    }
  }

  public static class Cache {
    private int maxSize;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final int maxSize) {
      this.maxSize = maxSize;
    }
  }
}
