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
    return new RateLimit(enabled, limit, rampUp);
  }

  public Duration getRampUp() {
    return rampUp;
  }

  public void setRampUp(final Duration rampUp) {
    this.rampUp = rampUp;
  }

  @Override
  public int hashCode() {
    return Objects.hash(limit, enabled, rampUp);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final RateLimitCfg that)) {
      return false;
    }
    return limit == that.limit && enabled == that.enabled && Objects.equals(rampUp, that.rampUp);
  }
}
