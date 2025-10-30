/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.retry;

import static io.github.resilience4j.core.IntervalFunction.DEFAULT_INITIAL_INTERVAL;
import static io.github.resilience4j.core.IntervalFunction.DEFAULT_MULTIPLIER;
import static io.github.resilience4j.retry.RetryConfig.DEFAULT_MAX_ATTEMPTS;
import static java.util.Optional.ofNullable;

import java.time.Duration;

public class RetryConfiguration {

  private static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofMillis(30_000);
  private Integer maxRetries;
  private Duration minRetryDelay;
  private Duration maxRetryDelay;
  private Double retryDelayMultiplier;

  public int getMaxRetries() {
    return ofNullable(maxRetries).orElseGet(this::defaultMaxRetries);
  }

  public void setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Duration getMinRetryDelay() {
    return ofNullable(minRetryDelay).orElseGet(this::defaultMinRetryDelay);
  }

  public void setMinRetryDelay(final Duration minRetryDelay) {
    this.minRetryDelay = minRetryDelay;
  }

  public Duration getMaxRetryDelay() {
    return ofNullable(maxRetryDelay).orElseGet(this::defaultMaxRetryDelay);
  }

  public void setMaxRetryDelay(final Duration maxRetryDelay) {
    this.maxRetryDelay = maxRetryDelay;
  }

  public double getRetryDelayMultiplier() {
    return ofNullable(retryDelayMultiplier).orElseGet(this::defaultRetryDelayMultiplier);
  }

  public void setRetryDelayMultiplier(final double retryDelayMultiplier) {
    this.retryDelayMultiplier = retryDelayMultiplier;
  }

  protected int defaultMaxRetries() {
    return DEFAULT_MAX_ATTEMPTS;
  }

  protected Duration defaultMinRetryDelay() {
    return Duration.ofMillis(DEFAULT_INITIAL_INTERVAL);
  }

  protected Duration defaultMaxRetryDelay() {
    return DEFAULT_MAX_RETRY_DELAY;
  }

  protected double defaultRetryDelayMultiplier() {
    return DEFAULT_MULTIPLIER;
  }
}
