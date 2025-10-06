/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

public class RdbmsHistory {

  /**
   * The default time to live for all camunda entities that support history time to live. Specified
   * in Java Duration format.
   */
  private Duration defaultHistoryTTL;

  /** The default time to live for all batch operations. Specified in Java Duration format. */
  private Duration defaultBatchOperationHistoryTTL;

  /**
   * The default time to live for cancel process instance batch operations. Specified in Java
   * Duration format.
   */
  private Duration batchOperationCancelProcessInstanceHistoryTTL;

  /**
   * The default time to live for migrate process instance batch operations. Specified in Java
   * Duration format.
   */
  private Duration batchOperationMigrateProcessInstanceHistoryTTL;

  /**
   * The default time to live for modify process instance batch operations. Specified in Java
   * Duration format.
   */
  private Duration batchOperationModifyProcessInstanceHistoryTTL;

  /**
   * The default time to live for resolve incident batch operations. Specified in Java Duration
   * format.
   */
  private Duration batchOperationResolveIncidentHistoryTTL;

  /**
   * The min interval between two history cleanup runs. This will be reached when the system is
   * constantly finding data to clean up. Specified in Java Duration format.
   */
  private Duration minHistoryCleanupInterval;

  /**
   * The max interval between two history cleanup runs. This will be reached when the system is
   * constantly finding no data to clean up. Specified in Java Duration format.
   */
  private Duration maxHistoryCleanupInterval;

  /** The number of history records to delete in one batch. */
  private Integer historyCleanupBatchSize;

  /** Interval how often usage metrics cleanup is performed. Specified in Java Duration format. */
  private Duration usageMetricsCleanup;

  /** The default time to live for usage metrics. Specified in Java Duration format. */
  private Duration usageMetricsTTL;

  public Integer getHistoryCleanupBatchSize() {
    return historyCleanupBatchSize;
  }

  public void setHistoryCleanupBatchSize(final Integer historyCleanupBatchSize) {
    this.historyCleanupBatchSize = historyCleanupBatchSize;
  }

  public Duration getMaxHistoryCleanupInterval() {
    return maxHistoryCleanupInterval;
  }

  public void setMaxHistoryCleanupInterval(final Duration maxHistoryCleanupInterval) {
    this.maxHistoryCleanupInterval = maxHistoryCleanupInterval;
  }

  public Duration getMinHistoryCleanupInterval() {
    return minHistoryCleanupInterval;
  }

  public void setMinHistoryCleanupInterval(final Duration minHistoryCleanupInterval) {
    this.minHistoryCleanupInterval = minHistoryCleanupInterval;
  }

  public Duration getBatchOperationResolveIncidentHistoryTTL() {
    return batchOperationResolveIncidentHistoryTTL;
  }

  public void setBatchOperationResolveIncidentHistoryTTL(
      final Duration batchOperationResolveIncidentHistoryTTL) {
    this.batchOperationResolveIncidentHistoryTTL = batchOperationResolveIncidentHistoryTTL;
  }

  public Duration getBatchOperationModifyProcessInstanceHistoryTTL() {
    return batchOperationModifyProcessInstanceHistoryTTL;
  }

  public void setBatchOperationModifyProcessInstanceHistoryTTL(
      final Duration batchOperationModifyProcessInstanceHistoryTTL) {
    this.batchOperationModifyProcessInstanceHistoryTTL =
        batchOperationModifyProcessInstanceHistoryTTL;
  }

  public Duration getBatchOperationMigrateProcessInstanceHistoryTTL() {
    return batchOperationMigrateProcessInstanceHistoryTTL;
  }

  public void setBatchOperationMigrateProcessInstanceHistoryTTL(
      final Duration batchOperationMigrateProcessInstanceHistoryTTL) {
    this.batchOperationMigrateProcessInstanceHistoryTTL =
        batchOperationMigrateProcessInstanceHistoryTTL;
  }

  public Duration getBatchOperationCancelProcessInstanceHistoryTTL() {
    return batchOperationCancelProcessInstanceHistoryTTL;
  }

  public void setBatchOperationCancelProcessInstanceHistoryTTL(
      final Duration batchOperationCancelProcessInstanceHistoryTTL) {
    this.batchOperationCancelProcessInstanceHistoryTTL =
        batchOperationCancelProcessInstanceHistoryTTL;
  }

  public Duration getDefaultBatchOperationHistoryTTL() {
    return defaultBatchOperationHistoryTTL;
  }

  public void setDefaultBatchOperationHistoryTTL(final Duration defaultBatchOperationHistoryTTL) {
    this.defaultBatchOperationHistoryTTL = defaultBatchOperationHistoryTTL;
  }

  public Duration getDefaultHistoryTTL() {
    return defaultHistoryTTL;
  }

  public void setDefaultHistoryTTL(final Duration defaultHistoryTTL) {
    this.defaultHistoryTTL = defaultHistoryTTL;
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
}
