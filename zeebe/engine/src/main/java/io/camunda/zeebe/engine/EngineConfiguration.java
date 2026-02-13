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
  public static final int DEFAULT_AUTHORIZATIONS_CACHE_CAPACITY = 1000;
  public static final Duration DEFAULT_AUTHORIZATIONS_CACHE_TTL = Duration.ofSeconds(10);
  public static final Duration DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL = Duration.ofSeconds(1);
  public static final int DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT = Integer.MAX_VALUE;
  public static final int DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE = 12 * 1024;
  public static final boolean DEFAULT_ENABLE_AUTHORIZATION_CHECKS = false;

  public static final int DEFAULT_MAX_PROCESS_DEPTH = 1000;
  public static final Duration DEFAULT_USAGE_METRICS_EXPORT_INTERVAL = Duration.ofMinutes(5);
  public static final Duration DEFAULT_JOB_METRICS_EXPORT_INTERVAL = Duration.ofMinutes(5);
  public static final int DEFAULT_MAX_WORKER_NAME_LENGTH = 100;
  public static final int DEFAULT_MAX_JOB_TYPE_LENGTH = 100;
  public static final int DEFAULT_MAX_TENANT_ID_LENGTH = 30;
  public static final int DEFAULT_MAX_UNIQUE_JOB_METRICS_KEYS = 9500;
  public static final boolean DEFAULT_JOB_METRICS_EXPORT_ENABLED = true;

  public static final Duration DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL = Duration.ofSeconds(1);
  // reasonable size of a chunk record to avoid too many or too large records
  public static final int DEFAULT_BATCH_OPERATION_CHUNK_SIZE = 100;
  // ES/OS have max 10000 entities per query
  public static final int DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE = 10000;
  // Oracle can only have 1000 elements in `IN` clause
  public static final int DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE = 1000;
  public static final int DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX = 0;
  public static final Duration DEFAULT_BATCH_OPERATION_QUERY_RETRY_INITIAL_DELAY =
      Duration.ofSeconds(1);
  public static final Duration DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX_DELAY =
      Duration.ofSeconds(60);
  public static final int DEFAULT_BATCH_OPERATION_QUERY_RETRY_BACKOFF_FACTOR = 2;
  public static final boolean DEFAULT_COMMAND_DISTRIBUTION_PAUSED = false;
  public static final Duration DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL = Duration.ofSeconds(10);
  public static final Duration DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION =
      Duration.ofMinutes(5);
  public static final boolean DEFAULT_ENABLE_IDENTITY_SETUP = true;
  public static final Duration DEFAULT_EXPRESSION_EVALUATION_TIMEOUT = Duration.ofSeconds(5);
  public static final boolean DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED = false;

  private int maxJobTypeLength = DEFAULT_MAX_JOB_TYPE_LENGTH;
  private int maxTenantIdLength = DEFAULT_MAX_TENANT_ID_LENGTH;
  private int maxUniqueJobMetricsKeys = DEFAULT_MAX_UNIQUE_JOB_METRICS_KEYS;
  private int maxWorkerNameLength = DEFAULT_MAX_WORKER_NAME_LENGTH;
  private int messagesTtlCheckerBatchLimit = DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;
  private Duration messagesTtlCheckerInterval = DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;
  private int drgCacheCapacity = DEFAULT_DRG_CACHE_CAPACITY;
  private int formCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;
  private int resourceCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;
  private int processCacheCapacity = DEFAULT_FORM_CACHE_CAPACITY;
  private int authorizationsCacheCapacity = DEFAULT_AUTHORIZATIONS_CACHE_CAPACITY;
  private Duration authorizationsCacheTtl = DEFAULT_AUTHORIZATIONS_CACHE_TTL;
  private Duration jobsTimeoutCheckerPollingInterval = DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
  private int jobsTimeoutCheckerBatchLimit = DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT;
  private int validatorsResultsOutputMaxSize = DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;
  private boolean enableAuthorization = DEFAULT_ENABLE_AUTHORIZATION_CHECKS;
  private int maxProcessDepth = DEFAULT_MAX_PROCESS_DEPTH;
  private Duration batchOperationSchedulerInterval = DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
  private int batchOperationChunkSize = DEFAULT_BATCH_OPERATION_CHUNK_SIZE;
  private int batchOperationQueryPageSize = DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE;
  private int batchOperationQueryInClauseSize = DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE;
  private int batchOperationQueryRetryMax = DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX;
  private Duration batchOperationQueryRetryInitialDelay =
      DEFAULT_BATCH_OPERATION_QUERY_RETRY_INITIAL_DELAY;
  private Duration batchOperationQueryRetryMaxDelay = DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX_DELAY;
  private int batchOperationQueryRetryBackoffFactor =
      DEFAULT_BATCH_OPERATION_QUERY_RETRY_BACKOFF_FACTOR;
  private Duration usageMetricsExportInterval = DEFAULT_USAGE_METRICS_EXPORT_INTERVAL;
  private Duration jobMetricsExportInterval = DEFAULT_JOB_METRICS_EXPORT_INTERVAL;
  private boolean jobMetricsExportEnabled = DEFAULT_JOB_METRICS_EXPORT_ENABLED;
  private boolean commandDistributionPaused = DEFAULT_COMMAND_DISTRIBUTION_PAUSED;
  private Duration commandRedistributionInterval = DEFAULT_COMMAND_REDISTRIBUTION_INTERVAL;
  private Duration commandRedistributionMaxBackoff =
      DEFAULT_COMMAND_REDISTRIBUTION_MAX_BACKOFF_DURATION;
  private boolean enableIdentitySetup = DEFAULT_ENABLE_IDENTITY_SETUP;
  private GlobalListenersConfiguration globalListeners = GlobalListenersConfiguration.empty();
  private Duration expressionEvaluationTimeout = DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  /**
   * Controls uniqueness enforcement of business IDs across active process instances.
   *
   * <ul>
   *   <li><b>Disabled (default):</b> Multiple active process instances can share the same business
   *       ID. No tracking or validation is performed.
   *   <li><b>Enabled:</b> Creating a process instance with a business ID that is already in use by
   *       an active process instance will be rejected. Business IDs of process instances created
   *       before enabling this setting are not tracked, so duplicates with those are not detected.
   * </ul>
   */
  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;

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

  public int getAuthorizationsCacheCapacity() {
    return authorizationsCacheCapacity;
  }

  public EngineConfiguration setAuthorizationsCacheCapacity(final int authorizationsCacheCapacity) {
    this.authorizationsCacheCapacity = authorizationsCacheCapacity;
    return this;
  }

  public Duration getAuthorizationsCacheTtl() {
    return authorizationsCacheTtl;
  }

  public EngineConfiguration setAuthorizationsCacheTtl(final Duration authorizationsCacheTtl) {
    this.authorizationsCacheTtl = authorizationsCacheTtl;
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

  public int getBatchOperationQueryRetryMax() {
    return batchOperationQueryRetryMax;
  }

  public EngineConfiguration setBatchOperationQueryRetryMax(final int batchOperationQueryRetryMax) {
    this.batchOperationQueryRetryMax = batchOperationQueryRetryMax;
    return this;
  }

  public Duration getBatchOperationQueryRetryInitialDelay() {
    return batchOperationQueryRetryInitialDelay;
  }

  public EngineConfiguration setBatchOperationQueryRetryInitialDelay(
      final Duration batchOperationQueryRetryInitialDelay) {
    this.batchOperationQueryRetryInitialDelay = batchOperationQueryRetryInitialDelay;
    return this;
  }

  public Duration getBatchOperationQueryRetryMaxDelay() {
    return batchOperationQueryRetryMaxDelay;
  }

  public EngineConfiguration setBatchOperationQueryRetryMaxDelay(
      final Duration batchOperationQueryRetryMaxDelay) {
    this.batchOperationQueryRetryMaxDelay = batchOperationQueryRetryMaxDelay;
    return this;
  }

  public int getBatchOperationQueryRetryBackoffFactor() {
    return batchOperationQueryRetryBackoffFactor;
  }

  public EngineConfiguration setBatchOperationQueryRetryBackoffFactor(
      final int batchOperationQueryRetryBackoffFactor) {
    this.batchOperationQueryRetryBackoffFactor = batchOperationQueryRetryBackoffFactor;
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

  public Duration getJobMetricsExportInterval() {
    return jobMetricsExportInterval;
  }

  public EngineConfiguration setJobMetricsExportInterval(final Duration jobMetricsExportInterval) {
    this.jobMetricsExportInterval = jobMetricsExportInterval;
    return this;
  }

  public boolean isJobMetricsExportEnabled() {
    return jobMetricsExportEnabled;
  }

  public EngineConfiguration setJobMetricsExportEnabled(final boolean jobMetricsExportEnabled) {
    this.jobMetricsExportEnabled = jobMetricsExportEnabled;
    return this;
  }

  public boolean isCommandDistributionPaused() {
    return commandDistributionPaused;
  }

  public EngineConfiguration setCommandDistributionPaused(final boolean commandDistributionPaused) {
    this.commandDistributionPaused = commandDistributionPaused;
    return this;
  }

  public Duration getCommandRedistributionInterval() {
    return commandRedistributionInterval;
  }

  public EngineConfiguration setCommandRedistributionInterval(
      final Duration commandRedistributionInterval) {
    this.commandRedistributionInterval = commandRedistributionInterval;
    return this;
  }

  public Duration getCommandRedistributionMaxBackoff() {
    return commandRedistributionMaxBackoff;
  }

  public EngineConfiguration setCommandRedistributionMaxBackoff(
      final Duration commandRedistributionMaxBackoff) {
    this.commandRedistributionMaxBackoff = commandRedistributionMaxBackoff;
    return this;
  }

  public boolean isEnableIdentitySetup() {
    return enableIdentitySetup;
  }

  public EngineConfiguration setEnableIdentitySetup(final boolean enableIdentitySetup) {
    this.enableIdentitySetup = enableIdentitySetup;
    return this;
  }

  public GlobalListenersConfiguration getGlobalListeners() {
    return globalListeners;
  }

  public EngineConfiguration setGlobalListeners(
      final GlobalListenersConfiguration globalListeners) {
    this.globalListeners = globalListeners;
    return this;
  }

  public int getMaxUniqueJobMetricsKeys() {
    return maxUniqueJobMetricsKeys;
  }

  public EngineConfiguration setMaxUniqueJobMetricsKeys(final int maxUniqueJobMetricsKeys) {
    this.maxUniqueJobMetricsKeys = maxUniqueJobMetricsKeys;
    return this;
  }

  public int getMaxWorkerNameLength() {
    return maxWorkerNameLength;
  }

  public EngineConfiguration setMaxWorkerNameLength(final int maxWorkerNameLength) {
    this.maxWorkerNameLength = maxWorkerNameLength;
    return this;
  }

  public int getMaxJobTypeLength() {
    return maxJobTypeLength;
  }

  public EngineConfiguration setMaxJobTypeLength(final int maxJobTypeLength) {
    this.maxJobTypeLength = maxJobTypeLength;
    return this;
  }

  public int getMaxTenantIdLength() {
    return maxTenantIdLength;
  }

  public EngineConfiguration setMaxTenantIdLength(final int maxTenantIdLength) {
    this.maxTenantIdLength = maxTenantIdLength;
    return this;
  }

  public Duration getExpressionEvaluationTimeout() {
    return expressionEvaluationTimeout;
  }

  public EngineConfiguration setExpressionEvaluationTimeout(
      final Duration expressionEvaluationTimeout) {
    this.expressionEvaluationTimeout = expressionEvaluationTimeout;
    return this;
  }

  public boolean isBusinessIdUniquenessEnabled() {
    return businessIdUniquenessEnabled;
  }

  public EngineConfiguration setBusinessIdUniquenessEnabled(
      final boolean businessIdUniquenessEnabled) {
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
    return this;
  }
}
