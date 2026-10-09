/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

/**
 * Defines configuration for the engine's secret resolution scheduler. The prefix for this class is
 * camunda.processing.engine.secrets.
 */
public class EngineSecrets {

  public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);
  public static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
  public static final Duration DEFAULT_RETRY_INITIAL_DELAY = Duration.ofSeconds(1);
  public static final Duration DEFAULT_RETRY_MAX_DELAY = Duration.ofSeconds(30);
  public static final int DEFAULT_RETRY_BACKOFF_FACTOR = 2;
  public static final int DEFAULT_BATCH_RESOLUTION_LIMIT = 20;
  public static final Duration DEFAULT_WAKE_DELAY = Duration.ofMillis(50);

  private Duration interval = DEFAULT_INTERVAL;
  private int retryMaxAttempts = DEFAULT_RETRY_MAX_ATTEMPTS;
  private Duration retryInitialDelay = DEFAULT_RETRY_INITIAL_DELAY;
  private Duration retryMaxDelay = DEFAULT_RETRY_MAX_DELAY;
  private int retryBackoffFactor = DEFAULT_RETRY_BACKOFF_FACTOR;
  private int batchResolutionLimit = DEFAULT_BATCH_RESOLUTION_LIMIT;
  private Duration wakeDelay = DEFAULT_WAKE_DELAY;

  /**
   * Cadence at which the secret resolution scheduler polls for pending secret references. This
   * configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.secrets.interval}.
   *
   * <p>Defaults to {@code 5s}.
   */
  public Duration getInterval() {
    return interval;
  }

  public void setInterval(final Duration interval) {
    this.interval = interval;
  }

  /**
   * Maximum number of attempts before a secret store's failure is treated as permanent. This
   * configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.secrets.retry-max-attempts}.
   *
   * <p>Defaults to {@code 3}.
   */
  public int getRetryMaxAttempts() {
    return retryMaxAttempts;
  }

  public void setRetryMaxAttempts(final int retryMaxAttempts) {
    this.retryMaxAttempts = retryMaxAttempts;
  }

  /**
   * Initial delay before the first retry after a secret store failure. This configuration can be
   * accessed via the environment variable: <br>
   * {@code camunda.processing.engine.secrets.retry-initial-delay}.
   *
   * <p>Defaults to {@code 1s}.
   */
  public Duration getRetryInitialDelay() {
    return retryInitialDelay;
  }

  public void setRetryInitialDelay(final Duration retryInitialDelay) {
    this.retryInitialDelay = retryInitialDelay;
  }

  /**
   * Maximum delay between retries after repeated secret store failures. This configuration can be
   * accessed via the environment variable: <br>
   * {@code camunda.processing.engine.secrets.retry-max-delay}.
   *
   * <p>Defaults to {@code 30s}.
   */
  public Duration getRetryMaxDelay() {
    return retryMaxDelay;
  }

  public void setRetryMaxDelay(final Duration retryMaxDelay) {
    this.retryMaxDelay = retryMaxDelay;
  }

  /**
   * Multiplier applied to the retry delay after each consecutive secret store failure. This
   * configuration can be accessed via the environment variable: <br>
   * {@code camunda.processing.engine.secrets.retry-backoff-factor}.
   *
   * <p>Defaults to {@code 2}.
   */
  public int getRetryBackoffFactor() {
    return retryBackoffFactor;
  }

  public void setRetryBackoffFactor(final int retryBackoffFactor) {
    this.retryBackoffFactor = retryBackoffFactor;
  }

  /**
   * Maximum number of pending secret references resolved per scheduling cycle. Remaining refs are
   * picked up in subsequent cycles. This configuration can be accessed via the environment
   * variable: <br>
   * {@code camunda.processing.engine.secrets.batch-resolution-limit}.
   *
   * <p>Defaults to {@code 20}.
   */
  public int getBatchResolutionLimit() {
    return batchResolutionLimit;
  }

  public void setBatchResolutionLimit(final int batchResolutionLimit) {
    this.batchResolutionLimit = batchResolutionLimit;
  }

  /**
   * How long a cycle that resolved something, or that was recently asked to, waits before its next
   * run rather than waiting for the full {@code interval}. This configuration can be accessed via
   * the environment variable: <br>
   * {@code camunda.processing.engine.secrets.wake-delay}.
   *
   * <p>Defaults to {@code 50ms}.
   */
  public Duration getWakeDelay() {
    return wakeDelay;
  }

  public void setWakeDelay(final Duration wakeDelay) {
    this.wakeDelay = wakeDelay;
  }
}
