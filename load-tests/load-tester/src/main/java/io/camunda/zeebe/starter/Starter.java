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
import io.camunda.zeebe.metrics.StarterMetricsDoc;
import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@Profile("starter")
@EnableScheduling
public class Starter {

  private final StarterProperties starterCfg;
  private final StarterLivenessIndicator livenessIndicator;
  private final TaskScheduler scheduler;
  private final ApplicationEventPublisher eventPublisher;
  private final AtomicInteger runFinished = new AtomicInteger(0);

  public Starter(
      final LoadTesterProperties properties,
      final MeterRegistry registry,
      final StarterLivenessIndicator livenessIndicator,
      final TaskScheduler scheduler,
      final ApplicationEventPublisher eventPublisher) {
    starterCfg = properties.getStarter();
    this.livenessIndicator = livenessIndicator;
    this.scheduler = scheduler;
    this.eventPublisher = eventPublisher;

    // Expose the client information early: these are only static values which are not supposed to
    // be affected by the current state of the client (whether it successfully connects to the
    // brokers, etc.)
    // Having these infos early could help to investigate the client faster.
    Gauge.builder(StarterMetricsDoc.CLIENT_INFO.getName(), () -> 1)
        .description(StarterMetricsDoc.CLIENT_INFO.getDescription())
        .tag(StarterMetricKeyNames.NAME.asString(), "starter")
        .tag(StarterMetricKeyNames.PROCESS_ID.asString(), starterCfg.getProcessId())
        .tag(StarterMetricKeyNames.NB_THREADS.asString(), String.valueOf(starterCfg.getThreads()))
        .register(registry);

    Gauge.builder(StarterMetricsDoc.RUN_FINISHED.getName(), runFinished, AtomicInteger::doubleValue)
        .description(StarterMetricsDoc.RUN_FINISHED.getDescription())
        .register(registry);
  }

  @EventListener(ApplicationReadyEvent.class)
  void scheduleFinish() {
    final int durationLimit = starterCfg.getDurationLimit();
    if (durationLimit > 0) {
      scheduler.schedule(this::finish, Instant.now().plusSeconds(durationLimit));
    }
  }

  private void finish() {
    runFinished.set(1);
    livenessIndicator.recordFinished();
    eventPublisher.publishEvent(new StarterFinished());
  }
}
