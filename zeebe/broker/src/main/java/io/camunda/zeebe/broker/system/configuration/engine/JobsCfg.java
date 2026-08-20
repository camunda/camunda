/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;

public class JobsCfg implements ConfigurationEntry {
  private Duration timeoutCheckerPollingInterval =
      EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
  private int timeoutCheckerBatchLimit =
      EngineConfiguration.DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT;
  private boolean includeVariablesInJobCompletedEvent =
      EngineConfiguration.DEFAULT_JOBS_INCLUDE_VARIABLES_IN_JOB_COMPLETED_EVENT;

  public Duration getTimeoutCheckerPollingInterval() {
    return timeoutCheckerPollingInterval;
  }

  public void setTimeoutCheckerPollingInterval(final Duration timeoutCheckerPollingInterval) {
    this.timeoutCheckerPollingInterval = timeoutCheckerPollingInterval;
  }

  public int getTimeoutCheckerBatchLimit() {
    return timeoutCheckerBatchLimit;
  }

  public void setTimeoutCheckerBatchLimit(final int timeoutCheckerBatchLimit) {
    this.timeoutCheckerBatchLimit = timeoutCheckerBatchLimit;
  }

  public boolean isIncludeVariablesInJobCompletedEvent() {
    return includeVariablesInJobCompletedEvent;
  }

  public void setIncludeVariablesInJobCompletedEvent(
      final boolean includeVariablesInJobCompletedEvent) {
    this.includeVariablesInJobCompletedEvent = includeVariablesInJobCompletedEvent;
  }

  @Override
  public String toString() {
    return "JobsCfg{"
        + "timeoutCheckerPollingInterval="
        + timeoutCheckerPollingInterval
        + ", timeoutCheckerBatchLimit="
        + timeoutCheckerBatchLimit
        + ", includeVariablesInJobCompletedEvent="
        + includeVariablesInJobCompletedEvent
        + '}';
  }
}
