/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class RdbmsHistory {

  /**
   * The default time to live for all camunda entities that support history time to live. Specified
   * in Java Duration format.
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
  private Integer historyCleanupBatchSize;

  /** Interval how often usage metrics cleanup is performed. Specified in Java Duration format. */
  private String usageMetricsCleanup;

  /** The default time to live for usage metrics. Specified in Java Duration format. */
  private String usageMetricsTTL;

  public Integer getHistoryCleanupBatchSize() {
    return historyCleanupBatchSize;
  }

  public void setHistoryCleanupBatchSize(final Integer historyCleanupBatchSize) {
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
