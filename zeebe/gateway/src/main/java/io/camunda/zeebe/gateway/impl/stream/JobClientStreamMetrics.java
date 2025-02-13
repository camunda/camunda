/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.gateway.impl.stream.JobClientStreamMetricsDoc.PushKeyNames;
import io.camunda.zeebe.gateway.impl.stream.JobClientStreamMetricsDoc.PushResultTag;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class JobClientStreamMetrics implements ClientStreamMetrics {

  private final AtomicLong serverCount = new AtomicLong();
  private final AtomicLong clientCount = new AtomicLong();
  private final AtomicLong aggregatedStreamCount = new AtomicLong();
  private final Map<ErrorCode, Counter> pushAttempts = new EnumMap<>(ErrorCode.class);

  private final DistributionSummary aggregatedClients;
  private final Counter pushSuccessCount;
  private final Counter pushFailureCount;

  JobClientStreamMetrics(final MeterRegistry registry) {
    aggregatedClients =
        MicrometerUtil.summary(JobClientStreamMetricsDoc.AGGREGATED_CLIENTS).register(registry);
    pushFailureCount =
        registerPushCounter(JobClientStreamMetricsDoc.PUSHES, PushResultTag.FAILURE, registry);
    pushSuccessCount =
        registerPushCounter(JobClientStreamMetricsDoc.PUSHES, PushResultTag.SUCCESS, registry);

    registerGauge(JobClientStreamMetricsDoc.SERVERS, serverCount, registry);
    registerGauge(JobClientStreamMetricsDoc.CLIENTS, clientCount, registry);
    registerGauge(JobClientStreamMetricsDoc.AGGREGATED_STREAMS, aggregatedStreamCount, registry);

    // pre-populate the map to ensure it is thread safe
    for (final var errorCode : ErrorCode.values()) {
      pushAttempts.put(errorCode, registerPushAttemptCounter(registry, errorCode));
    }
  }

  @Override
  public void serverCount(final int count) {
    serverCount.set(count);
  }

  @Override
  public void clientCount(final int count) {
    clientCount.set(count);
  }

  @Override
  public void aggregatedStreamCount(final int count) {
    aggregatedStreamCount.set(count);
  }

  @Override
  public void observeAggregatedClientCount(final int count) {
    aggregatedClients.record(count);
  }

  @Override
  public void pushSucceeded() {
    pushSuccessCount.increment();
  }

  @Override
  public void pushFailed() {
    pushFailureCount.increment();
  }

  @Override
  public void pushTryFailed(final ErrorCode code) {
    pushAttempts.get(code).increment();
  }

  private void registerGauge(
      final JobClientStreamMetricsDoc doc, final AtomicLong state, final MeterRegistry registry) {
    Gauge.builder(doc.getName(), state, AtomicLong::get)
        .description(doc.getDescription())
        .register(registry);
  }

  private Counter registerPushAttemptCounter(
      final MeterRegistry registry, final ErrorCode errorCode) {
    return Counter.builder(JobClientStreamMetricsDoc.PUSH_TRY_FAILED_COUNT.getName())
        .description(JobClientStreamMetricsDoc.PUSH_TRY_FAILED_COUNT.getDescription())
        .tag(PushKeyNames.CODE.asString(), errorCode.name())
        .register(registry);
  }

  private Counter registerPushCounter(
      final JobClientStreamMetricsDoc doc,
      final PushResultTag result,
      final MeterRegistry registry) {
    return Counter.builder(doc.getName())
        .description(doc.getDescription())
        .tag(PushKeyNames.STATUS.asString(), result.getTagValue())
        .register(registry);
  }
}
