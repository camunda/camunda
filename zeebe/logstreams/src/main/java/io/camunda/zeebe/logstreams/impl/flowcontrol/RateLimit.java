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

@SuppressWarnings("UnstableApiUsage")
public record RateLimit(boolean enabled, int limit, Duration rampUp, Throttling throttling) {
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
    static Throttling disabled() {
      return new Throttling(false, 0, 0, Duration.ZERO);
    }
  }
}
