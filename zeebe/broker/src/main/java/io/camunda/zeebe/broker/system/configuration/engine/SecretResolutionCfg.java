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
import java.time.Duration;

public class SecretResolutionCfg implements ConfigurationEntry {
  private Duration interval = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_INTERVAL;
  private int retryMaxAttempts = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_MAX_ATTEMPTS;
  private Duration retryInitialDelay =
      EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_INITIAL_DELAY;
  private Duration retryMaxDelay = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_MAX_DELAY;
  private int retryBackoffFactor =
      EngineConfiguration.DEFAULT_SECRET_RESOLUTION_RETRY_BACKOFF_FACTOR;
  private int batchResolutionLimit = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_BATCH_LIMIT;
  private Duration wakeDelay = EngineConfiguration.DEFAULT_SECRET_RESOLUTION_WAKE_DELAY;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (interval == null || !interval.isPositive()) {
      throw new IllegalArgumentException(
          "Secret resolution interval must be positive but was %s".formatted(interval));
    }
    if (retryMaxAttempts < 1) {
      throw new IllegalArgumentException(
          "Secret resolution retryMaxAttempts must be at least 1 but was %d"
              .formatted(retryMaxAttempts));
    }
    if (retryInitialDelay == null || !retryInitialDelay.isPositive()) {
      throw new IllegalArgumentException(
          "Secret resolution retryInitialDelay must be positive but was %s"
              .formatted(retryInitialDelay));
    }
    if (retryMaxDelay == null
        || !retryMaxDelay.isPositive()
        || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
      throw new IllegalArgumentException(
          "Secret resolution retryMaxDelay must be positive and not smaller than retryInitialDelay but was %s"
              .formatted(retryMaxDelay));
    }
    if (retryBackoffFactor < 1) {
      throw new IllegalArgumentException(
          "Secret resolution retryBackoffFactor must be at least 1 but was %d"
              .formatted(retryBackoffFactor));
    }
    if (batchResolutionLimit < 1) {
      throw new IllegalArgumentException(
          "Secret resolution batchResolutionLimit must be at least 1 but was %d"
              .formatted(batchResolutionLimit));
    }
    // zero is meaningful here: every cycle that resolves something reschedules at wakeDelay
    // regardless, so zero just means the very next one runs as soon as the actor is free
    if (wakeDelay == null || wakeDelay.isNegative()) {
      throw new IllegalArgumentException(
          "Secret resolution wakeDelay must not be negative but was %s".formatted(wakeDelay));
    }
  }

  public Duration getInterval() {
    return interval;
  }

  public void setInterval(final Duration interval) {
    this.interval = interval;
  }

  public int getRetryMaxAttempts() {
    return retryMaxAttempts;
  }

  public void setRetryMaxAttempts(final int retryMaxAttempts) {
    this.retryMaxAttempts = retryMaxAttempts;
  }

  public Duration getRetryInitialDelay() {
    return retryInitialDelay;
  }

  public void setRetryInitialDelay(final Duration retryInitialDelay) {
    this.retryInitialDelay = retryInitialDelay;
  }

  public Duration getRetryMaxDelay() {
    return retryMaxDelay;
  }

  public void setRetryMaxDelay(final Duration retryMaxDelay) {
    this.retryMaxDelay = retryMaxDelay;
  }

  public int getRetryBackoffFactor() {
    return retryBackoffFactor;
  }

  public void setRetryBackoffFactor(final int retryBackoffFactor) {
    this.retryBackoffFactor = retryBackoffFactor;
  }

  public int getBatchResolutionLimit() {
    return batchResolutionLimit;
  }

  public void setBatchResolutionLimit(final int batchResolutionLimit) {
    this.batchResolutionLimit = batchResolutionLimit;
  }

  public Duration getWakeDelay() {
    return wakeDelay;
  }

  public void setWakeDelay(final Duration wakeDelay) {
    this.wakeDelay = wakeDelay;
  }

  @Override
  public String toString() {
    return "SecretResolutionCfg{"
        + "interval="
        + interval
        + ", retryMaxAttempts="
        + retryMaxAttempts
        + ", retryInitialDelay="
        + retryInitialDelay
        + ", retryMaxDelay="
        + retryMaxDelay
        + ", retryBackoffFactor="
        + retryBackoffFactor
        + ", batchResolutionLimit="
        + batchResolutionLimit
        + ", wakeDelay="
        + wakeDelay
        + '}';
  }
}
