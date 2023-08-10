/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import static java.util.Objects.requireNonNull;

import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.scheduling.annotation.Scheduled;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Wrapper for a health indicator that adds time tolerance to the underlying health indicator. When
 * the original health indicator reports a {@code Status.DOWN}, then this health indicator will
 * still report the health status as {@code Status.UP} for a certain time. If the health comes back
 * up during that time, then the downtime will be hidden by the decorator. If the health stays down
 * for a long time, then this health indicator will also switch to {@code Status.DOWN}
 *
 * <p>Ultimately, the purpose of this class is to implement a health indicator like "no connection
 * to backend for > 5 min of time". In this setup, one needs a "no connection to backend" health
 * indicator that toggles immediately when the connection gets lost. This can then be wrapped in
 * this class to add the delay.
 */
public class DelayedHealthIndicator implements HealthIndicator {

  private final HealthIndicator originalHealthIndicator;
  private final Duration maxDowntime;
  private final Supplier<Long> clock;

  private HealthResult lastHealthResult;
  private Long lastTimeUp;

  private final Map<String, Object> staticDetails = new HashMap<>();

  /** Constructor for tests, mainly used to set a different clock */
  protected DelayedHealthIndicator(
      final HealthIndicator originalHealthIndicator,
      final Duration maxDowntime,
      final Supplier<Long> clock) {
    if (requireNonNull(maxDowntime).toMillis() < 0) {
      throw new IllegalArgumentException("maxDowntime must be >= 0");
    }
    this.originalHealthIndicator = requireNonNull(originalHealthIndicator);
    this.maxDowntime = maxDowntime;
    this.clock = requireNonNull(clock);

    staticDetails.put("derivedFrom", originalHealthIndicator.getClass().getSimpleName());
    staticDetails.put("maxDowntime", maxDowntime);
  }

  public DelayedHealthIndicator(
      final HealthIndicator originalHealthIndicator, final Duration maxDowntime) {
    this(originalHealthIndicator, maxDowntime, System::currentTimeMillis);
  }

  @Scheduled(fixedDelay = "5000")
  public void checkHealth() {
    final var originalHealth = originalHealthIndicator.getResult();
    lastHealthResult = Mono.from(originalHealth).block(Duration.ofMillis(5000));

    if (lastHealthResult.getStatus().equals(HealthStatus.UP)) {
      lastTimeUp = clock.get();
    }
  }

  @Override
  public Publisher<HealthResult> getResult() {
    final HealthStatus healthStatus;
    final long now = clock.get();

    if (lastHealthResult == null) { // was never checked
      healthStatus = HealthStatus.DOWN;
    } else {
      if (lastTimeUp == null) { // was never up
        healthStatus = lastHealthResult.getStatus();
      } else if (lastTimeUp + maxDowntime.toMillis() > now) {
        healthStatus = HealthStatus.UP;
      } else {
        healthStatus = lastHealthResult.getStatus();
      }
    }

    return Mono.just(
        HealthResult.builder(getClass().getSimpleName(), healthStatus)
            .details(createDetails(now))
            .build());
  }

  private Map<String, Object> createDetails(final long referenceTime) {
    final var result = new HashMap<>(staticDetails);

    if (lastHealthResult != null) {
      result.put("lastSeenDelegateHealthStatus", lastHealthResult);
    }

    result.put("wasEverUp", lastTimeUp != null);

    if (lastTimeUp != null
        && lastHealthResult != null
        && lastHealthResult.getStatus() != HealthStatus.UP) {
      result.put("downTime", Duration.ofMillis(referenceTime - lastTimeUp));
    }

    return result;
  }
}
