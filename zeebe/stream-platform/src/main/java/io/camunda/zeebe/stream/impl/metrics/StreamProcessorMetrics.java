/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class StreamProcessorMetrics {

  private final AtomicLong startupRecoveryTime = new AtomicLong();
  private final AtomicInteger processorState = new AtomicInteger();

  private final MeterRegistry registry;

  public StreamProcessorMetrics(final MeterRegistry registry) {
    this.registry = registry;

    registerStartupRecoveryTime();
    registerProcessorState();
  }

  public void setStreamProcessorInitial() {
    processorState.set(0);
  }

  public void setStreamProcessorReplay() {
    processorState.set(1);
  }

  public void setStreamProcessorProcessing() {
    processorState.set(2);
  }

  public void setStreamProcessorFailed() {
    processorState.set(3);
  }

  public void setStreamProcessorPaused() {
    processorState.set(4);
  }

  public CloseableSilently startRecoveryTimer() {
    return MicrometerUtil.timer(
        startupRecoveryTime::set, TimeUnit.MILLISECONDS, registry.config().clock());
  }

  public void initializeProcessorPhase(final Phase phase) {
    switch (phase) {
      case INITIAL:
        setStreamProcessorInitial();
        break;
      case REPLAY:
        setStreamProcessorReplay();
        break;
      case PROCESSING:
        setStreamProcessorProcessing();
        break;
      case PAUSED:
        setStreamProcessorPaused();
        break;
      default:
        setStreamProcessorFailed();
    }
  }

  private void registerStartupRecoveryTime() {
    final var meterDoc = StreamMetricsDoc.STARTUP_RECOVERY_TIME;
    TimeGauge.builder(
            meterDoc.getName(), startupRecoveryTime, TimeUnit.MILLISECONDS, AtomicLong::longValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }

  private void registerProcessorState() {
    final var meterDoc = StreamMetricsDoc.PROCESSOR_STATE;
    Gauge.builder(meterDoc.getName(), processorState, AtomicInteger::intValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }
}
