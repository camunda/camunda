/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.retry;

import static java.util.Optional.ofNullable;

import java.time.Duration;

public abstract class RetryConfiguration {
  private Integer maxRetries;
  private Duration minRetryDelay;
  private Duration maxRetryDelay;

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

  public abstract int defaultMaxRetries();

  public abstract Duration defaultMinRetryDelay();

  public abstract Duration defaultMaxRetryDelay();
}
