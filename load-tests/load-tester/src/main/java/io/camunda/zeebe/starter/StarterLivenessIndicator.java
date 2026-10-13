/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import io.camunda.zeebe.config.LoadTesterProperties;
import io.camunda.zeebe.config.StarterProperties;
import io.camunda.zeebe.metrics.ErrorType;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Liveness indicator of the starter. Reports DOWN when no start request succeeded for longer than
 * the configured age, so that Kubernetes restarts the pod. An answer that signals backpressure
 * counts as a success, as the cluster is responding. Every other failure does not, whatever its
 * cause: rejected credentials, a stopped request scheduler and an unreachable cluster all end in
 * the same state, a starter that creates no process instances.
 */
@Component("starterLiveness")
@Profile("starter")
public class StarterLivenessIndicator implements HealthIndicator {

  private final Duration maxNoSuccessAge;
  private final InstantSource clock;
  private final AtomicReference<@Nullable Instant> lastSuccess = new AtomicReference<>();
  private final AtomicBoolean finished = new AtomicBoolean();

  @Autowired
  public StarterLivenessIndicator(final LoadTesterProperties properties) {
    this(properties.getStarter(), InstantSource.system());
  }

  StarterLivenessIndicator(final StarterProperties properties, final InstantSource clock) {
    maxNoSuccessAge = properties.getLivenessMaxNoSuccessAge();
    this.clock = clock;
  }

  /** Starts the clock once the starter is connected to the cluster. */
  void recordStarted() {
    lastSuccess.compareAndSet(null, clock.instant());
  }

  /** Records the answer of a request; {@code error} is {@code null} if it succeeded. */
  void recordResult(final @Nullable Throwable error) {
    if (error == null || ErrorType.isBackpressure(error)) {
      lastSuccess.set(clock.instant());
    }
  }

  /** Records that the starter completed its run, after which liveness stays UP. */
  void recordFinished() {
    finished.set(true);
  }

  @Override
  public Health health() {
    final var success = lastSuccess.get();
    if (!finished.get() && success != null) {
      final var age = Duration.between(success, clock.instant());
      if (age.compareTo(maxNoSuccessAge) > 0) {
        return Health.down().withDetail("lastSuccessAge", age.toString()).build();
      }
    }
    return Health.up().build();
  }
}
