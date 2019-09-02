/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.util.DurationUtil;
import io.zeebe.util.Environment;
import java.time.Duration;
import java.util.Objects;

public class AIMDCfg {

  private String requestTimeout = "1s";
  private int initialLimit = 100;
  private int minLimit = 1;
  private int maxLimit = 1000;
  private double backoffRatio = 0.9;

  public Duration getRequestTimeout() {
    return DurationUtil.parse(requestTimeout);
  }

  public AIMDCfg setRequestTimeout(final String requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public AIMDCfg setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
    return this;
  }

  public int getMinLimit() {
    return minLimit;
  }

  public AIMDCfg setMinLimit(final int minLimit) {
    this.minLimit = minLimit;
    return this;
  }

  public int getMaxLimit() {
    return maxLimit;
  }

  public AIMDCfg setMaxLimit(final int maxLimit) {
    this.maxLimit = maxLimit;
    return this;
  }

  public double getBackoffRatio() {
    return backoffRatio;
  }

  public AIMDCfg setBackoffRatio(final double backoffRatio) {
    this.backoffRatio = backoffRatio;
    return this;
  }

  public void init(final Environment environment) {
    environment
        .get(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_AIMD_BACKOFF_RATIO)
        .map(Double::parseDouble)
        .ifPresent(this::setBackoffRatio);
    environment
        .getInt(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_AIMD_INITIAL_LIMIT)
        .ifPresent(this::setInitialLimit);
    environment
        .getInt(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_AIMD_MIN_LIMIT)
        .ifPresent(this::setMinLimit);
    environment
        .getInt(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_AIMD_MAX_LIMIT)
        .ifPresent(this::setMaxLimit);
    environment
        .get(EnvironmentConstants.ENV_BROKER_BACKPRESSURE_AIMD_REQUEST_TIMEOUT)
        .ifPresent(this::setRequestTimeout);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestTimeout, initialLimit, minLimit, maxLimit, backoffRatio);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AIMDCfg aimdCfg = (AIMDCfg) o;
    return initialLimit == aimdCfg.initialLimit
        && minLimit == aimdCfg.minLimit
        && maxLimit == aimdCfg.maxLimit
        && Double.compare(aimdCfg.backoffRatio, backoffRatio) == 0
        && Objects.equals(requestTimeout, aimdCfg.requestTimeout);
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
