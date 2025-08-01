/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import java.time.Duration;

public final class EngineConfiguration {

  public static final int DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT = Integer.MAX_VALUE;
  public static final Duration DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL = Duration.ofMinutes(1);

  public static final int DEFAULT_MAX_ERROR_MESSAGE_SIZE = 10000;

  // This size (in bytes) is used as a buffer when filling an event/command up to the maximum
  // message size.
  public static final int BATCH_SIZE_CALCULATION_BUFFER = 1024 * 8;

  public static final int DEFAULT_DRG_CACHE_CAPACITY = 1000;
  public static final int DEFAULT_FORM_CACHE_CAPACITY = 1000;
  public static final int DEFAULT_PROCESS_CACHE_CAPACITY = 1000;
  public static final Duration DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL = Duration.ofSeconds(1);
  public static final int DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT = Integer.MAX_VALUE;
  public static final int DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE = 12 * 1024;
  public static final boolean DEFAULT_ENABLE_AUTHORIZATION_CHECKS = false;

  public static final int DEFAULT_MAX_PROCESS_DEPTH = 1000;
  public static final Duration DEFAULT_USAGE_METRICS_EXPORT_INTERVAL = Duration.ofMinutes(5);

  public static final Duration DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL = Duration.ofSeconds(1);
  // reasonable size of a chunk record to avoid too many or too large records
  public static final int DEFAULT_BATCH_OPERATION_CHUNK_SIZE = 100;
  // key has 8 bytes, stay below 32KB block size
  public static final int DEFAULT_BATCH_OPERATION_DB_CHUNK_SIZE = 3500;
  // ES/OS have max 10000 entities per query
  public static final int DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE = 10000;
  // Oracle can only have 1000 elements in `IN` clause
  public static final int DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE = 1000;
  public static final boolean DEFAULT_COMMAND_DISTRIBUTION_PAUSED = false;

  private int messagesTtlCheckerBatchLimit = DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;
  private Duration messagesTtlCheckerInterval = DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;
  private int drgCacheCapacity = DEFAULT_DRG_CACHE_CAPACITY;
  private int formCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;
  private int resourceCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;
  private int processCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;

  private Duration jobsTimeoutCheckerPollingInterval = DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
  private int jobsTimeoutCheckerBatchLimit = DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT;

  private int validatorsResultsOutputMaxSize = DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;

  private boolean enableAuthorization = DEFAULT_ENABLE_AUTHORIZATION_CHECKS;

  private int maxProcessDepth = DEFAULT_MAX_PROCESS_DEPTH;

  private Duration batchOperationSchedulerInterval = DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
  private int batchOperationChunkSize = DEFAULT_BATCH_OPERATION_CHUNK_SIZE;
  private int batchOperationDbChunkSize = DEFAULT_BATCH_OPERATION_DB_CHUNK_SIZE;
  private int batchOperationQueryPageSize = DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE;
  private int batchOperationQueryInClauseSize = DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE;

  private Duration usageMetricsExportInterval = DEFAULT_USAGE_METRICS_EXPORT_INTERVAL;

  private boolean commandDistributionPaused = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;

  public int getMessagesTtlCheckerBatchLimit() {
    return messagesTtlCheckerBatchLimit;
  }

  public EngineConfiguration setMessagesTtlCheckerBatchLimit(
      final int messagesTtlCheckerBatchLimit) {
    this.messagesTtlCheckerBatchLimit = messagesTtlCheckerBatchLimit;
    return this;
  }

  public Duration getMessagesTtlCheckerInterval() {
    return messagesTtlCheckerInterval;
  }

  public EngineConfiguration setMessagesTtlCheckerInterval(
      final Duration messagesTtlCheckerInterval) {
    this.messagesTtlCheckerInterval = messagesTtlCheckerInterval;
    return this;
  }

  public int getDrgCacheCapacity() {
    return drgCacheCapacity;
  }

  public EngineConfiguration setDrgCacheCapacity(final int drgCacheCapacity) {
    this.drgCacheCapacity = drgCacheCapacity;
    return this;
  }

  public int getFormCacheCapacity() {
    return formCacheCapacity;
  }

  public EngineConfiguration setFormCacheCapacity(final int formCacheCapacity) {
    this.formCacheCapacity = formCacheCapacity;
    return this;
  }

  public int getResourceCacheCapacity() {
    return resourceCacheCapacity;
  }

  public EngineConfiguration setResourceCacheCapacity(final int resourceCacheCapacity) {
    this.resourceCacheCapacity = resourceCacheCapacity;
    return this;
  }

  public int getProcessCacheCapacity() {
    return processCacheCapacity;
  }

  public EngineConfiguration setProcessCacheCapacity(final int processCacheCapacity) {
    this.processCacheCapacity = processCacheCapacity;
    return this;
  }

  public Duration getJobsTimeoutCheckerPollingInterval() {
    return jobsTimeoutCheckerPollingInterval;
  }

  public EngineConfiguration setJobsTimeoutCheckerPollingInterval(
      final Duration jobsTimeoutCheckerPollingInterval) {
    this.jobsTimeoutCheckerPollingInterval = jobsTimeoutCheckerPollingInterval;
    return this;
  }

  public int getJobsTimeoutCheckerBatchLimit() {
    return jobsTimeoutCheckerBatchLimit;
  }

  public EngineConfiguration setJobsTimeoutCheckerBatchLimit(
      final int jobsTimeoutCheckerBatchLimit) {
    this.jobsTimeoutCheckerBatchLimit = jobsTimeoutCheckerBatchLimit;
    return this;
  }

  public int getValidatorsResultsOutputMaxSize() {
    return validatorsResultsOutputMaxSize;
  }

  public EngineConfiguration setValidatorsResultsOutputMaxSize(final int maxSize) {
    validatorsResultsOutputMaxSize = maxSize;
    return this;
  }

  public boolean isEnableAuthorization() {
    return enableAuthorization;
  }

  public EngineConfiguration setEnableAuthorization(final boolean enableAuthorization) {
    this.enableAuthorization = enableAuthorization;
    return this;
  }

  public int getMaxProcessDepth() {
    return maxProcessDepth;
  }

  public EngineConfiguration setMaxProcessDepth(final int maxProcessDepth) {
    this.maxProcessDepth = maxProcessDepth;
    return this;
  }

  public Duration getBatchOperationSchedulerInterval() {
    return batchOperationSchedulerInterval;
  }

  public EngineConfiguration setBatchOperationSchedulerInterval(
      final Duration batchOperationSchedulerInterval) {
    this.batchOperationSchedulerInterval = batchOperationSchedulerInterval;
    return this;
  }

  public int getBatchOperationChunkSize() {
    return batchOperationChunkSize;
  }

  public EngineConfiguration setBatchOperationChunkSize(final int batchOperationChunkSize) {
    this.batchOperationChunkSize = batchOperationChunkSize;
    return this;
  }

  public int getBatchOperationDbChunkSize() {
    return batchOperationDbChunkSize;
  }

  public EngineConfiguration setBatchOperationDbChunkSize(final int batchOperationDbChunkSize) {
    this.batchOperationDbChunkSize = batchOperationDbChunkSize;
    return this;
  }

  public int getBatchOperationQueryPageSize() {
    return batchOperationQueryPageSize;
  }

  public EngineConfiguration setBatchOperationQueryPageSize(final int batchOperationQueryPageSize) {
    this.batchOperationQueryPageSize = batchOperationQueryPageSize;
    return this;
  }

  public int getBatchOperationQueryInClauseSize() {
    return batchOperationQueryInClauseSize;
  }

  public EngineConfiguration setBatchOperationQueryInClauseSize(
      final int batchOperationQueryInClauseSize) {
    this.batchOperationQueryInClauseSize = batchOperationQueryInClauseSize;
    return this;
  }

  public Duration getUsageMetricsExportInterval() {
    return usageMetricsExportInterval;
  }

  public EngineConfiguration setUsageMetricsExportInterval(
      final Duration usageMetricsExportInterval) {
    this.usageMetricsExportInterval = usageMetricsExportInterval;
    return this;
  }

  public boolean isCommandDistributionPaused() {
    return commandDistributionPaused;
  }

  public EngineConfiguration setCommandDistributionPaused(final boolean commandDistributionPaused) {
    this.commandDistributionPaused = commandDistributionPaused;
    return this;
  }
}
