/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration.backpressure;

import static com.google.common.base.Preconditions.checkArgument;
import static io.zeebe.broker.system.configuration.ConfigurationUtil.checkPositive;

import java.time.Duration;

public class AIMDCfg {

  private Duration requestTimeout = Duration.ofSeconds(1);
  private int initialLimit = 100;
  private int minLimit = 1;
  private int maxLimit = 1000;
  private double backoffRatio = 0.9;

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    checkArgument(
        !requestTimeout.isNegative() && !requestTimeout.isZero(),
        "Expected requestTimeout to be > 0, but found %s",
        requestTimeout);
    this.requestTimeout = requestTimeout;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    checkPositive(initialLimit, "initialLimit");
    this.initialLimit = initialLimit;
  }

  public int getMinLimit() {
    return minLimit;
  }

  public void setMinLimit(final int minLimit) {
    checkPositive(minLimit, "minLimit");
    this.minLimit = minLimit;
  }

  public int getMaxLimit() {
    return maxLimit;
  }

  public void setMaxLimit(final int maxLimit) {
    checkPositive(maxLimit, "maxLimit");
    this.maxLimit = maxLimit;
  }

  public double getBackoffRatio() {
    return backoffRatio;
  }

  public void setBackoffRatio(final double backoffRatio) {
    checkArgument(
        backoffRatio < 1.0 && backoffRatio >= 0.5,
        "Expected backoff ratio to be in the range [0.5, 1.0), but found %s",
        backoffRatio);
    this.backoffRatio = backoffRatio;
  }

  @Override
  public String toString() {
    return "AIMDCfg{"
        + "requestTimeout='"
        + requestTimeout
        + ", initialLimit="
        + initialLimit
        + ", minLimit="
        + minLimit
        + ", maxLimit="
        + maxLimit
        + ", backoffRatio="
        + backoffRatio
        + '}';
  }
}
