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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class StreamProcessorMetrics {

  private final AtomicLong startupRecoveryTime = new AtomicLong();

  private final MeterRegistry registry;

  public StreamProcessorMetrics(final MeterRegistry registry) {
    this.registry = registry;

    registerStartupRecoveryTime();
  }

  public CloseableSilently startRecoveryTimer() {
    return MicrometerUtil.timer(
        startupRecoveryTime::set, TimeUnit.MILLISECONDS, registry.config().clock());
  }

  private void registerStartupRecoveryTime() {
    final var meterDoc = StreamMetricsDoc.STARTUP_RECOVERY_TIME;
    TimeGauge.builder(
            meterDoc.getName(), startupRecoveryTime, TimeUnit.MILLISECONDS, AtomicLong::longValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }
}
