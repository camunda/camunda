/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static java.lang.Math.clamp;

import com.google.common.util.concurrent.RateLimiter;
import io.camunda.zeebe.logstreams.impl.LogStreamMetrics;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Throttles a rate limiter based on another rate measurement. */
@SuppressWarnings("UnstableApiUsage")
final class RateLimitThrottle {
  private static final Logger LOG = LoggerFactory.getLogger(RateLimitThrottle.class);

  private final AtomicLong lastUpdate = new AtomicLong(-1);

  private final LogStreamMetrics metrics;
  private final RateLimit limit;
  private final RateLimiter limiter;
  private final RateMeasurement measurement;
  private final long resolution;
  private final boolean enabled;
  private final double minRate;

  RateLimitThrottle(
      final LogStreamMetrics metrics,
      final RateLimit limit,
      final RateLimiter limiter,
      final RateMeasurement measurement) {
    this.metrics = metrics;
    this.limit = limit;
    this.limiter = limiter;
    this.measurement = measurement;
    resolution = limit == null ? -1 : limit.throttling().resolution().toMillis();
    enabled = limit != null && limit.enabled() && limit.throttling().enabled();
    minRate = limit == null ? -1 : limit.throttling().minRate();
  }

  public void update(final long timestamp, final long backlog) {
    if (!enabled) {
      return;
    }

    if (canSkipUpdate(timestamp)) {
      return;
    }

    final var factor = limit.throttling().acceptableBacklog() / (double) backlog;
    final var rate = measurement.rate();
    final double adjustedRate =
        factor > 2 ? limit.limit() : clamp(factor * rate, minRate, limit.limit());
    if (adjustedRate < limit.limit()) {
      LOG.debug(
          "Throttling to {}, {} of observed rate {}, Current backlog {}, acceptable {}",
          String.format("%.2f", adjustedRate),
          String.format("%.2f", factor),
          rate,
          backlog,
          limit.throttling().acceptableBacklog());
    }
    limiter.setRate(adjustedRate);
    metrics.setWriteRateMaxLimit(limit.limit());
    metrics.setWriteRateLimit(adjustedRate);
  }

  private boolean canSkipUpdate(final long timestamp) {
    return lastUpdate.updateAndGet(
            lastUpdate -> {
              if (timestamp - lastUpdate < resolution) {
                return lastUpdate;
              } else {
                return timestamp;
              }
            })
        != timestamp;
  }
}
