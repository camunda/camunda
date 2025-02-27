/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.gateway.impl.stream.JobClientStreamMetricsDoc.PushKeyNames;
import io.camunda.zeebe.gateway.impl.stream.JobClientStreamMetricsDoc.PushResultTag;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class JobClientStreamMetrics implements ClientStreamMetrics {

  private final Map<ErrorCode, Counter> pushAttempts = new EnumMap<>(ErrorCode.class);

  private final StatefulGauge aggregatedStreamCount;
  private final StatefulGauge clientCount;
  private final StatefulGauge serverCount;
  private final DistributionSummary aggregatedClients;
  private final Counter pushSuccessCount;
  private final Counter pushFailureCount;

  JobClientStreamMetrics(final MeterRegistry registry) {
    aggregatedClients =
        MicrometerUtil.buildSummary(JobClientStreamMetricsDoc.AGGREGATED_CLIENTS)
            .register(registry);
    pushFailureCount =
        registerPushCounter(JobClientStreamMetricsDoc.PUSHES, PushResultTag.FAILURE, registry);
    pushSuccessCount =
        registerPushCounter(JobClientStreamMetricsDoc.PUSHES, PushResultTag.SUCCESS, registry);

    serverCount = registerGauge(JobClientStreamMetricsDoc.SERVERS, registry);
    clientCount = registerGauge(JobClientStreamMetricsDoc.CLIENTS, registry);
    aggregatedStreamCount = registerGauge(JobClientStreamMetricsDoc.AGGREGATED_STREAMS, registry);

    // pre-populate the map to
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

  private StatefulGauge registerGauge(
      final JobClientStreamMetricsDoc doc, final MeterRegistry registry) {
    return StatefulGauge.builder(doc.getName())
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
