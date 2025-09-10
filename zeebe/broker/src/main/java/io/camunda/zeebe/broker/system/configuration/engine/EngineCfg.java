/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
  private DistributionCfg distribution = new DistributionCfg();
  private int maxProcessDepth = EngineConfiguration.DEFAULT_MAX_PROCESS_DEPTH;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    messages.init(globalConfig, brokerBase);
    caches.init(globalConfig, brokerBase);
    jobs.init(globalConfig, brokerBase);
    validators.init(globalConfig, brokerBase);
    distribution.init(globalConfig, brokerBase);
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
        .setProcessCacheCapacity(caches.getProcessCacheCapacity())
        .setJobsTimeoutCheckerPollingInterval(jobs.getTimeoutCheckerPollingInterval())
        .setJobsTimeoutCheckerBatchLimit(jobs.getTimeoutCheckerBatchLimit())
        .setValidatorsResultsOutputMaxSize(validators.getResultsOutputMaxSize())
        .setCommandDistributionPaused(distribution.isPauseCommandDistribution())
        .setCommandRedistributionInterval(distribution.getRedistributionInterval())
        .setCommandRedistributionMaxBackoff(distribution.getMaxBackoffDuration())
        .setMaxProcessDepth(getMaxProcessDepth());
  }
}
