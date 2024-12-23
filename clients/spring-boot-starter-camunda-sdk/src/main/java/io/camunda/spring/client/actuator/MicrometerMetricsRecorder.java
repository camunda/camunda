/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.actuator;

import io.camunda.spring.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrometerMetricsRecorder implements MetricsRecorder {

  private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerMetricsRecorder.class);

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

  public MicrometerMetricsRecorder(final MeterRegistry meterRegistry) {
    LOGGER.info("Enabling Micrometer based metrics for camunda-spring-sdk (available via Actuator)");
    this.meterRegistry = meterRegistry;
  }

  protected Counter newCounter(final String metricName, final String action, final String jobType) {
    final List<Tag> tags = new ArrayList<>();
    if (action != null && !action.isEmpty()) {
      tags.add(Tag.of("action", action));
    }
    if (jobType != null && !jobType.isEmpty()) {
      tags.add(Tag.of("type", jobType));
    }
    return meterRegistry.counter(metricName, tags);
  }

  @Override
  public void increase(
      final String metricName, final String action, final String type, final int count) {
    final String key = metricName + "#" + action + '#' + type;
    final Counter counter =
        counters.computeIfAbsent(key, k -> newCounter(metricName, action, type));
    counter.increment(count);
  }

  @Override
  public void executeWithTimer(
      final String metricName, final String jobType, final Runnable methodToExecute) {
    final Timer timer = meterRegistry.timer(metricName, "type", jobType);
    timer.record(methodToExecute);
  }
}
