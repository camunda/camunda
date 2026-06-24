/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.time.Duration;

/** Retry delay strategy that uses {@link ExponentialBackoff} to calculate the next retry delay. */
public class ExponentialBackoffRetryDelay implements RetryDelayStrategy {

  private final ExponentialBackoff exponentialBackoff;
  private long currentDelay = 0; // starts with minDelay

  public ExponentialBackoffRetryDelay(final Duration maxDelay, final Duration minDelay) {
    exponentialBackoff = new ExponentialBackoff(maxDelay.toMillis(), minDelay.toMillis());
  }

  @Override
  public Duration nextDelay() {
    currentDelay = exponentialBackoff.supplyRetryDelay(currentDelay);
    return Duration.ofMillis(currentDelay);
  }

  @Override
  public void reset() {
    currentDelay = 0;
  }
}
