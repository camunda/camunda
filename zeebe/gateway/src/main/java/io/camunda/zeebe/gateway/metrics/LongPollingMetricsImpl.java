/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import static io.camunda.zeebe.gateway.metrics.LongPollingMetrics.LongPollingMetricsDoc.REQUESTS_QUEUED_CURRENT;
import static io.camunda.zeebe.gateway.metrics.LongPollingMetrics.RequestsQueuedKeyNames.TYPE;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class LongPollingMetricsImpl implements LongPollingMetrics {
  private final Map<String, AtomicLong> requestsQueued = new HashMap<>();
  private final MeterRegistry registry;
  private final GatewayProtocol gatewayProtocol;

  public LongPollingMetricsImpl(
      final MeterRegistry registry, final GatewayProtocol gatewayProtocol) {
    this.registry = Objects.requireNonNull(registry, "must specify a meter registry");
    this.gatewayProtocol = gatewayProtocol;
  }

  @Override
  public void setBlockedRequestsCount(final String type, final int count) {
    requestsQueued.computeIfAbsent(type, this::registerBlockedRequestsCount).set(count);
  }

  private AtomicLong registerBlockedRequestsCount(final String type) {
    final var count = new AtomicLong();
    Gauge.builder(REQUESTS_QUEUED_CURRENT.getName(), count, Number::longValue)
        .description(REQUESTS_QUEUED_CURRENT.getDescription())
        .tag(TYPE.asString(), type)
        .tag(GatewayKeyNames.GATEWAY_PROTOCOL.asString(), gatewayProtocol.value())
        .register(registry);

    return count;
  }
}
