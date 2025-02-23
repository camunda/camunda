/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import static io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.REQUESTS_QUEUED_CURRENT;
import static io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.RequestsQueuedKeyNames.TYPE;

import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayKeyNames;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayProtocol;
import io.camunda.zeebe.util.micrometer.BoundedMeterCache;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Metrics to monitor the health of the long polling requests per protocol. */
public sealed class LongPollingMetrics {

  private final BoundedMeterCache<StatefulGauge> requestsQueued;

  public LongPollingMetrics(final MeterRegistry registry, final GatewayProtocol gatewayProtocol) {
    final var provider =
        StatefulGauge.builder(REQUESTS_QUEUED_CURRENT.getName())
            .description(REQUESTS_QUEUED_CURRENT.getDescription())
            .tag(GatewayKeyNames.GATEWAY_PROTOCOL.asString(), gatewayProtocol.value())
            .withRegistry(registry);

    requestsQueued = new BoundedMeterCache.Builder<>(registry, provider, TYPE).build();
  }

  protected LongPollingMetrics(final BoundedMeterCache<StatefulGauge> requestsQueued) {
    this.requestsQueued = requestsQueued;
  }

  /**
   * Returns an instance of {@link LongPollingMetrics} which does nothing. Mostly useful for
   * testing.
   */
  public static LongPollingMetrics noop() {
    return new Noop();
  }

  /** Sets the number of long polling requests which are idle for a given job type */
  public void setBlockedRequestsCount(final String type, final int count) {
    requestsQueued.get(type).set(count);
  }

  private static final class Noop extends LongPollingMetrics {

    private Noop() {
      super(null);
    }

    @Override
    public void setBlockedRequestsCount(final String type, final int count) {}
  }
}
