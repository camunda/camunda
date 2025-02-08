/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BrokerStepMetrics {
  private final Map<String, AtomicLong> startup = new HashMap<>();
  private final Map<String, AtomicLong> close = new HashMap<>();

  private final MeterRegistry registry;

  public BrokerStepMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a meter registry");
  }

  public CloseableSilently createStartupTimer(final String stepName) {
    final var timerTracker =
        startup.computeIfAbsent(
            stepName, name -> registerMetric(BrokerStepMetricsDoc.STARTUP, name));
    return MicrometerUtil.timer(
        timerTracker::set, TimeUnit.MILLISECONDS, registry.config().clock());
  }

  public CloseableSilently createCloseTimer(final String stepName) {
    final var timerTracker =
        close.computeIfAbsent(stepName, name -> registerMetric(BrokerStepMetricsDoc.CLOSE, name));
    return MicrometerUtil.timer(
        timerTracker::set, TimeUnit.MILLISECONDS, registry.config().clock());
  }

  private AtomicLong registerMetric(final BrokerStepMetricsDoc meterDoc, final String stepName) {
    final var timeTracker = new AtomicLong();
    TimeGauge.builder(meterDoc.getName(), timeTracker, TimeUnit.MILLISECONDS, AtomicLong::longValue)
        .description(meterDoc.getDescription())
        .tag("stepName", stepName)
        .register(registry);

    return timeTracker;
  }
}
