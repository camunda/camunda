/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

// AIMD backpressure algorithm configs
public class Aimd {

  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMillis(200);
  private static final int DEFAULT_INITIAL_LIMIT = 100;
  private static final int DEFAULT_MIN_LIMIT = 1;
  private static final int DEFAULT_MAX_LIMIT = 1000;
  private static final double DEFAULT_BACKOFF_RATIO = 0.9;

  /** The request timeout */
  private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;

  /** The initial limit */
  private int initialLimit = DEFAULT_INITIAL_LIMIT;

  /** The minimum limit */
  private int minLimit = DEFAULT_MIN_LIMIT;

  /** The maximum limit */
  private int maxLimit = DEFAULT_MAX_LIMIT;

  /** The backoff ratio */
  private double backoffRatio = DEFAULT_BACKOFF_RATIO;

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
  }

  public int getMinLimit() {
    return minLimit;
  }

  public void setMinLimit(final int minLimit) {
    this.minLimit = minLimit;
  }

  public int getMaxLimit() {
    return maxLimit;
  }

  public void setMaxLimit(final int maxLimit) {
    this.maxLimit = maxLimit;
  }

  public double getBackoffRatio() {
    return backoffRatio;
  }

  public void setBackoffRatio(final double backoffRatio) {
    this.backoffRatio = backoffRatio;
  }
}
