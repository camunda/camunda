/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public final class EngineCfg implements ConfigurationEntry {

  private MessagesCfg messages = new MessagesCfg();
  private CachesCfg caches = new CachesCfg();
  private JobsCfg jobs = new JobsCfg();
  private ValidatorsCfg validators = new ValidatorsCfg();
  private BatchOperationCfg batchOperations = new BatchOperationCfg();
  private UsageMetricsCfg usageMetrics = new UsageMetricsCfg();
  private DistributionCfg distribution = new DistributionCfg();
  private int maxProcessDepth = EngineConfiguration.DEFAULT_MAX_PROCESS_DEPTH;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    messages.init(globalConfig, brokerBase);
    caches.init(globalConfig, brokerBase);
    jobs.init(globalConfig, brokerBase);
    batchOperations.init(globalConfig, brokerBase);
    validators.init(globalConfig, brokerBase);
    distribution.init(globalConfig, brokerBase);
    usageMetrics.init(globalConfig, brokerBase);
  }

  public MessagesCfg getMessages() {
    return messages;
  }

  public void setMessages(final MessagesCfg messages) {
    this.messages = messages;
  }

  public CachesCfg getCaches() {
    return caches;
  }

  public void setCaches(final CachesCfg caches) {
    this.caches = caches;
  }

  public JobsCfg getJobs() {
    return jobs;
  }

  public void setJobs(final JobsCfg jobs) {
    this.jobs = jobs;
  }

  public ValidatorsCfg getValidators() {
    return validators;
  }

  public void setValidators(final ValidatorsCfg validators) {
    this.validators = validators;
  }

  public BatchOperationCfg getBatchOperations() {
    return batchOperations;
  }

  public void setBatchOperations(final BatchOperationCfg batchOperations) {
    this.batchOperations = batchOperations;
  }

  public UsageMetricsCfg getUsageMetrics() {
    return usageMetrics;
  }

  public void setUsageMetrics(final UsageMetricsCfg usageMetrics) {
    this.usageMetrics = usageMetrics;
  }

  public DistributionCfg getDistribution() {
    return distribution;
  }

  public void setDistribution(final DistributionCfg distribution) {
    this.distribution = distribution;
  }

  public int getMaxProcessDepth() {
    return maxProcessDepth;
  }

  public void setMaxProcessDepth(final int maxProcessDepth) {
    this.maxProcessDepth = maxProcessDepth;
  }

  @Override
  public String toString() {
    return "EngineCfg{"
        + "messages="
        + messages
        + ", caches="
        + caches
        + ", jobs="
        + jobs
        + ", validators="
        + validators
        + ", batchOperations="
        + batchOperations
        + ", usageMetrics="
        + usageMetrics
        + ", distribution="
        + distribution
        + ", maxProcessDepth="
        + maxProcessDepth
        + '}';
  }

  public EngineConfiguration createEngineConfiguration() {
    return new EngineConfiguration()
        .setMessagesTtlCheckerBatchLimit(messages.getTtlCheckerBatchLimit())
        .setMessagesTtlCheckerInterval(messages.getTtlCheckerInterval())
        .setDrgCacheCapacity(caches.getDrgCacheCapacity())
        .setFormCacheCapacity(caches.getFormCacheCapacity())
        .setResourceCacheCapacity(caches.getResourceCacheCapacity())
        .setProcessCacheCapacity(caches.getProcessCacheCapacity())
        .setJobsTimeoutCheckerPollingInterval(jobs.getTimeoutCheckerPollingInterval())
        .setJobsTimeoutCheckerBatchLimit(jobs.getTimeoutCheckerBatchLimit())
        .setValidatorsResultsOutputMaxSize(validators.getResultsOutputMaxSize())
        .setBatchOperationSchedulerInterval(batchOperations.getSchedulerInterval())
        .setBatchOperationChunkSize(batchOperations.getChunkSize())
        .setBatchOperationDbChunkSize(batchOperations.getDbChunkSize())
        .setBatchOperationQueryPageSize(batchOperations.getQueryPageSize())
        .setBatchOperationQueryInClauseSize(batchOperations.getQueryInClauseSize())
        .setBatchOperationQueryRetryMax(batchOperations.getQueryRetryMax())
        .setBatchOperationQueryRetryInitialDelay(batchOperations.getQueryRetryInitialDelay())
        .setBatchOperationQueryRetryMaxDelay(batchOperations.getQueryRetryMaxDelay())
        .setBatchOperationQueryRetryBackoffFactor(batchOperations.getQueryRetryBackoffFactor())
        .setUsageMetricsExportInterval(usageMetrics.getExportInterval())
        .setCommandDistributionPaused(distribution.isPauseCommandDistribution())
        .setCommandRedistributionInterval(distribution.getRetryInterval())
        .setCommandRedistributionMaxBackoff(distribution.getMaxBackoffDuration())
        .setMaxProcessDepth(getMaxProcessDepth());
  }
}
