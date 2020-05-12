/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.annotation.Scheduled;

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

  private Status lastStatus;
  private Long lastTimeUp;

  private final Map<String, Object> staticDetails = new HashMap<>();

  public DelayedHealthIndicator(
      final HealthIndicator originalHealthIndicator, final Duration maxDowntime) {
    if (requireNonNull(maxDowntime).toMillis() < 0) {
      throw new IllegalArgumentException("maxDonwtime must be >= 0");
    }
    this.originalHealthIndicator = requireNonNull(originalHealthIndicator);
    this.maxDowntime = maxDowntime;

    staticDetails.put("derivedFrom", originalHealthIndicator.getClass().getSimpleName());
    staticDetails.put("maxDowntime", maxDowntime);
  }

  @Scheduled(fixedDelay = 5000)
  public void checkHealth() {
    lastStatus = originalHealthIndicator.health().getStatus();

    if (lastStatus.equals(Status.UP)) {
      lastTimeUp = System.currentTimeMillis();
    }
  }

  @Override
  public Health health() {
    final Builder responseBuilder;
    final long now = System.currentTimeMillis();
    if (lastTimeUp == null) { // was never up
      if (lastStatus == null) {
        responseBuilder = Health.unknown();
      } else {
        responseBuilder = Health.status(lastStatus);
      }
    } else if (lastTimeUp + maxDowntime.toMillis() > now) {
      responseBuilder = Health.up();
    } else {
      responseBuilder = Health.status(lastStatus);
    }

    return responseBuilder.withDetails(createDetails(now)).build();
  }

  private Map<String, Object> createDetails(long referenceTime) {
    final var result = new HashMap<>(staticDetails);
    if (lastStatus != null) {
      result.put("lastSeenStatus", lastStatus.getCode());
    }

    result.put("wasEverUp", lastTimeUp != null);

    if (lastTimeUp != null && lastStatus != Status.UP) {
      result.put("downTime", Duration.ofMillis(referenceTime - lastTimeUp));
    }

    return result;
  }
}
