/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.scheduler.Actor;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import org.slf4j.Logger;

/** Samples every {@link SampledDetector} at a fixed interval on its own actor. */
public final class HealthDetectorService extends Actor {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final Duration interval;
  private final List<SampledDetector> detectors;
  private final InstantSource clock;

  public HealthDetectorService(
      final Duration interval, final List<SampledDetector> detectors, final InstantSource clock) {
    super("HealthDetectors");
    this.interval = interval;
    this.detectors = List.copyOf(detectors);
    this.clock = clock;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(interval, this::sampleAll);
  }

  private void sampleAll() {
    final var now = clock.instant();
    for (final var detector : detectors) {
      try {
        detector.sample(now);
      } catch (final RuntimeException e) {
        LOG.warn("Failed to sample health detector {}", detector, e);
      }
    }
  }
}
