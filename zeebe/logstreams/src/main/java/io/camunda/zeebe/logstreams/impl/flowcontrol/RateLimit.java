/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public record RateLimit(boolean enabled, int limit, Duration rampUp, Throttling throttling) {
  public RateLimit {
    Objects.requireNonNull(throttling, "throttling must not be null");
    Objects.requireNonNull(rampUp, "rampUp must not be null");
    if (enabled && limit <= 0) {
      throw new IllegalArgumentException("limit must be greater than 0");
    }
    if (enabled && rampUp.isNegative()) {
      throw new IllegalArgumentException("rampUp cannot be negative");
    }
    if (enabled && throttling.enabled() && throttling.minRate() > limit) {
      throw new IllegalArgumentException(
          "minimum throttling rate must not be larger than the regular limit");
    }
  }

  public static RateLimit disabled() {
    return new RateLimit(false, 0, Duration.ZERO, Throttling.disabled());
  }

  public RateLimiter limiter() {
    if (!enabled) {
      return null;
    }
    if (rampUp.isZero()) {
      return RateLimiter.create(limit);
    }
    return RateLimiter.create(limit, rampUp);
  }

  public record Throttling(
      boolean enabled, long acceptableBacklog, long minRate, Duration resolution) {
    public Throttling {
      Objects.requireNonNull(resolution, "resolution must not be null");

      if (enabled && resolution.isZero()) {
        throw new IllegalArgumentException("resolution must be greater than 0");
      }
      if (enabled && acceptableBacklog < 0) {
        throw new IllegalArgumentException("acceptableBacklog must be greater than 0");
      }
      if (enabled && minRate < 0) {
        throw new IllegalArgumentException("minRate must be greater than 0");
      }
    }

    static Throttling disabled() {
      return new Throttling(false, 0, 0, Duration.ZERO);
    }
  }
}
