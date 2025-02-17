/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JobStreamMetrics implements RemoteStreamMetrics {
  private final AtomicInteger streamCount = new AtomicInteger(0);
  private final Map<ErrorCode, Counter> pushTryFailedCount = new EnumMap<>(ErrorCode.class);

  private final MeterRegistry registry;
  private final Counter pushSuccessCount;
  private final Counter pushFailedCount;

  public JobStreamMetrics(final MeterRegistry registry) {
    this.registry = registry;

    pushSuccessCount = registerCounter(JobStreamMetricsDoc.PUSH_SUCCESS_COUNT);
    pushFailedCount = registerCounter(JobStreamMetricsDoc.PUSH_FAILED_COUNT);

    final var streamCountDoc = JobStreamMetricsDoc.STREAM_COUNT;
    Gauge.builder(streamCountDoc.getName(), streamCount, Number::intValue)
        .description(streamCountDoc.getDescription())
        .register(registry);
  }

  private Counter registerCounter(final JobStreamMetricsDoc doc, final Tag... tags) {
    final var builder = Counter.builder(doc.getName()).description(doc.getDescription());
    for (final var tag : tags) {
      builder.tag(tag.getKey(), tag.getValue());
    }

    return builder.register(registry);
  }

  @Override
  public void addStream() {
    streamCount.incrementAndGet();
  }

  @Override
  public void removeStream() {
    streamCount.decrementAndGet();
  }

  @Override
  public void pushSucceeded() {
    pushSuccessCount.increment();
  }

  @Override
  public void pushFailed() {
    pushFailedCount.increment();
  }

  @Override
  public void pushTryFailed(final ErrorCode code) {
    final var meterDoc = JobStreamMetricsDoc.PUSH_TRY_FAILED_COUNT;
    pushTryFailedCount
        .computeIfAbsent(code, ignored -> registerCounter(meterDoc, Tag.of("code", code.name())))
        .increment();
  }
}
