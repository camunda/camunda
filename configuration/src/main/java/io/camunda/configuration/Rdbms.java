/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class Rdbms extends SecondaryStorageDatabase {

  /** The interval at which the exporters execution queue is flushed. */
  private String flushInterval;

  /** The maximum size of the exporters execution queue before it is flushed to the database. */
  private Integer queueSize;

  /** History cleanup configuration. */
  private History history;

  /** Process definition cache configuration. Defines the size of the process definition cache. */
  private Cache processCache;

  /** Batch operation cache configuration. Defines the size of the batch operation cache. */
  private Cache batchOperationCache;

  /**
   * If true, batch operation items are exported to the database when the batch operation is created
   * (status = ACTIVE). If false, the items are created on demand when they have been processed.
   * When set to true, this ensures that the items are available when the batch operation is
   * created, but it may lead to a delay in the creation of the batch operation if there are many
   * items to create.
   */
  private boolean exportBatchOperationItemsOnCreation;

  /**
   * The number of batch operation items to insert in a single batched SQL when creating the items
   * for a batch operation. This is only relevant when exportBatchOperationItemsOnCreation is set to
   * true.
   */
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

    /**
     * The default time to live for all camunda entities that support history time to live.
     * Specified in Java Duration format.
     */
    private String defaultHistoryTTL;

    /** The default time to live for all batch operations. Specified in Java Duration format. */
    private String defaultBatchOperationHistoryTTL;

    /**
     * The default time to live for cancel process instance batch operations. Specified in Java
     * Duration format.
     */
    private String batchOperationCancelProcessInstanceHistoryTTL;

    /**
     * The default time to live for migrate process instance batch operations. Specified in Java
     * Duration format.
     */
    private String batchOperationMigrateProcessInstanceHistoryTTL;

    /**
     * The default time to live for modify process instance batch operations. Specified in Java
     * Duration format.
     */
    private String batchOperationModifyProcessInstanceHistoryTTL;

    /**
     * The default time to live for resolve incident batch operations. Specified in Java Duration
     * format.
     */
    private String batchOperationResolveIncidentHistoryTTL;

    /**
     * The min interval between two history cleanup runs. This will be reached when the system is
     * constantly finding data to clean up. Specified in Java Duration format.
     */
    private String minHistoryCleanupInterval;

    /**
     * The max interval between two history cleanup runs. This will be reached when the system is
     * constantly finding no data to clean up. Specified in Java Duration format.
     */
    private String maxHistoryCleanupInterval;

    /** The number of history records to delete in one batch. */
    private String historyCleanupBatchSize;

    /**
     * Interval how often usage metrics cleanup is performed.
     * Specified in Java Duration format.
     */
    private String usageMetricsCleanup;

    /**
     * The default time to live for usage metrics.
     * Specified in Java Duration format.
     */
    private String usageMetricsTTL;

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

  }

  public static class Cache {

    /**
     * The maximum number of entries the cache may contain. When the size of the cache exceeds this,
     * the oldest entries are removed.
     */
    private int maxSize;

    public int getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final int maxSize) {
      this.maxSize = maxSize;
    }
  }
}
