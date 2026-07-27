/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

/**
 * Defines configurations for jobs in the engine. The prefix for this class is
 * camunda.processing.engine.job.
 */
public class EngineJob {
  private static final String PREFIX = "camunda.processing.engine.job";

  private static final Set<String> LEGACY_TIMEOUT_CHECKER_BATCH_LIMIT_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobs.timeoutCheckerBatchLimit");
  private static final Set<String> LEGACY_TIMEOUT_CHECKER_POLLING_INTERVAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.jobs.timeoutCheckerPollingInterval");

  private boolean includeVariablesInJobCompletedEvent = false;

  /** Configures the maximum number of timed-out jobs processed per timeout check. */
  private int timeoutCheckerBatchLimit = DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT;

  /** Configures the interval at which jobs are checked for timeouts. */
  private Duration timeoutCheckerPollingInterval = DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;

  /**
   * Configuration option to include variables in the job completed event. This configuration can be
   * accessed via the environment variable: <br>
   * {@code camunda.processing.engine.job.include-variables-in-job-completed-event}.
   *
   * <p>Defaults to {@code false} to prevent job completed events from failing due to excessive
   * batch record size.
   *
   * @return {@code true} if variables should be included in the job completed event, {@code false}
   *     otherwise
   */
  public boolean isIncludeVariablesInJobCompletedEvent() {
    return includeVariablesInJobCompletedEvent;
  }

  public void setIncludeVariablesInJobCompletedEvent(
      final boolean includeVariablesInJobCompletedEvent) {
    this.includeVariablesInJobCompletedEvent = includeVariablesInJobCompletedEvent;
  }

  public int getTimeoutCheckerBatchLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".timeout-checker-batch-limit",
        timeoutCheckerBatchLimit,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_TIMEOUT_CHECKER_BATCH_LIMIT_PROPERTIES);
  }

  public void setTimeoutCheckerBatchLimit(final int timeoutCheckerBatchLimit) {
    this.timeoutCheckerBatchLimit = timeoutCheckerBatchLimit;
  }

  public Duration getTimeoutCheckerPollingInterval() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".timeout-checker-polling-interval",
        timeoutCheckerPollingInterval,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_TIMEOUT_CHECKER_POLLING_INTERVAL_PROPERTIES);
  }

  public void setTimeoutCheckerPollingInterval(final Duration timeoutCheckerPollingInterval) {
    this.timeoutCheckerPollingInterval = timeoutCheckerPollingInterval;
  }
}
