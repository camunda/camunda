/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;

public final class ReplayMetrics {

  private final AtomicLong lastSourcePosition = new AtomicLong();

  private final Clock clock;
  private final Counter replayEventsCount;
  private final Timer replayDurationTimer;

  public ReplayMetrics(final MeterRegistry registry) {
    clock = registry.config().clock();

    replayEventsCount = registerReplayEventsCount(registry);
    replayDurationTimer = registerReplayDuration(registry);
    registerLastSourcePosition(registry);
  }

  private Timer registerReplayDuration(final MeterRegistry registry) {
    final var meterDoc = StreamMetricsDoc.REPLAY_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .register(registry);
  }

  private Counter registerReplayEventsCount(final MeterRegistry registry) {
    final var meterDoc = StreamMetricsDoc.REPLAY_EVENTS_COUNT;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .register(registry);
  }

  private void registerLastSourcePosition(final MeterRegistry registry) {
    final var meterDoc = StreamMetricsDoc.LAST_SOURCE_POSITION;
    Gauge.builder(meterDoc.getName(), lastSourcePosition, AtomicLong::longValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }

  public void event() {
    replayEventsCount.increment();
  }

  public CloseableSilently startReplayDurationTimer() {
    return MicrometerUtil.timer(replayDurationTimer, Timer.start(clock));
  }

  public void setLastSourcePosition(final long position) {
    lastSourcePosition.set(position);
  }
}
