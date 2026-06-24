/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backpressure;

import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import java.time.Duration;
import java.util.Objects;

public class RateLimitCfg {
  private boolean enabled = false;
  private int limit;
  private Duration rampUp = Duration.ZERO;
  private ThrottleCfg throttling = new ThrottleCfg();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(final int limit) {
    this.limit = limit;
  }

  public RateLimit buildLimit() {
    return new RateLimit(
        enabled,
        limit,
        rampUp,
        new RateLimit.Throttling(
            throttling.isEnabled(),
            throttling.getAcceptableBacklog(),
            throttling.getMinimumLimit(),
            throttling.getResolution()));
  }

  public Duration getRampUp() {
    return rampUp;
  }

  public void setRampUp(final Duration rampUp) {
    this.rampUp = rampUp;
  }

  public ThrottleCfg getThrottling() {
    return throttling;
  }

  public void setThrottling(final ThrottleCfg throttling) {
    this.throttling = throttling;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, limit, rampUp, throttling);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final RateLimitCfg that)) {
      return false;
    }
    return enabled == that.enabled
        && limit == that.limit
        && Objects.equals(rampUp, that.rampUp)
        && Objects.equals(throttling, that.throttling);
  }

  @Override
  public String toString() {
    return "RateLimitCfg{"
        + "enabled="
        + enabled
        + ", limit="
        + limit
        + ", rampUp="
        + rampUp
        + ", throttling="
        + throttling
        + '}';
  }
}
